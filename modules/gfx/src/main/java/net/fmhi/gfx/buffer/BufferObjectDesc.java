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

/**
 * Creates a new {@code BufferObjectDesc} describing a GPU buffer to be allocated.
 *
 * <p>Specifies the buffer's type (vertex, index, or uniform) and its expected
 * update frequency, which the GPU driver uses as an allocation hint.
 *
 * @param frequency how often the buffer contents are updated
 * @param type      the buffer's role in the pipeline
 * @param usage     access-pattern hints (see {@link BufferUsage})
 * @see BufferFrequency
 * @see BufferType
 * @see BufferUsage
 */
public record BufferObjectDesc(BufferFrequency frequency, BufferType type, int usage) {
  /**
   * Creates a descriptor with default access pattern (GPU reads, CPU writes).
   *
   * @param frequency how often the buffer contents are updated
   * @param type      the buffer's role in the pipeline
   */
  public BufferObjectDesc(BufferFrequency frequency, BufferType type) {
    this(frequency, type, BufferUsage.GPU_READ_CPU_WRITE);
  }

  /**
   * Creates a descriptor for a vertex buffer with the given update frequency.
   *
   * @param freq the expected update frequency
   * @return a vertex-buffer descriptor
   */
  public static BufferObjectDesc vertex(BufferFrequency freq) {
    return new BufferObjectDesc(freq, BufferType.VERTEX);
  }

  /**
   * Creates a descriptor for an index buffer with the given update frequency.
   *
   * @param freq the expected update frequency
   * @return an index-buffer descriptor
   */
  public static BufferObjectDesc index(BufferFrequency freq) {
    return new BufferObjectDesc(freq, BufferType.INDEX);
  }

  /**
   * Creates a descriptor for a uniform buffer with dynamic frequency.
   *
   * <p>Uniform buffers default to {@link BufferFrequency#DYNAMIC} since they
   * are typically updated every frame.
   *
   * @return a uniform-buffer descriptor
   */
  public static BufferObjectDesc uniform() {
    return new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM);
  }
}
