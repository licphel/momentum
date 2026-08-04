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

import net.fmhi.gfx.GfxStats;
import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Topology;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.math.Color;
import net.fmhi.util.InternalApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

/**
 * OpenGL implementation of {@link Encoder} that records GPU commands into a list of {@link Runnable} lambdas.
 *
 * <p>Commands are accumulated in an {@link ArrayList} on the calling thread.
 * When {@link #queuedExecute()} is called, the entire list is snapshot and posted to the render thread as a single
 * {@link OpenGLDevice#submit(Runnable)} call. This batching avoids per-command queue overhead.
 *
 * <p><b>State tracking:</b> the encoder remembers the last-set pipeline,
 * vertex buffer, index buffer, and topology on the recording thread so that {@link #draw} and {@link #drawIndexed} can
 * capture the correct state without requiring it to be re-specified before every draw call.
 *
 * <p><b>Thread safety:</b> recording is single-threaded per instance.
 * {@link #queuedExecute()} and {@link #reset()} may be called from any thread.
 *
 * @see OpenGLDevice#submit(Runnable)
 */
@InternalApi
public final class OpenGLEncoder implements Encoder {
  private static final Logger LOGGER = LogManager.getLogger();

  private final OpenGLDevice ctx;
  private final List<Runnable> commands = new ArrayList<>();
  private final @Nullable OpenGLResourceSet[] currentRss = new OpenGLResourceSet[64]; // Most support 64 sets
  // Per-frame recording state (single-threaded)
  private @Nullable OpenGLPipeline currentPipe;
  private @Nullable OpenGLBufferObject currentVbo;
  private @Nullable OpenGLBufferObject currentEbo;
  private @Nullable OpenGLBufferObject currentInstanceVbo;
  private @Nullable RenderTarget currentTarget;
  private int topology = GL_TRIANGLES;
  private boolean queryReset;
  private int currentInstanceBase;
  private boolean loggedResetWarn;

  /**
   * Creates a new encoder backed by the given GL context.
   *
   * @param ctx the GL context whose render thread executes the commands
   */
  OpenGLEncoder(OpenGLDevice ctx) {
    this.ctx = ctx;
  }

  /**
   * Discards all recorded commands and resets per-frame tracking state.
   *
   * <p>Safe to call from any thread. After reset, the encoder has no
   * remembered pipeline, buffers, or viewport.
   */
  @Override
  public void reset() {
    commands.clear();
    currentPipe = null;
    currentVbo = null;
    currentEbo = null;
    currentInstanceVbo = null;
    currentTarget = null;
    queryReset = false;
  }

  /**
   * Snapshots the command list and submits it to the render thread.
   *
   * <p>The snapshot decouples the submission from further recording:
   * {@link #reset()} may be called immediately without affecting the batch that is about to execute.
   */
  @Override
  public void queuedExecute() {
    List<Runnable> snapshot = new ArrayList<>(commands);
    queryReset = true;
    GfxStats.ECMDPT.add(snapshot.size());

    ctx.submit(() -> {
      for (Runnable cmd : snapshot) {
        cmd.run();
      }
    });
  }

