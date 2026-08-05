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

import net.fmhi.collection.MSPCRingBuffer;
import net.fmhi.gfx.GfxMetrics;
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

import java.util.Arrays;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

/**
 * OpenGL implementation of {@link Encoder} that records GPU commands into a bounded
 * {@link MSPCRingBuffer} of opcodes, executed by a switch on the render thread.
 *
 * <p>Commands are recorded as opcode + operands straight into the ring; a producer
 * whose recording would overflow the ring spins until the consumer frees a slot, so
 * recording can never run unbounded ahead of execution. Backend objects (pipelines,
 * resource sets, targets, buffers) cannot live in an int ring, so they are kept in a
 * per-batch reference pool and referenced by index. Buffer handles are read at
 * execution time, because they are assigned asynchronously at buffer creation.
 * Recording a command costs a few ints instead of a {@link Runnable} object, and
 * {@link #queuedExecute()} submits a consumer task that polls the batch straight off
 * the ring — no stream copy.
 *
 * <p><b>State tracking:</b> the encoder remembers the last-set pipeline,
 * vertex buffer, index buffer, and topology on the recording thread so that {@link #draw}
 * and {@link #drawIndexed} can capture the correct state without requiring it to be
 * re-specified before every draw call. Every call still records exactly one command; a
 * Vulkan backend can map them to {@code vkCmd*} calls verbatim.
 *
 * <p><b>Thread safety:</b> recording is single-threaded per instance.
 * {@link #queuedExecute()} and {@link #reset()} may be called from any thread.
 *
 * @see OpenGLDevice#submit(Runnable)
 */
@InternalApi
public final class OpenGLEncoder implements Encoder {
  private static final Logger LOGGER = LogManager.getLogger();

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

  /** Ring capacity: the consumer drains the ring every frame, so the capacity only
   * needs to hold one frame's worst-case stream (~35k ints today); the producer
   * spins only if the consumer ever runs behind. */
  private static final int RING_CAPACITY = 1 << 16;
  private static final int REF_CAPACITY = 1 << 4;

  private final OpenGLDevice ctx;
  private final MSPCRingBuffer ring = new MSPCRingBuffer(RING_CAPACITY);
  private @Nullable Object[] refs = new Object[REF_CAPACITY];
  private int refCount;
  private int batchRefStart;
  /** Ints recorded since the batch start; the consumer polls exactly this many. */
  private int pendingInts;
  private int cmdCount;
  // Per-frame recording state (single-threaded)
  private final @Nullable OpenGLResourceSet[] currentRss = new OpenGLResourceSet[64]; // Most support 64 sets
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
   * Discards the current batch's recording state.
   *
   * <p>The ring itself is not touched — its content belongs to the consumer —
   * and the executed-side state is not cleared either: every batch re-establishes
   * the state it needs from its own commands, and clearing it here would race a
   * concurrently executing consumer.
   *
   * <p>Safe to call from any thread.
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
   * Hands the recorded batch to the render thread: a consumer task polls exactly
   * the batch's ints off the ring and executes them in order. The batch's
   * reference pool is snapshot so the producer can start recording the next batch
   * immediately.
   */
  @Override
  public void queuedExecute() {
    int ints = pendingInts;
    pendingInts = 0;
    int refStart = batchRefStart;
    batchRefStart = refCount;
    @Nullable Object[] refsSnapshot = Arrays.copyOfRange(refs, refStart, refCount);
    queryReset = true;
    GfxMetrics.ECMDPT.add(cmdCount);

    ctx.submit(() -> executeBatch(ints, refsSnapshot));
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

  @Override
  public void setResource(int slot, ResourceSet set) {
    opStart(OP_SET_RESOURCE, 2);
    operand(slot);
    operand(refId(set));
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

  /**
   * Executes one recorded batch on the render thread, polling the batch's
   * opcodes and operands straight off the ring.
   *
   * @param intCount the number of ints the batch occupies in the ring
   * @param pool     the batch's referenced objects
   */
  private void executeBatch(int intCount, @Nullable Object[] pool) {
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
          GfxMetrics.DCPT.increment();
          glDrawArrays(topology, firstVertex, vertexCount);
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
          GfxMetrics.DCPT.increment();
          glDrawElements(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES);
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
          GfxMetrics.DCPT.increment();
          glDrawArraysInstanced(topology, firstVertex, vertexCount, instanceCount);
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
          GfxMetrics.DCPT.increment();
          glDrawElementsInstanced(topology, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES, instanceCount);
          ctx.cache.bindVao(0);
        }
        case OP_DISPATCH -> {
          int x = ring.poll();
          int y = ring.poll();
          int z = ring.poll();
          consumed += 3;
          GfxMetrics.DCPT.increment();
          glDispatchCompute(x, y, z);
        }
        default -> throw new GraphicsException("Unknown command opcode: " + op);
      }
    }
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
