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

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pipe.Blend;
import io.viki.momentum.gfx.pipe.Depth;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.PipelineDesc;
import io.viki.momentum.gfx.pipe.RasterizationDesc;
import io.viki.momentum.gfx.pipe.Scissor;
import io.viki.momentum.gfx.shader.MatrixUtil;
import io.viki.momentum.gfx.shader.ResourceSet;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import io.viki.momentum.gfx.shader.ShaderProgram;
import io.viki.momentum.gfx.shader.ShaderLanguage;
import io.viki.momentum.gfx.shader.ShaderType;
import io.viki.momentum.gfx.shader.ResourceType;
import io.viki.momentum.gfx.shader.Slot;
import io.viki.momentum.gfx.shader.VertexLayout;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.SamplerDesc;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.gfx.texture.TextureWrap;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.util.impl.MeshGraphics;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.resource.Resource;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * UI-owned nine-tap backdrop blur effect.
 *
 * <p>The effect deliberately owns all shader, pipeline, uniform and capture resources here. The
 * generic graphics layer only sees the ordinary texture draw recorded by this class. It is not
 * thread-safe and must be used on the thread that owns its device.
 */
public final class BackdropBlurEffect implements AutoCloseable {
  private static final int CAMERA_UNIFORM_SIZE = 64;
  private static final int BLUR_UNIFORM_SIZE = 16;

  private final Device device;
  private final ShaderProgram shader;
  private final Pipeline pipeline;
  private final Sampler sampler;
  private final BufferObject cameraUniform;
  private final BufferObject blurUniform;
  private final ResourceSet resources;
  private final byte[] blurParameters = new byte[BLUR_UNIFORM_SIZE];
  private @Nullable RenderTarget capture;
  private int captureWidth;
  private int captureHeight;
  private boolean closed;

