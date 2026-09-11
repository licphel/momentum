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

package io.viki.momentum.gfx.opengl;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.cmd.Encoder;
import io.viki.momentum.gfx.cmd.EncoderDesc;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pass.RenderTargetDesc;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.PipelineDesc;
import io.viki.momentum.gfx.shader.*;
import io.viki.momentum.gfx.text.FallbackFont;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.SamplerDesc;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureDesc;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
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
  private static final Logger LOGGER = Log.getLogger();
  /**
   * Shared GL state cache — only accessed during {@link #pollEvents()}.
   */
  final OpenGLCache cache = new OpenGLCache();
  /**
   * Device-wide VAO cache, shared by all pipelines and invalidated when a buffer dies.
   */
  final VaoRegistry vaos = new VaoRegistry(this);

  private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
  private final OpenGLSwapchain swapchain = new OpenGLSwapchain(this);
  private final OpenGLTransformHandler transformHandler = new OpenGLTransformHandler();
  @Nullable View host;
  private long lastCheckErrorMs;

  /**
   * Creates a new device.
   *
   * <p>Call {@link #load(View)} before any GPU resource creation.
   */
  public OpenGLDevice() {
  }

  /**
   * Loads this device onto the given view.
   *
   * <p>Binds the GL context to the calling thread and initializes GL
   * capabilities. Must be called before any resource creation.
   */
  @Override
  public void load(View host) {
    ((Runnable) host.procAddress()).run();
    GL.createCapabilities();
    this.host = host;

    /*
     * Okay, so we handle this here.
     * I don't know if this will cause some problems -
     * like initialization order, or so.
     *
     * But we cannot assume that users can invoke it
     * correctly. At least it works for now.
     */
    FallbackFont.init(this);
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
  public ShaderCompiler getShaderCompiler() {
    return new ShadercCompiler();
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
  public TransformHandler getTransformHandler() {
    return transformHandler;
  }

  @Override
  public void submit(Runnable work) {
    queue.add(work);
  }

  @Override
  public void execute() {
    Runnable task;
    GraphicsMetrics.DeviceQueueSize.add(queue.size());
    try {
      while ((task = queue.poll()) != null) {
        task.run();
      }
    } catch (Exception e) {
      LOGGER.warn("OpenGL execution error", e);
    }
  }

  @Override
  public void pollEvents() {
    if (host != null && host.isDebug()) {
      long ms = System.currentTimeMillis();

      if (ms - lastCheckErrorMs > 1000) {
        lastCheckErrorMs = ms;
        submit(() -> {
          int err;
          while ((err = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            LOGGER.warn("OpenGL error: 0x{}", Integer.toHexString(err));
          }
        });
      }
    }
  }

  @Override
  public void close() {
    submit(vaos::clear);
    execute();
  }
}