  @Override
  public void beginPass(RenderPass desc) {
    warnIfNotReset();

    commands.add(() -> {
      currentTarget = desc.target() == null ? ctx.getSwapchain() : desc.target();

      if (currentTarget instanceof OpenGLSwapchain swapchain) {
        ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
      } else if (currentTarget instanceof OpenGLRenderTarget glRenderTarget) {
        ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, glRenderTarget.fboHandle());
      }

      int glMask = 0;
      if ((desc.clearMask() & RenderPass.CLEAR_COLOR) != 0) {
        Color color = desc.clearColor();
        glClearColor(color.red(), color.green(), color.blue(), color.alpha());
        glMask |= GL_COLOR_BUFFER_BIT;
      }
      if ((desc.clearMask() & RenderPass.CLEAR_DEPTH) != 0) {
        glClearDepth(desc.clearDepth());
        glMask |= GL_DEPTH_BUFFER_BIT;
      }
      if ((desc.clearMask() & RenderPass.CLEAR_STENCIL) != 0) {
        glClearStencil(desc.clearStencil());
        glMask |= GL_STENCIL_BUFFER_BIT;
      }
      if (glMask != 0) {
        glClear(glMask);
      }
    });
  }

  @Override
  public void endPass() {
    commands.add(() -> {
      if (currentTarget == null) {
        throw new GraphicsException("endPass called without a prior beginPass");
      }
      currentTarget = null;
    });
  }

  @Override
  public void setTopology(Topology t) {
    int glTopo = OpenGLUtils.topology(t);
    commands.add(() -> topology = glTopo);
  }

  @Override
  public void setVertexBuffer(BufferObject buffer) {
    OpenGLBufferObject glBuf = (OpenGLBufferObject) buffer;
    commands.add(() -> currentVbo = glBuf);
  }

  @Override
  public void setIndexBuffer(BufferObject buffer) {
    OpenGLBufferObject glBuf = (OpenGLBufferObject) buffer;
    commands.add(() -> currentEbo = glBuf);
  }

  @Override
  public void setInstanceBuffer(@Nullable BufferObject buffer) {
    if (buffer == null) {
      commands.add(() -> currentInstanceVbo = null);
      return;
    }
    OpenGLBufferObject glBuf = (OpenGLBufferObject) buffer;
    commands.add(() -> currentInstanceVbo = glBuf);
  }

  @Override
  public void setInstanceBase(int baseInstance) {
    commands.add(() -> currentInstanceBase = baseInstance);
  }

  @Override
  public void setRenderPipe(Pipeline pipe) {
    OpenGLPipeline glPipe = (OpenGLPipeline) pipe;
    commands.add(() -> {
      currentPipe = glPipe;
      glPipe.apply(ctx.cache);
    });
  }

  @Override
  public void setViewport(int x, int y, int width, int height) {
    commands.add(() -> {
      if (currentTarget == null) {
        throw new GraphicsException("setViewport called without an active render pass");
      }

      int fbHeight = currentTarget.height();
      int glY = fbHeight - y - height;
      ctx.cache.setViewport(x, glY, width, height);
    });
  }

  @Override
  public void setScissor(int x, int y, int width, int height, boolean enable) {
    commands.add(() -> {
      if (currentTarget == null) {
        throw new GraphicsException("setScissor called without an active render pass");
      }

      int fbHeight = currentTarget.height();
      int glY = fbHeight - y - height;
      ctx.cache.setScissor(x, glY, width, height, enable);
    });
  }

  @Override
  public void setResource(int slot, ResourceSet set) {
    OpenGLResourceSet rs = (OpenGLResourceSet) set;
    commands.add(() -> {
      if (slot >= currentRss.length) {
        throw new GraphicsException("Mostly support 64 slots, but got " + slot);
      }
      currentRss[slot] = rs;
    });
  }

  /**
   * Records a non-indexed draw call.
   *
   * <p>Acquires a VAO for the current (VBO, 0) pair from the pipeline's VAO cache, binds it, issues
   * {@code glDrawArrays},
   * and unbinds.
   */
  @Override
  public void draw(int vertexCount, int firstVertex) {
    commands.add(() -> {
      if (currentVbo == null) {
        throw new GraphicsException("VBO not bound");
      }
      if (currentPipe == null) {
        throw new GraphicsException("Pipeline not bound");
      }

      applyResources();

      int vao = currentPipe.acquireVao(currentVbo.handle, 0, 0, currentInstanceBase);
      ctx.cache.bindVao(vao);
      GfxStats.DCPT.increment();
      glDrawArrays(topology, firstVertex, vertexCount);
      ctx.cache.bindVao(0);
    });
  }

  /**
   * Records an indexed draw call.
   *
   * <p>The index buffer is treated as {@code GL_UNSIGNED_INT}.
   *
   * @param indexCount number of indices to draw
   * @param firstIndex index of the first element (byte offset = {@code firstIndex * 4})
   */
  @Override
  public void drawIndexed(int indexCount, int firstIndex) {
    commands.add(() -> {
      if (currentVbo == null) {
        throw new GraphicsException("VBO not bound");
      }
      if (currentEbo == null) {
        throw new GraphicsException("EBO not bound");
      }
      if (currentPipe == null) {
        throw new GraphicsException("Pipeline not bound");
      }

      applyResources();

      int vao = currentPipe.acquireVao(currentVbo.handle, 0, currentEbo.handle, currentInstanceBase);
      ctx.cache.bindVao(vao);
      GfxStats.DCPT.increment();
      glDrawElements(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES);
      ctx.cache.bindVao(0);
    });
  }

  @Override
  public void drawInstanced(int vertexCount, int instanceCount, int firstVertex) {
    commands.add(() -> {
      if (currentVbo == null) {
        throw new GraphicsException("VBO not bound");
      }
      if (currentPipe == null) {
        throw new GraphicsException("Pipeline not bound");
      }

      applyResources();

      int instHandle = currentInstanceVbo != null ? currentInstanceVbo.handle : 0;
      int vao = currentPipe.acquireVao(currentVbo.handle, instHandle, 0, currentInstanceBase);
      ctx.cache.bindVao(vao);
      GfxStats.DCPT.increment();
      glDrawArraysInstanced(topology, firstVertex, vertexCount, instanceCount);
      ctx.cache.bindVao(0);
    });
  }

  @Override
  public void drawIndexedInstanced(int indexCount, int instanceCount, int firstIndex) {
    commands.add(() -> {
      if (currentVbo == null) {
        throw new GraphicsException("VBO not bound");
      }
      if (currentEbo == null) {
        throw new GraphicsException("EBO not bound");
      }
      if (currentPipe == null) {
        throw new GraphicsException("Pipeline not bound");
      }

      applyResources();

      int instHandle = currentInstanceVbo != null ? currentInstanceVbo.handle : 0;
      int vao = currentPipe.acquireVao(currentVbo.handle, instHandle, currentEbo.handle, currentInstanceBase);
      ctx.cache.bindVao(vao);
      GfxStats.DCPT.increment();
      glDrawElementsInstanced(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES, instanceCount);
      ctx.cache.bindVao(0);
    });
  }

  /**
   * Records a compute-shader dispatch.
   *
   * <p>The currently bound pipeline must contain a valid compute program.
   */
  @Override
  public void dispatch(int x, int y, int z) {
    commands.add(() -> {
      GfxStats.DCPT.increment();
      glDispatchCompute(x, y, z);
    });
  }

  @Override
  public void close() {
    commands.clear();
  }

  private void warnIfNotReset() {
    if (queryReset && !loggedResetWarn) {
      loggedResetWarn = true;
      LOGGER.warn("Encoder is not reset after use. Do you forget it?");
    }
  }

  private void applyResources() {
    assert currentPipe != null;

    ResourceSetLayout[] layouts = currentPipe.desc().resourceLayouts();
    for (int i = 0; i < layouts.length; i++) {
      OpenGLResourceSet rs = currentRss[i];
      if (rs == null) {
        throw new GraphicsException("Null resource layout at slot " + i);
      }

      rs.validate(layouts[i]);
      rs.apply(ctx.cache);
    }
  }
}
