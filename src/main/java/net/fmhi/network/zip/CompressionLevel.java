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

package net.fmhi.network.zip;

/**
 * Compression level for {@link Compressor} operations.
 *
 * <p>Each level maps to a Deflate / Gzip numeric value:
 *
 * <table summary="Compression level to Deflate numeric value mapping">
 *   <caption>Level Mapping</caption>
 *   <tr><th>Level</th><th>Deflate / GZip</th></tr>
 *   <tr><td>{@link #NONE}</td><td>0 (no compression)</td></tr>
 *   <tr><td>{@link #FAST}</td><td>1 (best speed)</td></tr>
 *   <tr><td>{@link #DEFAULT}</td><td>-1 (system default)</td></tr>
 *   <tr><td>{@link #BEST}</td><td>9 (best compression)</td></tr>
 * </table>
 *
 * @see Compressor
 */
public enum CompressionLevel {
  /**
   * No compression — store only.
   *
   * <p>Data passes through without any compression applied. Useful when the
   * input is already compressed or incompressible (encrypted data, random bytes).
   */
  NONE,

  /**
   * Fastest compression, minimal CPU time at the cost of a lower ratio.
   *
   * <p>Use for real-time or high-throughput scenarios where speed matters more
   * than storage efficiency — live network streams, temporary caches, etc.
   */
  FAST,

  /**
   * Balanced between speed and compression ratio.
   *
   * <p>This is the recommended default for most use cases. It should be used
   * unless you have a measured reason to prefer {@link #FAST} or {@link #BEST}.
   */
  DEFAULT,

  /**
   * Maximum compression ratio at the cost of more CPU time and memory.
   *
   * <p>Use for archival, long-term storage, or one-time batch compression
   * where decompression speed matters but compression time does not.
   */
  BEST
}
