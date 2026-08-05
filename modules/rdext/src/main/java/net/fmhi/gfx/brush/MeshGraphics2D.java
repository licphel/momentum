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

package net.fmhi.gfx.brush;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.mesh.Material;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.mesh.Section;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Topology;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.Texture;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Records 2D geometry into a retained-mode {@link Mesh} for later replay.
 *
 * <p>Unlike {@link BatchedGraphics2D}, which submits draw calls to the GPU on every flush,
 * {@code MeshGraphics2D} accumulates vertex and index data across flushes into section
 * drafts. Call {@link #bake(Device)} to produce an immutable {@link Mesh} that can be drawn
 * later via {@link Graphics2D#drawMesh(Mesh)}.
 *
 * <p>{@link #begin(RenderPass)} and {@link #end()} have no effect in this implementation;
 * render pass management is the caller's responsibility when drawing the baked mesh.
 *
 * @see BatchedGraphics2D
 * @see Mesh
 */
public final class MeshGraphics2D extends BatchedGraphics2D {
  private final List<SectionDraft> drafts = new ArrayList<>();

  /**
   * Creates a new {@code MeshGraphics2D} backed by the given device.
   *
   * @param data   the staging area receiving vertices and indices
   * @param device the GPU device
   */
  public MeshGraphics2D(VertexStore data, Device device) {
    super(data, device);
  }

  /**
   * Captures the recorded vertex and index data as a section draft, then clears the
   * staging buffers for the next batch. Does not submit anything to the GPU.
   *
   * @param force ignored; an empty batch is never recorded
   */
  @Override
  public void flush(boolean force) {
    if (!force && data.vertexCount() <= 0 && data.indexCount() <= 0) {
      return;
    }
    byte[] vertices = data.recordVertices();
    byte[] indices = data.recordIndices();
    data.clear();
    drafts.add(new SectionDraft(vertices, indices,
        currentPrimitive, currentTexture, sampler == null ? defSampler : sampler,
        resolvePipeline(), resolveResourceSetLayout()));
  }

  /**
   * No-op; render pass management is deferred until the baked mesh is drawn.
   *
   * @param pass the render pass descriptor; ignored
   */
  @Override
  public void begin(RenderPass pass) {
    drafts.clear();
  }

  /**
   * No-op; render pass management is deferred until the baked mesh is drawn.
   */
  @Override
  public void end() {
    flush(true);
  }

  /**
   * Converts all recorded section drafts into an immutable {@link Mesh}.
   *
   * <p>Each draft becomes a {@link Section} with its own vertex and index buffers, material,
   * and topology. Every baked mesh owns its own uniform buffer, so meshes cached from one
   * context can be created and closed independently — a shared ubo would be released by the
   * first {@link Mesh#close()}. The result can be drawn with
   * {@link Graphics2D#drawMesh(Mesh)}.
   *
   * @param device the GPU device to allocate buffers from
   * @return a new mesh containing all recorded geometry
   */
  public Mesh bake(Device device) {
    flush(true);
    List<Section> sections = new ArrayList<>();

    for (SectionDraft d : drafts) {
      if (d.primitive() == null) {
        continue;
      }
      ResourceSet rs = device.getResourceSet(d.rsl);
      boolean tex = d.primitive().isTextured();
      if (tex && d.texture() != null && d.sampler() != null) {
        rs.bindTexture(1, d.texture(), d.sampler());
      }

      BufferObject vbo = device.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STATIC));
      vbo.submit(d.vertices(), 0, d.vertices().length);
      BufferObject ibo = d.primitive().isIndexed() ? device.getBuffer(BufferObjectDesc.index(BufferFrequency.STATIC)) : null;
      if (ibo != null) {
        ibo.submit(d.indices(), 0, d.indices().length);
      }

      Topology top = d.primitive().topology();
      int idxCount = d.indices().length / Integer.BYTES;
      int vertCount = d.vertices().length / d.primitive().vertexSize();
      sections.add(new Section(new Material(d.pipeline, rs), vbo, ibo, vertCount, 0, 0, idxCount, top));
    }

    return new Mesh(List.copyOf(sections));
  }

  private record SectionDraft(byte[] vertices,
                              byte[] indices,
                              @Nullable Primitive2D primitive,
                              @Nullable Texture texture,
                              @Nullable Sampler sampler,
                              Pipeline pipeline,
                              ResourceSetLayout rsl) {
  }
}
