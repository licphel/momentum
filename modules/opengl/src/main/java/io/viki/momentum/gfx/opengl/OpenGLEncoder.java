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

import io.viki.momentum.util.Analysis;

import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.cmd.Encoder;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.Topology;
import io.viki.momentum.gfx.shader.ResourceSet;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.util.Pool;
import io.viki.momentum.util.MspcRingBuffer;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

/**
 * Records graphics commands for ordered execution by an OpenGL device.
 *
 * <p>Recording calls are single-threaded per instance, and submitted batches execute on the
 * device's GL context thread. State remains in effect for later draws until a recording call
 * changes it. Resource bindings are captured when {@link #setResource(int, ResourceSet)} is called,
 * so later changes to that set do not alter commands already recorded.
 *
 * @see OpenGLDevice#submit(Runnable)
 */
@InternalApi
public final class OpenGLEncoder implements Encoder {
  private static final @Nullable Object[] EMPTY_REFERENCES = new Object[0];
  private static final Logger LOGGER = Log.getLogger();
  private static final int OP_BEGIN_PASS = 0;
  private static final int OP_END_PASS = 1;
  private static final int OP_SET_TOPOLOGY = 2;
  private static final int OP_SET_VERTEX_BUFFER = 3;
  private static final int OP_SET_INDEX_BUFFER = 4;
  private static final int OP_SET_INSTANCE_BUFFER = 5;
  private static final int OP_SET_INSTANCE_BASE = 6;
  private static final int OP_SET_RENDER_PIPE = 7;
  private static final int OP_SET_VIEWPORT = 8;
  private static final int OP_SET_SCISSOR = 9;
  private static final int OP_SET_RESOURCE = 10;
  private static final int OP_DRAW = 11;
  private static final int OP_DRAW_INDEXED = 12;
  private static final int OP_DRAW_INSTANCED = 13;
  private static final int OP_DRAW_INDEXED_INSTANCED = 14;
  private static final int OP_DISPATCH = 15;
  /**
   * Ring capacity: the consumer drains the ring every frame, so the capacity only
   * needs to hold one frame's worst-case stream (~35k ints today); the producer
   * spins only if the consumer ever runs behind.
   */
  private static final int RING_CAPACITY = 1 << 16;
  private static final int REF_CAPACITY = 1 << 4;
  private final Pool<BatchCommand> batches = new Pool<>();
  private final OpenGLDevice ctx;
  private final MspcRingBuffer ring = new MspcRingBuffer(RING_CAPACITY);
  // Per-frame recording state (single-threaded)
  private final @Nullable OpenGLResourceSet[] currentRss = new OpenGLResourceSet[64]; // Most support 64 sets
  private @Nullable Object[] refs = new Object[REF_CAPACITY];
  private int refCount;
  private int batchRefStart;
  /** Ints recorded since the batch start; the consumer polls exactly this many. */
  private int pendingInts;
  private int cmdCount;
  private @Nullable OpenGLPipeline currentPipe;
  private int currentVboHandle;
  private int currentEboHandle;
  private int currentInstHandle;
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
   * Discards the current recording state so this encoder can be reused.
   *
   * <p>Coordinate this call with recording; each encoder supports one recording thread at a time.
   */
  @Override
  public void reset() {
    refCount = 0;
    batchRefStart = 0;
    pendingInts = 0;
    cmdCount = 0;
    queryReset = false;
  }

  /**
   * Submits the recorded command batch for ordered execution on the device's GL context thread.
   *
   * <p>The batch is captured at submission, so this encoder can be reset and reused while it runs.
   */
  @Override
  public void queuedExecute() {
    Analysis.start("encoder.snapshotReferencesAndQueueCommands");
    try {
      int ints = pendingInts;
      pendingInts = 0;
      int refStart = batchRefStart;
      batchRefStart = refCount;
      @Nullable Object[] refsSnapshot = Arrays.copyOfRange(refs, refStart, refCount);
      queryReset = true;
      GraphicsMetrics.EncoderSum.add(cmdCount);

      BatchCommand command = batches.poll();
      if (command == null) {
        command = new BatchCommand(this);
      }
      command.intCount = ints;
      command.references = refsSnapshot;
      ctx.submit(command);
    } finally {
      Analysis.end("encoder.snapshotReferencesAndQueueCommands");
    }
  }

