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

package net.fmhi.gfx;

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

/**
 * Represents a graphics output device (hardware or software).
 *
 * <p>A {@code Device} is the entry point for all GPU resource creation.
 * It owns the connection to the underlying graphics backend (OpenGL, Vulkan, etc.) and is the factory for all GPU
 * resources — buffers, textures, samplers, shaders, pipelines, resource sets, encoders, and render targets.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Create GPU resources with the {@code create*} factory methods.
 *   <li>Call {@link #pollEvents()} once per frame in the main loop.
 *   <li>Call {@link #close()} when shutting down the graphics subsystem.
 * </ol>
 *
 * <p>Devices are generally long-lived; close them only when shutting down
 * the graphics subsystem entirely.
 *
 * @see DeviceInfo
 */
public interface Device extends AutoCloseable {
  /**
   * Returns static information about this device.
   *
   * @return name and hardware-acceleration flag for this device
   */
  DeviceInfo info();

  /**
   * Loads this device onto the given view.
   *
   * <p>The device extracts the opaque native handle via {@link View#procAddress()}
   * and hooks internal callbacks (e.g. resize) on the view automatically. Must be called before any resource creation.
   *
   * @param view the view to render into
   */
  void load(View view);

  /**
   * Creates a new GPU buffer (vertex, index, or uniform).
   *
   * @param desc the buffer type, frequency, and size hint
   * @return a new buffer backed by this device
   */
  BufferObject getBuffer(BufferObjectDesc desc);

  /**
   * Creates a new GPU texture (1D, 2D, or 3D).
   *
   * <p>If the descriptor includes {@link TextureDesc#initialBytes()}, they are
   * uploaded during creation. Otherwise, the texture is left uninitialized.
   *
   * @param desc the texture dimensions, format, type, and optional initial data
   * @return a new texture backed by this device
   */
  Texture getTexture(TextureDesc desc);

  /**
   * Creates a new GPU sampler object.
   *
   * @param desc the sampler filtering, wrapping, and LOD parameters
   * @return a new sampler backed by this device
   */
  Sampler getSampler(SamplerDesc desc);

  /**
   * Creates a new compiled shader module (a single pipeline stage).
   *
   * @param desc the shader stage type and GLSL source code
   * @return a new compiled shader module
   * @throws GraphicsException if compilation fails
   */
  ShaderModule getShaderModule(ShaderModuleDesc desc);

  /**
   * Creates a new linked shader program from one or more compiled modules.
   *
   * <p>A graphics pipeline requires at least a vertex and fragment module;
   * compute pipelines use a single compute module.
   *
   * @param modules the compiled shader modules to link
   * @return a new linked shader program
   * @throws GraphicsException if linking fails
   */
  ShaderProgram getShaderProgram(ShaderModule... modules);

  /**
   * Creates a new immutable render pipeline.
   *
   * <p>Pipelines are heavy objects that should be created once and reused
   * across many frames.
   *
   * @param desc the blend, depth, stencil, rasterization, shader, and vertex layout
   * @return a new render pipeline backed by this device
   */
  Pipeline getRenderPipeline(PipelineDesc desc);

  /**
   * Creates a new, empty resource set.
   *
   * <p>Bind textures, samplers, and uniform buffers via
   * {@link ResourceSet#bindTexture} and {@link ResourceSet#bindUniform} before using the set in a draw call.
   *
   * @param layout the resource set layout declaring valid slots
   * @return a new resource set backed by this device
   */
  ResourceSet getResourceSet(ResourceSetLayout layout);

  /**
   * Creates a new command encoder for recording GPU commands.
   *
   * @param desc encoder usage, extended info
   * @return a new encoder backed by this device
   */
  Encoder getEncoder(EncoderDesc desc);

  /**
   * Creates a render target wrapping the default framebuffer (swapchain).
   *
   * <p>Use this for rendering directly to the window view.
   *
   * @return a render target for the default framebuffer
   */
  RenderTarget getRenderTarget();

  /**
   * Creates an off-screen render target with the given dimensions.
   *
   * <p>The target has an RGBA8 color attachment and a depth24/stencil8
   * render buffer.
   *
   * @param width  the FBO width in pixels
   * @param height the FBO height in pixels
   * @return an off-screen render target
   */
  RenderTarget getRenderTarget(int width, int height);

  /**
   * Creates a render target from a full descriptor.
   *
   * @param desc color attachment formats, depth/stencil, sample count
   * @return a new render target backed by this device
   */
  RenderTarget getRenderTarget(RenderTargetDesc desc);

  /**
   * Returns the swapchain render target representing the screen.
   *
   * <p>The swapchain is a per-device singleton.
   *
   * @return the screen render target
   */
  RenderTarget getSwapchain();

  /**
   * Enqueues a block of work to run on the render thread.
   *
   * <p>Thread-safe: may be called from any thread. Implementations must
   * ensure the work executes on the thread that owns the graphics context.
   *
   * @param work the commands to execute, wrapped in a {@link Runnable}
   */
  void submit(Runnable work);

  /**
   * Drains and executes all pending GPU commands on the calling thread.
   *
   * <p>Must be called from the GL context thread every frame.
   */
  void execute();

  /**
   * Checks for GPU errors. Submits via {@link #submit} so errors are reported in order with other queued work.
   */
  void pollEvents();

  /**
   * Releases all native resources held by this device.
   *
   * <p>After closing, no further GPU operations may be performed.
   * Implementations should be idempotent.
   */
  @Override
  void close();
}
