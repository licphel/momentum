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

package io.viki.momentum.gfx.util.impl;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.buffer.BufferFrequency;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.cmd.Encoder;
import io.viki.momentum.gfx.cmd.EncoderDesc;
import io.viki.momentum.gfx.mesh.Mesh;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pipe.*;
import io.viki.momentum.gfx.shader.*;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.SamplerDesc;
import io.viki.momentum.gfx.util.VertexStore;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.resource.Resource;

/**
 * A per-frame 2D batch renderer that submits draw calls directly to the GPU on each flush.
 *
 * <p>Uses built-in shaders for textured sprites, colored primitives, lines, and points.
 * Each call to {@link #flush(boolean)} submits the accumulated vertex and index data,
 * uploads the view-projection matrix, and executes the encoder. Suitable for immediate-mode
 * rendering where geometry changes every frame.
 *
 * <p>For retained-mode rendering where geometry is recorded once and replayed, use
 * {@link MeshGraphics} instead.
 *
 * @see MeshGraphics
 */
public class BatchedGraphics extends StatefulGraphics {
  final Pipeline pipeColor;
  final Pipeline pipeTexture;
  final ResourceSetLayout rslColor;
  final ResourceSetLayout rslTexture;
  final VertexLayout vlColor;
  final VertexLayout vlTexture;
  final Sampler defSampler;
  final BufferObject vbo;
  final BufferObject ibo;
  final BufferObject ubo;
  private final Encoder encoder;
  private final Device device;
  private boolean begun;

  /**
   * Creates a new {@code BatchedGraphics} backed by the given device.
   *
   * @param data   the staging area receiving vertices and indices
   * @param device the GPU device
   */
  public BatchedGraphics(VertexStore data, Device device) {
    super(data, device.getTransformHandler());

    Resource rp = Resource.classpath(BatchedGraphics.class);
    ShaderProgram spCol = ShaderProgram.load(device,
        rp.readString("/shaders/builtin_color.vert.hlsl"),
        rp.readString("/shaders/builtin_color.frag.hlsl"));
    ShaderProgram spTex = ShaderProgram.load(device,
        rp.readString("/shaders/builtin_texture.vert.hlsl"),
        rp.readString("/shaders/builtin_texture.frag.hlsl"));

    rslColor = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER));
    rslTexture = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_tex", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE, 0));

    vlColor = VertexLayout.XYZ_F32_RGBA_F16;
    vlTexture = VertexLayout.XYZ_F32_RGBA_F32_UV_F32;

    pipeColor = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.NOT_CULL)
        .shaderProgram(spCol).vertexLayout(vlColor).resourceLayouts(rslColor).build());
    pipeTexture = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.NOT_CULL)
        .shaderProgram(spTex).vertexLayout(vlTexture).resourceLayouts(rslTexture).build());

    defSampler = device.getSampler(SamplerDesc.DEFAULT);

    encoder = device.getEncoder(EncoderDesc.DEFAULT);
    vbo = device.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STREAM));
    vbo.allocate(1024, null);
    ibo = device.getBuffer(BufferObjectDesc.index(BufferFrequency.STREAM));
    ibo.allocate(512, null);
    ubo = device.getBuffer(BufferObjectDesc.uniform());
    ubo.allocate(64, null);
    this.device = device;
  }

  /**
   * Replays the sections of the given mesh with the current render state.
   *
   * <p>Pending draws are flushed first, and the mesh is drawn with the current camera,
   * viewport, and scissor.
   *
   * @param mesh the mesh to replay
   */
  @Override
  public void drawMesh(Mesh mesh) {
    flush();

    if (mesh.sections().isEmpty()) {
      return;
    }

    encoder.setViewport((int) viewport.minX(), (int) viewport.minY(), (int) viewport.width(), (int) viewport.height());
    encoder.setScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height(), scissor.enable());

    mesh.submit(encoder, 0, ubo);
  }

  /**
   * Submits pending vertex and index data to the GPU and executes the encoder.
   *
   * <p>If no vertex data has been recorded and {@code force} is {@code false}, nothing is
   * submitted. Otherwise the buffers are uploaded, the viewport, scissor, pipeline, and
   * resource set are applied, and the render pass is restarted for the next batch.
   *
   * @param force if {@code true}, submits even when no vertex data has been recorded
   */
  @Override
  public void flush(boolean force) {
    if (!force && data.vertices().readableBytes() == 0) {
      return;
    }
    submitBatch();
  }

  /**
   * Begins a render pass and prepares the staging buffers for a new frame.
   *
   * <p>Must be called before any draw commands.
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
    viewport = Rectangle.create(0, 0, rt.width(), rt.height());
    data.clear();
    currentPrimitive = null;
    currentTexture = null;
    encoder.beginPass(pass);
  }

  /**
   * Ends the render pass, submitting remaining draws and executing the encoder.
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

  @Override
  protected void onCameraChanged() {
    MatrixUtil.store(camera == null ? Matrix4x4.IDENTITY : camera.viewProjectionMatrix(), ubo);
  }

  /**
   * Releases all GPU resources: the encoder, vertex buffer, index buffer, uniform buffer,
   * pipelines, and default sampler.
   */
  @Override
  public void close() {
    encoder.close();
    vbo.close();
    ibo.close();
    ubo.close();
    pipeColor.close();
    pipeTexture.close();
    defSampler.close();
  }

  /**
   * Uploads the recorded vertex and index data and issues a draw with the current render
   * state.
   *
   * <p>Does nothing if no primitive has been selected or no camera is set.
   */
  private void submitBatch() {
    if (currentPrimitive == null) {
      return;
    }

    int vc = data.vertexCount();
    int ic = data.indexCount();
    int vr = data.vertices().readerIndex();
    int vw = data.vertices().writerIndex();
    int ir = data.indices().readerIndex();
    int iw = data.indices().writerIndex();
    byte[] rawV = data.recordVertices();
    byte[] rawI = data.recordIndices();

    // This won't actually clear array data
    // we've set volatile = true.
    data.clear();

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

      pipe = useTexture ? pipeTexture : pipeColor;
      ResourceSetLayout rsl = useTexture ? rslTexture : rslColor;
      rs = device.getResourceSet(rsl);
      rs.bindUniform(0, ubo, 64);

      if (currentTexture != null && useTexture) {
        Sampler usedSamp = sampler == null ? defSampler : sampler;
        rs.bindTexture(1, currentTexture, usedSamp);
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
   * Resolves the pipeline for the current primitive: the custom pipeline when set,
   * otherwise the matching built-in pipeline.
   *
   * @return the resolved pipeline
   */
  protected Pipeline resolvePipeline() {
    if (currentPipeline != null) {
      return currentPipeline;
    }
    return (currentPrimitive == null || currentPrimitive.isTextured()) ? pipeTexture : pipeColor;
  }

  /**
   * Resolves the resource set layout for the current primitive, matching
   * {@link #resolvePipeline()}.
   *
   * @return the resolved resource set layout
   */
  protected ResourceSetLayout resolveResourceSetLayout() {
    return (currentPrimitive == null || currentPrimitive.isTextured()) ? rslTexture : rslColor;
  }
}