  @Override
  public void beginPass(RenderPass desc) {
    warnIfNotReset();

    int clearMask = 0;
    float cr = 0F;
    float cg = 0F;
    float cb = 0F;
    float ca = 0F;
    float cd = 1F;
    int cs = 0;
    if ((desc.clearMask() & RenderPass.CLEAR_COLOR) != 0) {
      Color color = desc.clearColor();
      cr = color.red();
      cg = color.green();
      cb = color.blue();
      ca = color.alpha();
      clearMask |= GL_COLOR_BUFFER_BIT;
    }
    if ((desc.clearMask() & RenderPass.CLEAR_DEPTH) != 0) {
      cd = (float) desc.clearDepth();
      clearMask |= GL_DEPTH_BUFFER_BIT;
    }
    if ((desc.clearMask() & RenderPass.CLEAR_STENCIL) != 0) {
      cs = desc.clearStencil();
      clearMask |= GL_STENCIL_BUFFER_BIT;
    }
    RenderTarget target = desc.target() == null ? ctx.getSwapchain() : desc.target();
    opStart(OP_BEGIN_PASS, 8);
    operand(refId(target));
    operand(clearMask);
    operand(Float.floatToRawIntBits(cr));
    operand(Float.floatToRawIntBits(cg));
    operand(Float.floatToRawIntBits(cb));
    operand(Float.floatToRawIntBits(ca));
    operand(Float.floatToRawIntBits(cd));
    operand(cs);
  }

  @Override
  public void endPass() {
    opStart(OP_END_PASS, 0);
  }

  @Override
  public void setTopology(Topology t) {
    opStart(OP_SET_TOPOLOGY, 1);
    operand(OpenGLUtils.topology(t));
  }

  @Override
  public void setVertexBuffer(BufferObject buffer) {
    opStart(OP_SET_VERTEX_BUFFER, 1);
    operand(refId(buffer));
  }

  @Override
  public void setIndexBuffer(BufferObject buffer) {
    opStart(OP_SET_INDEX_BUFFER, 1);
    operand(refId(buffer));
  }

  @Override
  public void setInstanceBuffer(@Nullable BufferObject buffer) {
    opStart(OP_SET_INSTANCE_BUFFER, 1);
    operand(refId(buffer));
  }

  @Override
  public void setInstanceBase(int baseInstance) {
    opStart(OP_SET_INSTANCE_BASE, 1);
    operand(baseInstance);
  }

  @Override
  public void setRenderPipe(Pipeline pipe) {
    opStart(OP_SET_RENDER_PIPE, 1);
    operand(refId(pipe));
  }

  @Override
  public void setViewport(int x, int y, int width, int height) {
    opStart(OP_SET_VIEWPORT, 4);
    operand(x);
    operand(y);
    operand(width);
    operand(height);
  }

  @Override
  public void setScissor(int x, int y, int width, int height, boolean enable) {
    opStart(OP_SET_SCISSOR, 5);
    operand(x);
    operand(y);
    operand(width);
    operand(height);
    operand(enable ? 1 : 0);
  }

  /**
   * Records a resource set for subsequent draw calls.
   *
   * <p>The set's bindings are captured at this call; later changes do not affect the recorded
   * commands.
   *
   * @param slot the shader binding slot
   * @param set the resource set whose current bindings are captured
   */
  @Override
  public void setResource(int slot, ResourceSet set) {
    opStart(OP_SET_RESOURCE, 2);
    operand(slot);
    operand(refId(((OpenGLResourceSet) set).snapshot()));
  }

  /**
   * Records a non-indexed draw for the active render pass.
   *
   * <p>Requires a vertex buffer and render pipeline to have been set.
   *
   * @param vertexCount the number of vertices to draw
   * @param firstVertex the index of the first vertex
   */
  @Override
  public void draw(int vertexCount, int firstVertex) {
    opStart(OP_DRAW, 2);
    operand(vertexCount);
    operand(firstVertex);
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
    opStart(OP_DRAW_INDEXED, 2);
    operand(indexCount);
    operand(firstIndex);
  }

  @Override
  public void drawInstanced(int vertexCount, int instanceCount, int firstVertex) {
    opStart(OP_DRAW_INSTANCED, 3);
    operand(vertexCount);
    operand(instanceCount);
    operand(firstVertex);
  }

  @Override
  public void drawIndexedInstanced(int indexCount, int instanceCount, int firstIndex) {
    opStart(OP_DRAW_INDEXED_INSTANCED, 3);
    operand(indexCount);
    operand(instanceCount);
    operand(firstIndex);
  }

  /**
   * Records a compute-shader dispatch.
   *
   * <p>The currently bound pipeline must contain a valid compute program.
   *
   * @param x the number of work groups in X
   * @param y the number of work groups in Y
   * @param z the number of work groups in Z
   */
  @Override
  public void dispatch(int x, int y, int z) {
    opStart(OP_DISPATCH, 3);
    operand(x);
    operand(y);
    operand(z);
  }

  @Override
  public void close() {
    refCount = 0;
    batchRefStart = 0;
    pendingInts = 0;
    cmdCount = 0;
  }

