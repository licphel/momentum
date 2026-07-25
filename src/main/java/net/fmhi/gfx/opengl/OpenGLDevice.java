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

package net.fmhi.gfx.opengl;

import net.fmhi.gfx.BuiltinGfx;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.DeviceInfo;
import net.fmhi.gfx.View;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.cmd.EncoderDesc;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.PipelineDesc;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TextureDesc;
import net.fmhi.util.InternalApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * OpenGL implementation of {@link Device}.
 *
 * <p>GL commands are submitted from any thread via {@link #submit(Runnable)}
 * into a lock-free queue, then executed on the calling thread by {@link #pollEvents()} (the "command buffer execution"
 * pattern).
 *
 * <p><b>Thread safety:</b> {@link #submit} is safe from any thread.
 * {@link #pollEvents()} drains and executes queued work on the calling thread (which must be the GL context thread).
 * The {@link #cache} is only touched during {@code pollEvents}.
 *
 * @see OpenGLCache
 * @see View
 * @see Device
 */
@InternalApi
public final class OpenGLDevice implements Device {
  private static final Logger LOGGER = LogManager.getLogger();
  /**
   * Shared GL state cache — only accessed during {@link #pollEvents()}.
   */
  final OpenGLCache cache = new OpenGLCache();

  private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
  private final OpenGLSwapchain swapchain = new OpenGLSwapchain(this);
  @Nullable View view;

  /**
   * Creates a new device.
   *
   * <p>Call {@link #load(View)} before any GPU resource creation.
   */
  public OpenGLDevice() {
  }

  @Override
  public DeviceInfo info() {
    return new DeviceInfo("OpenGL (LWJGL 3)", true);
  }

  /**
   * Loads this device onto the given view.
   *
   * <p>Binds the GL context to the calling thread and initializes GL
   * capabilities. Must be called before any resource creation.
   */
  @Override
  public void load(View view) {
    ((Runnable) view.procAddress()).run();
    GL.createCapabilities();
    this.view = view;

    /*
     * Okay, so we handle this here.
     * I don't know if this will cause some problems -
     * like initialization order, or so.
     *
     * But we cannot assume that users can invoke it
     * correctly. At least it works for now.
     */
    BuiltinGfx.init(this);
  }

  @Override
  public BufferObject getBuffer(BufferObjectDesc desc) {
    return new OpenGLBufferObject(this, desc);
  }

  @Override
  public Texture getTexture(TextureDesc desc) {
    return new OpenGLTexture(this, desc);
  }

  @Override
  public Sampler getSampler(SamplerDesc desc) {
    return new OpenGLSampler(this, desc);
  }

  @Override
  public ShaderModule getShaderModule(ShaderModuleDesc desc) {
    return new OpenGLShaderModule(this, desc);
  }

  @Override
  public ShaderProgram getShaderProgram(ShaderModule... modules) {
    return new OpenGLShaderProgram(this, modules);
  }

  @Override
  public Pipeline getRenderPipeline(PipelineDesc desc) {
    return new OpenGLPipeline(this, desc);
  }

  @Override
  public ResourceSet getResourceSet(ResourceSetLayout layout) {
    return new OpenGLResourceSet(this, layout);
  }

  @Override
  public Encoder getEncoder(EncoderDesc desc) {
    return new OpenGLEncoder(this);
  }

  @Override
  public RenderTarget getRenderTarget() {
    return swapchain;
  }

  @Override
  public RenderTarget getRenderTarget(int width, int height) {
    return new OpenGLRenderTarget(this, width, height);
  }

  @Override
  public RenderTarget getRenderTarget(RenderTargetDesc desc) {
    if (desc.isSwapchain()) {
      return swapchain;
    }
    /*
     * Extended options are not supported yet.
     * TODO
     */
    return new OpenGLRenderTarget(this, desc.width(), desc.height());
  }

  @Override
  public RenderTarget getSwapchain() {
    return swapchain;
  }

  @Override
  public void submit(Runnable work) {
    queue.add(work);
  }

  @Override
  public void execute() {
    Runnable task;
    while ((task = queue.poll()) != null) {
      try {
        task.run();
      } catch (Exception e) {
        LOGGER.error("OpenGL execution error", e);
      }
    }
  }

  @Override
  public void pollEvents() {
    submit(() -> {
      int err;
      while ((err = GL11.glGetError()) != GL11.GL_NO_ERROR) {
        LOGGER.warn("OpenGL error: 0x{}", Integer.toHexString(err));
      }
    });
  }

  @Override
  public void close() {
    execute();
  }
}
