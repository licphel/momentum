/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fmhi.gfx.particle;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.buffer.BufferType;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.texture.*;
import net.fmhi.math.Half;
import net.fmhi.math.Matrix4x4;
import net.fmhi.util.ResourceProvider;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;

/**
 * A GPU-accelerated particle renderer using instanced rendering.
 *
 * <p>Particles are added via {@link #spawn(Particle2D)} and rendered in
 * instanced draws batched by texture. The emitter performs no physics
 * or lifecycle management — subclasses of {@link Particle2D} handle that
 * themselves.
 */
public final class ParticleSystem2D {
  private static final int VP_UBO_SIZE = 64;
  private static final int INSTANCE_STRIDE = 36;
  private static final Comparator<Particle2D> TEXTURE_COMPARATOR =
      Comparator.comparingInt(p -> {
        TexturePart t = p.texture;
        if (t == null) {
          return 0;
        }
        Texture tex = t.src();
        return System.identityHashCode(tex);
      });

  private final Device device;
  private final Pipeline pipeline;
  private final BufferObject quadVbo;
  private final BufferObject quadIbo;
  private final BufferObject particleVbo;
  private final BufferObject ubo;
  private final Sampler sampler;
  private final ResourceSetLayout rsl;
  private final byte[] vpBytes = new byte[VP_UBO_SIZE];
  private final float[] mVp = new float[16];
  private final Particle2D[] pool;
  private final int maxParticles;
  private @Nullable ByteBuffer instanceBuffer;
  private int size;