  /** Records an opcode; the caller then records its operands via {@link #operand(int)}. */
  private void opStart(int opcode, int operandCount) {
    ring.add(opcode);
    pendingInts += 1 + operandCount;
    cmdCount++;
  }

  /** Records one operand of the current command. */
  private void operand(int value) {
    ring.add(value);
  }

  /** Registers an object referenced by the current batch and returns its batch-local id. */
  private int refId(@Nullable Object obj) {
    if (refCount == refs.length) {
      refs = Arrays.copyOf(refs, refs.length * 2);
    }
    refs[refCount] = obj;
    return refCount++ - batchRefStart;
  }

  /**
   * Executes one submitted command batch on the render thread.
   *
   * @param intCount the number of recorded command values in the batch
   * @param pool the objects referenced by the batch's commands
   */
  private void executeBatch(int intCount, @Nullable Object[] pool) {
    Analysis.start("gl.decodeAndExecuteCommandBatch");
    try {
      int consumed = 0;
      while (consumed < intCount) {
        int op = ring.poll();
        consumed++;
        switch (op) {
          case OP_BEGIN_PASS -> {
            currentTarget = (RenderTarget) pool[ring.poll()];
            int clearMask = ring.poll();
            float cr = Float.intBitsToFloat(ring.poll());
            float cg = Float.intBitsToFloat(ring.poll());
            float cb = Float.intBitsToFloat(ring.poll());
            float ca = Float.intBitsToFloat(ring.poll());
            float cd = Float.intBitsToFloat(ring.poll());
            int cs = ring.poll();
            consumed += 8;

            if (currentTarget instanceof OpenGLSwapchain) {
              ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
            } else if (currentTarget instanceof OpenGLRenderTarget glTarget) {
              ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, glTarget.fboHandle());
            }

            if (clearMask != 0) {
              if ((clearMask & GL_COLOR_BUFFER_BIT) != 0) {
                glClearColor(cr, cg, cb, ca);
              }
              if ((clearMask & GL_DEPTH_BUFFER_BIT) != 0) {
                glClearDepth(cd);
              }
              if ((clearMask & GL_STENCIL_BUFFER_BIT) != 0) {
                glClearStencil(cs);
              }
              glClear(clearMask);
            }
          }
          case OP_END_PASS -> {
            if (currentTarget == null) {
              throw new GraphicsException("endPass called without a prior beginPass");
            }
            currentTarget = null;
          }
          case OP_SET_TOPOLOGY -> {
            topology = ring.poll();
            consumed++;
          }
          case OP_SET_VERTEX_BUFFER -> {
            Object o = pool[ring.poll()];
            currentVboHandle = o != null ? ((OpenGLBufferObject) o).handle : 0;
            consumed++;
          }
          case OP_SET_INDEX_BUFFER -> {
            Object o = pool[ring.poll()];
            currentEboHandle = o != null ? ((OpenGLBufferObject) o).handle : 0;
            consumed++;
          }
          case OP_SET_INSTANCE_BUFFER -> {
            Object o = pool[ring.poll()];
            currentInstHandle = o != null ? ((OpenGLBufferObject) o).handle : 0;
            consumed++;
          }
          case OP_SET_INSTANCE_BASE -> {
            currentInstanceBase = ring.poll();
            consumed++;
          }
          case OP_SET_RENDER_PIPE -> {
            Object o = pool[ring.poll()];
            if (o != null) {
              currentPipe = (OpenGLPipeline) o;
              currentPipe.apply(ctx.cache);
            }
            consumed++;
          }
          case OP_SET_VIEWPORT -> {
            int x = ring.poll();
            int y = ring.poll();
            int width = ring.poll();
            int height = ring.poll();
            consumed += 4;
            if (currentTarget == null) {
              throw new GraphicsException("setViewport called without an active render pass");
            }
            ctx.cache.setViewport(x, currentTarget.height() - y - height, width, height);
          }
          case OP_SET_SCISSOR -> {
            int x = ring.poll();
            int y = ring.poll();
            int width = ring.poll();
            int height = ring.poll();
            boolean enable = ring.poll() != 0;
            consumed += 5;
            if (currentTarget == null) {
              throw new GraphicsException("setScissor called without an active render pass");
            }
            ctx.cache.setScissor(x, currentTarget.height() - y - height, width, height, enable);
          }
          case OP_SET_RESOURCE -> {
            int slot = ring.poll();
            if (slot >= currentRss.length) {
              throw new GraphicsException("Mostly support 64 slots, but got " + slot);
            }
            currentRss[slot] = (OpenGLResourceSet) pool[ring.poll()];
            consumed += 2;
          }
          case OP_DRAW -> {
            int vertexCount = ring.poll();
            int firstVertex = ring.poll();
            consumed += 2;
            if (currentVboHandle == 0) {
              throw new GraphicsException("VBO not bound");
            }
            if (currentPipe == null) {
              throw new GraphicsException("Pipeline not bound");
            }

            applyResources();

            int vao = currentPipe.acquireVao(currentVboHandle, 0, 0, currentInstanceBase);
            ctx.cache.bindVao(vao);
            GraphicsMetrics.Drawcalls.increment();
            Analysis.start("gl.glDrawArrays");
            try {
              glDrawArrays(topology, firstVertex, vertexCount);
            } finally {
              Analysis.end("gl.glDrawArrays");
            }
            ctx.cache.bindVao(0);
          }
          case OP_DRAW_INDEXED -> {
            int indexCount = ring.poll();
            int firstIndex = ring.poll();
            consumed += 2;
            if (currentVboHandle == 0) {
              throw new GraphicsException("VBO not bound");
            }
            if (currentEboHandle == 0) {
              throw new GraphicsException("EBO not bound");
            }
            if (currentPipe == null) {
              throw new GraphicsException("Pipeline not bound");
            }

            applyResources();

            int vao = currentPipe.acquireVao(currentVboHandle, 0, currentEboHandle, currentInstanceBase);
            ctx.cache.bindVao(vao);
            GraphicsMetrics.Drawcalls.increment();
            Analysis.start("gl.glDrawElements");
            try {
              glDrawElements(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES);
            } finally {
              Analysis.end("gl.glDrawElements");
            }
            ctx.cache.bindVao(0);
          }
          case OP_DRAW_INSTANCED -> {
            int vertexCount = ring.poll();
            int instanceCount = ring.poll();
            int firstVertex = ring.poll();
            consumed += 3;
            if (currentVboHandle == 0) {
              throw new GraphicsException("VBO not bound");
            }
            if (currentPipe == null) {
              throw new GraphicsException("Pipeline not bound");
            }

            applyResources();

            int vao = currentPipe.acquireVao(currentVboHandle, currentInstHandle, 0, currentInstanceBase);
            ctx.cache.bindVao(vao);
            GraphicsMetrics.Drawcalls.increment();
            Analysis.start("gl.glDrawArraysInstanced");
            try {
              glDrawArraysInstanced(topology, firstVertex, vertexCount, instanceCount);
            } finally {
              Analysis.end("gl.glDrawArraysInstanced");
            }
            ctx.cache.bindVao(0);
          }
          case OP_DRAW_INDEXED_INSTANCED -> {
            int indexCount = ring.poll();
            int instanceCount = ring.poll();
            int firstIndex = ring.poll();
            consumed += 3;
            if (currentVboHandle == 0) {
              throw new GraphicsException("VBO not bound");
            }
            if (currentEboHandle == 0) {
              throw new GraphicsException("EBO not bound");
            }
            if (currentPipe == null) {
              throw new GraphicsException("Pipeline not bound");
            }

            applyResources();

            int vao = currentPipe.acquireVao(currentVboHandle, currentInstHandle, currentEboHandle, currentInstanceBase);
            ctx.cache.bindVao(vao);
            GraphicsMetrics.Drawcalls.increment();
            Analysis.start("gl.glDrawElementsInstanced");
            try {
              glDrawElementsInstanced(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES, instanceCount);
            } finally {
              Analysis.end("gl.glDrawElementsInstanced");
            }
            ctx.cache.bindVao(0);
          }
          case OP_DISPATCH -> {
            int x = ring.poll();
            int y = ring.poll();
            int z = ring.poll();
            consumed += 3;
            GraphicsMetrics.Drawcalls.increment();
            glDispatchCompute(x, y, z);
          }
          default -> throw new GraphicsException("Unknown command opcode: " + op);
        }
      }
    } finally {
      Analysis.end("gl.decodeAndExecuteCommandBatch");
    }
  }

  private void warnIfNotReset() {
    if (queryReset && !loggedResetWarn) {
      loggedResetWarn = true;
      LOGGER.warn("Encoder is not reset after use. Have you forgotten it?");
    }
  }

  /**
   * Validates and applies the resource sets required by the current pipeline.
   *
   * @throws GraphicsException if a required resource set is missing or incompatible
   */
  private void applyResources() {
    Analysis.start("gl.validateAndBindResources");
    try {
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
    } finally {
      Analysis.end("gl.validateAndBindResources");
    }
  }

  private static final class BatchCommand implements Runnable {
    private final OpenGLEncoder encoder;
    private int intCount;
    private @Nullable Object[] references = EMPTY_REFERENCES;

    private BatchCommand(OpenGLEncoder encoder) {
      this.encoder = encoder;
    }

    @Override
    public void run() {
      try {
        encoder.executeBatch(intCount, references);
      } finally {
        references = EMPTY_REFERENCES;
        intCount = 0;
        encoder.batches.release(this);
      }
    }
  }
}
