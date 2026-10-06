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

import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.gfx.buffer.BufferFrequency;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.buffer.BufferObjectDesc;
import io.viki.momentum.gfx.buffer.BufferUsage;
import io.viki.momentum.util.Handle;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.util.Pool;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL buffer object implementation supporting vertex, index, and uniform buffer usage.
 *
 * <p>Every buffer has a target ({@code GL_ARRAY_BUFFER}, {@code GL_ELEMENT_ARRAY_BUFFER},
 * or {@code GL_UNIFORM_BUFFER}) and a usage hint derived from its {@link BufferFrequency}. All GL calls are enqueued
 * via {@link OpenGLDevice#submit(Runnable)} for execution on the render thread.
 *
 * <p><b>Buf orphaning:</b> for {@link BufferFrequency#STREAM} buffers,
 * a {@link #submit(byte[], int, int)} call at offset 0 discards the previous allocation (via an extra
 * {@code glBufferData}) before writing. This technique avoids GPU pipeline stalls by letting the driver rotate through
 * fresh backing storage each frame. The trade-off is an extra allocation per frame.
 *
 * <p><b>Auto-expansion:</b> {@link #canExpand()} always returns {@code true}.
 * If a {@link #submit} would overflow the current capacity, the buffer is transparently reallocated to at least double
 * the previous size.
 *
 * <p><b>Thread safety:</b> creation, mutation ({@link #allocate}, {@link #submit}),
 * and {@link #close()} submit work to the render thread and are safe to call from any thread. Reads ({@link #desc()},
 * {@link #capacity()}) return values that may be stale if a pending submission has not yet executed.
 */
@InternalApi
public final class OpenGLBufferObject implements BufferObject, Handle {
  private static final ByteBuffer EMPTY_UPLOAD = ByteBuffer.allocate(0);
  private final OpenGLDevice ctx;
  private final BufferObjectDesc desc;
  private final int target;
  private final int hint;
  private final Pool<UploadCommand> uploads = new Pool<>();

  /**
   * The GL buffer handle (0 until the render thread creates it).
   */
  int handle = 0;
  private int capacity = 0;

  /**
   * Creates a new OpenGL buffer.
   *
   * <p>The GL handle is allocated asynchronously on the render thread.
   *
   * @param ctx  the GL context that owns this buffer
   * @param desc the buffer type, frequency, and initial-size hint
   */
  OpenGLBufferObject(OpenGLDevice ctx, BufferObjectDesc desc) {
    this.ctx = ctx;
    this.desc = desc;
    target = OpenGLUtils.bufferTarget(desc.type());
    boolean gpuWrite = (desc.usage() & BufferUsage.GPU_WRITE) != 0;
    hint = OpenGLUtils.bufferUsage(desc.frequency(), gpuWrite);

    ctx.submit(() -> handle = glGenBuffers());
  }

  @Override
  public BufferObjectDesc desc() {
    return desc;
  }

  @Override
  public int capacity() {
    return capacity;
  }

  @Override
  public boolean canExpand() {
    return true;
  }

  @Override
  public void allocate(int cap, byte @Nullable [] data) {
    ctx.submit(() -> {
      OpenGLCache cache = ctx.cache;
      cache.bindBuffer(target, handle);
      if (data != null && data.length == cap) {
        ByteBuffer bb = DirectBufferPool.acquire(cap);
        try {
          bb.put(data).flip();
          glBufferData(target, bb, hint);
        } finally {
          DirectBufferPool.release(bb);
        }
      } else {
        glBufferData(target, cap, hint);
      }
      capacity = cap;
    });
  }

  @Override
  public void submit(ByteBuffer memory, int offset) {
    /*
     * Copy first since the memory is volatile.
     * Users may pollute the memory after submission.
     *
     * P.S. This might influence the performance.
     * However, this is essential, if we want a pure asynchronous submission.
     */
    int size = memory.remaining();
    if (size == 0) {
      return;
    }
    ByteBuffer bb = DirectBufferPool.acquire(size);
    bb.put(memory).flip();

    UploadCommand command = uploads.poll();
    if (command == null) {
      command = new UploadCommand(this);
    }
    command.bytes = bb;
    command.offset = offset;
    ctx.submit(command);
  }

  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        // drop VAOs referencing this buffer before the GL handle is freed,
        // otherwise the handle could be recycled under a dangling VAO
        ctx.vaos.invalidateBuffer(handle);
        glDeleteBuffers(handle);
        handle = 0;
      }
    });
  }

  @Override
  public int handle(int slot) {
    return slot == 0 ? handle : target;
  }

  private static final class UploadCommand implements Runnable {
    private final OpenGLBufferObject buffer;
    private ByteBuffer bytes = EMPTY_UPLOAD;
    private int offset;

    private UploadCommand(OpenGLBufferObject buffer) {
      this.buffer = buffer;
    }

    @Override
    public void run() {
      try {
        buffer.ctx.cache.bindBuffer(buffer.target, buffer.handle);
        int needed = offset + bytes.remaining();
        if (needed > buffer.capacity) {
          buffer.capacity = Math.max(needed, buffer.capacity * 2);
          glBufferData(buffer.target, buffer.capacity, buffer.hint);
        } else if (buffer.desc.frequency() == BufferFrequency.STREAM && offset == 0) {
          // Growth already orphans storage; do not allocate twice.
          glBufferData(buffer.target, buffer.capacity, buffer.hint);
        }
        glBufferSubData(buffer.target, offset, bytes);
      } finally {
        DirectBufferPool.release(bytes);
        bytes = EMPTY_UPLOAD;
        offset = 0;
        buffer.uploads.release(this);
      }
    }
  }
}