  /**
   * Creates a UI-owned backdrop effect using shaders packaged with the UI module.
   *
   * <p>The effect allocates a capture target, sampler, uniforms, and pipeline from the supplied
   * device. Call {@link #close()} when the dispatcher or device lifetime ends.
   *
   * @param device graphics device used to allocate effect resources
   */
  public BackdropBlurEffect(Device device) {
    this.device = device;
    Resource resource = Resource.classpath(BackdropBlurEffect.class);
    shader = ShaderProgram.load(device,
        resource.readString("/shaders/ui_frosted.vert.glsl"),
        resource.readString("/shaders/ui_frosted.frag.glsl"), ShaderLanguage.GLSL);
    ResourceSetLayout layout = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_tex", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE, 0),
        new Slot(1, "B", ShaderType.FRAGMENT_BIT, ResourceType.UNIFORM_BUFFER, 1));
    pipeline = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX)
        .depth(Depth.DISABLED)
        .rasterization(RasterizationDesc.NOT_CULL)
        .shaderProgram(shader)
        .vertexLayout(VertexLayout.XYZ_F32_RGBA_F32_UV_F32)
        .resourceLayouts(layout)
        .build());
    sampler = device.getSampler(new SamplerDesc.Builder()
        .magFilter(TextureFilter.LINEAR)
        .minFilter(TextureFilter.LINEAR)
        .mipmapFilter(TextureFilter.LINEAR)
        .wrapX(TextureWrap.CLAMP_TO_EDGE)
        .wrapY(TextureWrap.CLAMP_TO_EDGE)
        .wrapZ(TextureWrap.CLAMP_TO_EDGE)
        .build());
    cameraUniform = device.getBuffer(BufferObjectDesc.uniform());
    cameraUniform.allocate(CAMERA_UNIFORM_SIZE, null);
    blurUniform = device.getBuffer(BufferObjectDesc.uniform());
    blurUniform.allocate(BLUR_UNIFORM_SIZE, null);
    resources = device.getResourceSet(layout);
    resources.bindUniform(0, cameraUniform, CAMERA_UNIFORM_SIZE);
    resources.bindUniform(2, blurUniform, BLUR_UNIFORM_SIZE);
  }

  /**
   * Renders the current framebuffer behind a world-space UI rectangle with a soft blur.
   *
   * <p>The method flushes pending graphics before capturing the backdrop. It returns without
   * drawing when the graphics implementation cannot expose a render target or when the area is
   * empty. The effect is not usable after {@link #close()}.
   *
   * @param graphics graphics context containing the backdrop and destination target
   * @param area world-space rectangle receiving the blurred image
   * @return {@code true} when the backdrop was captured and drawn
   */
  public boolean draw(Graphics graphics, Rectangle area) {
    if (closed || graphics instanceof MeshGraphics || area.width() <= 0.0F
        || area.height() <= 0.0F) {
      return false;
    }
    RenderTarget target = graphics.renderTarget();
    Camera2D camera = graphics.camera();
    if (target == null || camera == null) {
      return false;
    }
    int width = target.width();
    int height = target.height();
    if (width <= 0 || height <= 0) {
      return false;
    }
    RenderTarget source = ensureCapture(width, height);
    graphics.flush();
    Scissor captureScissor = captureScissor(camera, graphics.currentViewport(), area,
        graphics.currentScissor());
    target.blit(source, 0, 0, width, height, 0, 0, width, height,
        TextureFilter.NEAREST, captureScissor);
    Texture texture = source.pin();
    if (texture == null) {
      return false;
    }

    MatrixUtil.store(camera.viewProjectionMatrix(), cameraUniform);
    ByteBuffer parameters = ByteBuffer.wrap(blurParameters).order(ByteOrder.nativeOrder());
    parameters.clear();
    parameters.putFloat(1.0F / width);
    parameters.putFloat(1.0F / height);
    parameters.putFloat(0.0F);
    parameters.putFloat(0.0F);
    blurUniform.submit(blurParameters);
    resources.bindTexture(1, texture, sampler);

    Rectangle sourceArea = sourceArea(camera, graphics.currentViewport(), area);
    Gradient previousTint = graphics.gradient();
    graphics.setTint(Color.WHITE);
    graphics.setPipeline(pipeline, resources);
    graphics.drawTexture(texture, area, sourceArea);
    graphics.setPipeline(null, null);
    graphics.setTint(previousTint);
    return true;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (capture != null) {
      capture.close();
      capture = null;
    }
    resources.close();
    blurUniform.close();
    cameraUniform.close();
    sampler.close();
    pipeline.close();
    shader.close();
  }

  private RenderTarget ensureCapture(int width, int height) {
    if (capture != null && captureWidth == width && captureHeight == height) {
      return capture;
    }
    if (capture != null) {
      capture.close();
    }
    capture = device.getRenderTarget(width, height);
    captureWidth = width;
    captureHeight = height;
    return capture;
  }

  private static Rectangle sourceArea(Camera2D camera, Rectangle viewport, Rectangle area) {
    Vector2 min = camera.project(new Vector2(area.minX(), area.minY()), viewport);
    Vector2 max = camera.project(new Vector2(area.maxX(), area.maxY()), viewport);
    float sourceX = Math.min(min.x(), max.x());
    float sourceY = Math.min(min.y(), max.y());
    float sourceWidth = Math.max(1.0F, Math.abs(max.x() - min.x()));
    float sourceHeight = Math.max(1.0F, Math.abs(max.y() - min.y()));
    return Rectangle.of(sourceX, sourceY, sourceWidth, sourceHeight);
  }

  private static Scissor captureScissor(Camera2D camera, Rectangle viewport, Rectangle area,
                                         Scissor parentScissor) {
    Rectangle projected = sourceArea(camera, viewport, area);
    int x = (int) Math.floor(projected.minX());
    int y = (int) Math.floor(projected.minY());
    int maxX = (int) Math.ceil(projected.maxX());
    int maxY = (int) Math.ceil(projected.maxY());
    if (parentScissor.enable()) {
      x = Math.max(x, parentScissor.x());
      y = Math.max(y, parentScissor.y());
      maxX = Math.min(maxX, parentScissor.x() + parentScissor.width());
      maxY = Math.min(maxY, parentScissor.y() + parentScissor.height());
    }
    return new Scissor(x, y, Math.max(0, maxX - x), Math.max(0, maxY - y), true);
  }
}
