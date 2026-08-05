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

package net.fmhi.gfx.buffer;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.cmd.Encoder;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * A GPU buffer holding vertex, index, or uniform data.
 *
 * <p>{@code BufferObject} represents a linear allocation in GPU memory that
 * the CPU can write to and the GPU can read from during draw calls or compute dispatches. The buffer's type —
 * {@code VERTEX}, {@code INDEX}, or {@code UNIFORM} — is declared once at creation time via {@link BufferObjectDesc}.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Create with {@link Device#getBuffer device.createBuffer(desc)}.
 *   <li>Allocate storage with {@link #allocate(int, byte[])} or upload immediately
 *       with {@link #submit(byte[], int, int)} (which auto-allocates if needed).
 *   <li>Bind the buffer to an {@link Encoder} for drawing.
 *   <li>Update contents each frame with {@link #submit}.
 *   <li>Call {@link #close()} when the buffer is no longer needed.
 * </ol>
 *
 * <p><b>Buffer orphaning:</b> for {@link BufferFrequency#STREAM} buffers,
 * calling {@link #submit} with {@code offset == 0} discards the previous
 * allocation (via {@code glBufferData}) before uploading, which avoids
 * GPU pipeline stalls at the cost of an extra allocation per frame.
 *
 * <p><b>Thread safety:</b> creation and mutation must happen on the render
 * thread (or be submitted to it via the backend queue). Reading
 * {@link #desc()}, {@link #capacity()}, and {@link #canExpand()} is safe
 * from any thread.
 *
 * @see BufferObjectDesc
 */
public interface BufferObject extends AutoCloseable {
  /**
   * Returns the immutable descriptor that was used to create this buffer.
   *
   * @return the buffer's type, frequency, and size hint
   */
  BufferObjectDesc desc();

  /**
   * Returns the current capacity of this buffer in bytes.
   *
   * <p>The capacity may grow beyond the initial allocation if
   * {@link #canExpand()} returns {@code true} and a {@link #submit} call overflows.
   *
   * @return current byte capacity
   */
  int capacity();

  /**
   * Returns whether this buffer automatically expands on {@link #submit}.
   *
   * <p>When {@code true}, an upload that would overflow the current capacity
   * triggers a reallocation before the data is written. The new capacity is at least double the previous size, or
   * exactly the required size if larger.
   *
   * @return {@code true} if the buffer grows automatically
   */
  boolean canExpand();

  /**
   * Allocates (or reallocates) GPU storage for this buffer.
   *
   * <p>Any previous contents are discarded. If {@code data} is non-null,
   * its length must equal {@code capacity} and the bytes are copied into the new allocation; otherwise the storage is
   * left uninitialized.
   *
   * <p>This is a potentially expensive operation — prefer {@link #submit}
   * for incremental updates.
   *
   * @param capacity new size in bytes
   * @param data     initial contents, or {@code null} to leave uninitialized
   */
  void allocate(int capacity, byte @Nullable [] data);

  /**
   * Uploads bytes into the buffer at the given offset.
   *
   * <p>If the upload overflows the current capacity and {@link #canExpand()}
   * returns {@code true}, the buffer is transparently reallocated.
   *
   * <p>For {@link BufferFrequency#STREAM} buffers, an upload at offset 0
   * orphans the previous allocation before writing, which avoids pipeline stalls.
   *
   * @param data   the bytes to upload.
   * @param offset byte offset within the buffer to start writing
   * @param size   bytes that will be uploaded
   */
  default void submit(byte[] data, int offset, int size) {
    submit(ByteBuffer.wrap(data).slice(0, size), offset);
  }

  /**
   * Uploads bytes into the buffer at the given offset.
   *
   * <p>If the upload overflows the current capacity and {@link #canExpand()}
   * returns {@code true}, the buffer is transparently reallocated.
   *
   * <p>For {@link BufferFrequency#STREAM} buffers, an upload at offset 0
   * orphans the previous allocation before writing, which avoids pipeline stalls.
   *
   * @param data   the bytes to upload.
   * @param offset byte offset within the buffer to start writing
   */
  default void submit(byte[] data, int offset) {
    submit(data, offset, data.length);
  }

  /**
   * Uploads bytes starting at offset 0.
   *
   * <p>Equivalent to {@link #submit(byte[], int, int) submit(data, 0)}.
   *
   * @param data the bytes to upload.
   */
  default void submit(byte[] data) {
    submit(data, 0, data.length);
  }

  /**
   * Uploads bytes into the buffer at the given offset.
   *
   * <p>If the upload overflows the current capacity and {@link #canExpand()}
   * returns {@code true}, the buffer is transparently reallocated.
   *
   * <p>For {@link BufferFrequency#STREAM} buffers, an upload at offset 0
   * orphans the previous allocation before writing, which avoids pipeline stalls.
   *
   * @param memory the bytes to upload.
   * @param offset byte offset within the buffer to start writing
   */
  void submit(ByteBuffer memory, int offset);

  /**
   * Uploads bytes starting at offset 0.
   *
   * <p>Equivalent to {@link #submit(byte[], int, int) submit(data, 0)}.
   *
   * @param memory the bytes to upload.
   */
  default void submit(ByteBuffer memory) {
    submit(memory, 0);
  }

  @Override
  void close();
}