  private ParticleSystem2D(Builder b) {
    Device device = b.device;
    this.device = device;

    ResourceProvider rp = ResourceProvider.classpath(ParticleSystem2D.class);
    ShaderProgram program = ShaderProgram.load(device,
        rp.readString("/shaders/particle.vert.hlsl"),
        rp.readString("/shaders/particle.frag.hlsl"));

    VertexLayout vl = VertexLayout.bake(
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false).withDivisor(1),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false).withDivisor(1),
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false).withDivisor(1),
        new VertexLayout.Attr(1, VertexAttributeType.FLOAT32, false).withDivisor(1),
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false).withDivisor(1));

    rsl = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_tex", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE));

    pipeline = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.NOT_CULL)
        .shaderProgram(program).vertexLayout(vl).resourceLayouts(rsl).build());

    sampler = device.getSampler(new SamplerDesc.Builder()
        .minFilter(TextureFilter.LINEAR).magFilter(TextureFilter.LINEAR).build());

    quadVbo = device.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STATIC));
    quadVbo.allocate(64, new byte[64]);
    ByteBuffer qb = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
    qb.putFloat(0.0F).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
    qb.putFloat(1.0F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F);
    qb.putFloat(1.0F).putFloat(1.0F).putFloat(1.0F).putFloat(1.0F);
    qb.putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(1.0F);
    qb.flip();
    quadVbo.submit(qb);

    quadIbo = device.getBuffer(BufferObjectDesc.index(BufferFrequency.STATIC));
    quadIbo.allocate(24, new byte[24]);
    ByteBuffer ib = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder());
    ib.putInt(0).putInt(2).putInt(1).putInt(2).putInt(0).putInt(3);
    ib.flip();
    quadIbo.submit(ib);

    particleVbo = device.getBuffer(new BufferObjectDesc(BufferFrequency.STREAM, BufferType.VERTEX));
    ubo = device.getBuffer(BufferObjectDesc.uniform());

    this.maxParticles = b.maxParticles;
    this.pool = new Particle2D[maxParticles];
  }

  /**
   * Creates a new {@link Builder}.
   *
   * @param device the graphics device for GPU resource creation
   * @return a new builder
   */
  public static Builder builder(Device device) {
    return new Builder(device);
  }

  /**
   * Adds a particle to this emitter. The particle reference is stored
   * directly — no fields are copied.
   *
   * <p>If the pool is full, the particle is silently ignored.
   *
   * @param p the particle to add
   */
  public void spawn(Particle2D p) {
    if (size >= maxParticles) {
      return;
    }
    pool[size++] = p;
  }

  /**
   * Renders all alive particles, sorted by texture, in one instanced
   * draw call per texture group.
   *
   * <p>Dead particles (where {@link Particle2D#alive()} returns
   * {@code false}) are skipped and compacted out.
   *
   * @param encoder the command encoder
   * @param vp      the view-projection matrix
   */
  public void draw(Encoder encoder, Matrix4x4 vp) {
    if (size == 0) {
      return;
    }

    // compact dead
    compact();

    if (size == 0) {
      return;
    }

    // sort by texture
    Arrays.sort(pool, 0, size, TEXTURE_COMPARATOR);

    // upload VP matrix
    vp.toFloatArray(mVp, 0);
    for (int j = 0; j < 16; j++) {
      int bits = Float.floatToRawIntBits(mVp[j]);
      int off = j * 4;
      vpBytes[off] = (byte) bits;
      vpBytes[off + 1] = (byte) (bits >> 8);
      vpBytes[off + 2] = (byte) (bits >> 16);
      vpBytes[off + 3] = (byte) (bits >> 24);
    }
    ubo.submit(vpBytes, 0, VP_UBO_SIZE);

    // upload all instance data at once (sorted)
    uploadAllInstanceData();

    encoder.setRenderPipe(pipeline);
    encoder.setVertexBuffer(quadVbo);
    encoder.setIndexBuffer(quadIbo);
    encoder.setInstanceBuffer(particleVbo);

    // draw each texture group (grouped by underlying Texture)
    int groupStart = 0;
    while (groupStart < size) {
      TexturePart batchTex = pool[groupStart].texture;
      if (batchTex == null) {
        groupStart++;
        continue;
      }
      Texture batchSrc = batchTex.src();

      int groupEnd = groupStart + 1;
      while (groupEnd < size) {
        TexturePart nextTex = pool[groupEnd].texture;
        if (nextTex == null || nextTex.src() != batchSrc) {
          break;
        }
        groupEnd++;
      }
      int count = groupEnd - groupStart;

      if (batchSrc != null) {
        ResourceSet rs = device.getResourceSet(rsl);
        rs.bindUniform(0, ubo, VP_UBO_SIZE);
        rs.bindTexture(1, batchSrc, sampler);

        encoder.setInstanceBase(groupStart);
        encoder.setResource(0, rs);
        encoder.drawIndexedInstanced(6, count, 0);
      }

      groupStart = groupEnd;
    }
    encoder.setInstanceBase(0);
    encoder.setInstanceBuffer(null);
  }

  /**
   * Removes all particles.
   */
  public void clear() {
    size = 0;
  }

  /**
   * Returns the number of currently tracked particles.
   *
   * @return the particle count
   */
  public int particleCount() {
    return size;
  }

  private void compact() {
    int i = 0;
    while (i < size) {
      if (pool[i].alive()) {
        i++;
      } else {
        size--;
        Particle2D tmp = pool[i];
        pool[i] = pool[size];
        pool[size] = tmp;
      }
    }
  }

  private void uploadAllInstanceData() {
    int bytes = size * INSTANCE_STRIDE;
    if (instanceBuffer == null || instanceBuffer.capacity() < bytes) {
      instanceBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
    instanceBuffer.clear();
    for (int i = 0; i < size; i++) {
      Particle2D p = pool[i];
      instanceBuffer.putFloat(p.posX()).putFloat(p.posY());
      instanceBuffer.putFloat(p.width()).putFloat(p.height());
      instanceBuffer.putLong(p.color().pack());
      instanceBuffer.putFloat(p.rotation());
      // UV region normalized to [0,1]
      TexturePart tp = p.texture;
      if (tp != null) {
        Texture tex = tp.src();
        float tw = tex != null ? tex.width() : 1.0F;
        float th = tex != null ? tex.height() : 1.0F;
        float nu = tp.u() / tw;
        float nv = tp.v() / th;
        float nuw = tp.width() / tw;
        float nvh = tp.height() / th;
        long uvPacked = (Half.quickHalf(nu) & 0xFFFFL)
            | ((Half.quickHalf(nv) & 0xFFFFL) << 16)
            | ((Half.quickHalf(nuw) & 0xFFFFL) << 32)
            | ((Half.quickHalf(nvh) & 0xFFFFL) << 48);
        instanceBuffer.putLong(uvPacked);
      } else {
        // null texture → full 0..1
        long one = Half.quickHalf(1.0F) & 0xFFFFL;
        instanceBuffer.putLong(one << 32 | one << 48);
      }
    }
    instanceBuffer.flip();
    particleVbo.submit(instanceBuffer);
  }

  /**
   * Builder for configuring and creating a {@link ParticleSystem2D}.
   */
  public static final class Builder {
    private final Device device;
    private int maxParticles = 500;

    Builder(Device device) {
      this.device = device;
    }

    /**
     * Sets the maximum number of particles.
     *
     * @param v the capacity; must be positive
     * @return this builder
     */
    public Builder maxParticles(int v) {
      maxParticles = v;
      return this;
    }

    /**
     * Builds the particle emitter, creating all GPU resources.
     *
     * @return a new {@link ParticleSystem2D}
     */
    public ParticleSystem2D build() {
      return new ParticleSystem2D(this);
    }
  }
}
