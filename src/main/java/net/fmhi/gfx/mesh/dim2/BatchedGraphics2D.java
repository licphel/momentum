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

package net.fmhi.gfx.mesh.dim2;

import net.fmhi.gfx.BuiltinGfx;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.cmd.EncoderDesc;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Topology;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.math.Box2D;
import net.fmhi.math.Matrix4x4;

/**
 * A per-frame 2D batch renderer that submits draw calls directly to the GPU each flush.
 *
 * <p>Uses built-in shaders for textured sprites, colored primitives, lines, and points.
 * Each call to {@link #flush(boolean)} submits accumulated vertex and index data, uploads
 * the view-projection matrix, and executes the encoder. This class is suitable for immediate-mode
 * rendering where geometry changes every frame.
 *
 * <p>For retained-mode rendering where geometry is recorded once and replayed, use
 * {@link MeshGraphics2D} instead.
 *
 * @see MeshGraphics2D
 */
public class BatchedGraphics2D extends AbstractStatefulGraphics2D {
  final BufferObject vbo;
  final BufferObject ibo;
  final BufferObject ubo;
  private final Encoder encoder;
  private final Device device;
  private boolean begun;

  /**
   * Creates a new {@code BatchedGraphics2D} backed by the given device.
   *
   * @param device the GPU device
   */
  public BatchedGraphics2D(Device device) {
    encoder = device.getEncoder(EncoderDesc.DEFAULT);
    vbo = device.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STREAM));
    vbo.allocate(1024, null);
    ibo = device.getBuffer(BufferObjectDesc.index(BufferFrequency.STREAM));
    ibo.allocate(512, null);
    ubo = device.getBuffer(BufferObjectDesc.uniform());
    ubo.allocate(64, null);
    sampler = BuiltinGfx.sampler;
    this.device = device;
  }

  @Override
  public void drawMesh(Mesh mesh) {
    flush();

    if (camera == null || mesh.sections().isEmpty()) {
      return;
    }

    mesh.uploadVP(camera.viewProjectionMatrix());

    encoder.setViewport((int) viewport.minX(), (int) viewport.minY(),
        (int) viewport.width(), (int) viewport.height());
    encoder.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height(), scissor.enable());

    mesh.draw(encoder, 0);
  }

  /**
   * Submits pending vertex and index data to the GPU and executes the encoder.
   *
   * <p>If no vertex data has been recorded and {@code force} is {@code false}, this method
   * does nothing. Otherwise, it uploads buffers, applies the viewport, scissor, pipeline,
   * and resource set, then draws and restarts the render pass for the next batch.
   *
   * @param force if {@code true}, submits even when no vertex data has been recorded
   */
  @Override
  public void flush(boolean force) {
    if (!force && vertexBuf.writerIndex() == 0) {
      return;
    }
    submitBatch();
  }

  /**
   * Begins a render pass. Must be called before any draw commands.
   *
   * @param pass the render pass descriptor
   * @throws IllegalStateException if already begun
   */
  public void begin(RenderPass pass) {
    if (begun) {
      throw new IllegalStateException("Already begun");
    }

    begun = true;
    renderTarget = pass.target();
    if (renderTarget == null) {
      renderTarget = device.getSwapchain();
    }
    RenderTarget rt = renderTarget;
    viewport = Box2D.create(0, 0, rt.width(), rt.height());
    vertexBuf.clear();
    indexBuf.clear();
    vertexCount = 0;
    indexCount = 0;
    currentPrimitive = null;
    currentTexture = null;
    encoder.beginPass(pass);
  }

  /**
   * Ends the render pass, flushing pending draws and executing the encoder.
   *
   * @throws IllegalStateException if not begun
   */
  public void end() {
    if (!begun) {
      throw new IllegalStateException("Not begun");
    }
    flush(true);
    encoder.endPass();
    encoder.queuedExecute();
    encoder.reset();
    begun = false;
  }

  /**
   * Sets the view-projection matrix and uploads it to the uniform buffer.
   *
   * @param vpm the view-projection matrix
   */
  @Override
  public void setViewProjection(Matrix4x4 vpm) {
    uploadVP(vpm);
  }

  private void submitBatch() {
    if (currentPrimitive == null || camera == null) {
      return;
    }

    int vc = vertexCount;
    int ic = indexCount;
    vertexCount = 0;
    indexCount = 0;

    int vr = vertexBuf.readerIndex();
    int vw = vertexBuf.writerIndex();
    int ir = indexBuf.readerIndex();
    int iw = indexBuf.writerIndex();
    byte[] rawV = vertexBuf.backingArray();
    byte[] rawI = indexBuf.backingArray();
    vertexBuf.clear();
    indexBuf.clear();

    uploadVP(camera.viewProjectionMatrix());
    vbo.submit(rawV, vr, vw - vr);
    if (ic > 0 && currentPrimitive.isIndexed()) {
      ibo.submit(rawI, ir, iw - ir);
    }

    encoder.setViewport((int) viewport.minX(), (int) viewport.minY(),
        (int) viewport.width(), (int) viewport.height());
    encoder.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height(), scissor.enable());

    Pipeline pipe = currentPipeline;
    ResourceSet rs = currentResourceSet;
    if (pipe == null) {
      boolean useTexture = currentPrimitive.isTextured();

      pipe = useTexture ? BuiltinGfx.pipeTexture : BuiltinGfx.pipeColor;
      ResourceSetLayout rsl = useTexture ? BuiltinGfx.rslTexture : BuiltinGfx.rslColor;
      rs = device.getResourceSet(rsl);
      rs.bindUniform(0, ubo, 64);

      if (currentTexture != null && useTexture) {
        if (sampler == null) {
          throw new GraphicsException("Null sampler");
        }
        rs.bindTexture(1, currentTexture, sampler);
      }
    }
    encoder.setRenderPipe(pipe);
    if (rs == null) {
      throw new GraphicsException("There's a custom pipeline, however, no custom resource set bound");
    }
    encoder.setResource(0, rs);

    Topology top = currentPrimitive.topology();
    encoder.setTopology(top);
    encoder.setVertexBuffer(vbo);
    if (currentPrimitive.isIndexed()) {
      encoder.setIndexBuffer(ibo);
      encoder.drawIndexed(ic, 0);
    } else {
      encoder.draw(vc, 0);
    }

    encoder.endPass();
    encoder.queuedExecute();
    encoder.reset();
    encoder.beginPass(new RenderPass.Builder().target(renderTarget).clearMask(0).build());
  }

  /**
   * Returns the pipeline that would be used for the current primitive: the custom pipeline
   * if set, or the appropriate built-in pipeline otherwise.
   *
   * @return the resolved pipeline
   */
  protected Pipeline resolvePipeline() {
    if (currentPipeline != null) {
      return currentPipeline;
    }
    return (currentPrimitive == null || currentPrimitive.isTextured()) ?
        BuiltinGfx.pipeTexture : BuiltinGfx.pipeColor;
  }

  /**
   * Returns the resource set layout matching {@link #resolvePipeline()}.
   *
   * @return the resolved resource set layout
   */
  protected ResourceSetLayout resolveResourceSetLayout() {
    return (currentPrimitive == null || currentPrimitive.isTextured()) ?
        BuiltinGfx.rslTexture : BuiltinGfx.rslColor;
  }

  /**
   * Releases all GPU resources: the encoder, vertex buffer, index buffer, and uniform buffer.
   */
  @Override
  public void close() {
    encoder.close();
    vbo.close();
    ibo.close();
    ubo.close();
  }

  private void uploadVP(Matrix4x4 vpm) {
    float[] m = vpm.toFloatArray();
    byte[] bytes = new byte[64];
    for (int i = 0; i < 16; i++) {
      int bits = Float.floatToRawIntBits(m[i]);
      int off = i * 4;
      bytes[off] = (byte) bits;
      bytes[off + 1] = (byte) (bits >> 8);
      bytes[off + 2] = (byte) (bits >> 16);
      bytes[off + 3] = (byte) (bits >> 24);
    }
    ubo.submit(bytes, 0, bytes.length);
  }
}
