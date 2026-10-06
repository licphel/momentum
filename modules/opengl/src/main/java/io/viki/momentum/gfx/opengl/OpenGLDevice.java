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
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.*;
import org.lwjgl.system.Callback;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * OpenGL implementation of {@link Device}.
 *
 * <p>GL commands are submitted from any thread via {@link #submit(Runnable)}
 * into a synchronized array queue and execute on the context thread via {@link #execute()}.
 *
 * <p><b>Thread safety:</b> submission is thread-safe; execution and the state cache
 * are confined to the GL context thread.
 *
 * @see OpenGLCache
 * @see View
 * @see Device
 */
@InternalApi
public final class OpenGLDevice implements Device {
  private static final Logger LOGGER = Log.getLogger();
  /** Caps diagnostic stack traces so a recurring driver error does not flood the log. */
  private static final int MAX_DEBUG_ERRORS = 16;
  /**
   * Shared GL state cache — only accessed by the context thread.
   */
  final OpenGLCache cache = new OpenGLCache();
  /**
   * Device-wide VAO cache, shared by all pipelines and invalidated when a buffer dies.
   */
  final VaoRegistry vaos = new VaoRegistry(this);

  private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
  private final OpenGLSwapchain swapchain = new OpenGLSwapchain(this);
  private final OpenGLTransformHandler transformHandler = new OpenGLTransformHandler();
  @Nullable View view;
  private long lastCheckErrorMs;
  private volatile @Nullable Callback debugCallback;
  private int debugErrors;

  /**
   * Creates a new device.
   *
   * <p>Call {@link #load(Object...)} before any GPU resource creation.
   */
  public OpenGLDevice() {
  }

  /**
   * Initializes this device for a GLFW view.
   *
   * <p>The view's context is owned by the calling thread. This must be called before
   * creating graphics resources.
   *
   * @param context platform values containing the view, backend identifier, and context lifecycle callbacks
   * @throws IllegalArgumentException if the backend is not GLFW
   */
  @Override
  public void load(Object... context) {
    this.view = (View) context[0];
    String bk = (String) context[1];

    if ("GLFW".equals(bk)) {
      ((Runnable) context[2]).run();
      GL.createCapabilities();
      if (view.isDebug()) {
        installDebugCallback();
      }
    } else {
      throw new IllegalArgumentException("OpenGL device only supports GLFW");
    }

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

  /** Registers the supported OpenGL debug callback for reporting driver errors. */
  private void installDebugCallback() {
    GLCapabilities caps = GL.getCapabilities();
    if (caps.OpenGL43 || caps.GL_KHR_debug) {
      GLDebugMessageCallback callback = GLDebugMessageCallback.create(
          (source, type, id, severity, length, message, user) -> {
            if (type == KHRDebug.GL_DEBUG_TYPE_ERROR) {
              reportDebugError(id, GLDebugMessageCallback.getMessage(length, message));
            }
          });
      debugCallback = callback;
      KHRDebug.glDebugMessageCallback(callback, 0L);
      GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
      GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
    } else if (caps.GL_ARB_debug_output) {
      GLDebugMessageARBCallback callback = GLDebugMessageARBCallback.create(
          (source, type, id, severity, length, message, user) -> {
            if (type == ARBDebugOutput.GL_DEBUG_TYPE_ERROR_ARB) {
              reportDebugError(id, GLDebugMessageARBCallback.getMessage(length, message));
            }
          });
      debugCallback = callback;
      ARBDebugOutput.glDebugMessageCallbackARB(callback, 0L);
      GL11.glEnable(ARBDebugOutput.GL_DEBUG_OUTPUT_SYNCHRONOUS_ARB);
    }
  }

  /**
   * Reports a driver error while limiting repeated diagnostic stack traces.
   *
   * @param id the driver error identifier
   * @param message the driver-provided error message
   */
  private void reportDebugError(int id, String message) {
    if (debugErrors >= MAX_DEBUG_ERRORS) {
      return;
    }
    debugErrors++;
    LOGGER.warnExc("OpenGL driver error 0x" + Integer.toHexString(id) + ": " + message,
        new IllegalStateException("Synchronous OpenGL call stack"));
    if (debugErrors == MAX_DEBUG_ERRORS) {
      LOGGER.warn("Further OpenGL error stacks suppressed for this device");
    }
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
    return new OpenGLResourceSet(layout);
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
    synchronized (queue) {
      queue.addLast(work);
    }
  }

  @Override
  public void execute() {
    synchronized (queue) {
      GraphicsMetrics.DeviceQueueSize.add(queue.size());
    }
    try {
      Runnable task;
      while ((task = pollCommand()) != null) task.run();
    } catch (Exception exception) {
      LOGGER.warn("OpenGL execution error", exception);
    }
  }

  private @Nullable Runnable pollCommand() {
    synchronized (queue) {
      return queue.pollFirst();
    }
  }

  @Override
  public void pollEvents() {
    if (view != null && view.isDebug() && debugCallback == null) {
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

  /**
   * Releases device-owned GL resources on the context thread.
   */
  @Override
  public void close() {
    submit(vaos::clear);
    submit(() -> {
      if (debugCallback != null) {
        var caps = GL.getCapabilities();
        if (caps.OpenGL43 || caps.GL_KHR_debug) {
          KHRDebug.glDebugMessageCallback(null, 0L);
        } else {
          ARBDebugOutput.glDebugMessageCallbackARB(null, 0L);
        }

        Objects.requireNonNull(debugCallback).free();
        debugCallback = null;
      }
    });
    execute();
  }

}
