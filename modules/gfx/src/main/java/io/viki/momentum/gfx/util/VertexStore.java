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

package io.viki.momentum.gfx.util;

import io.viki.momentum.codec.streaming.CursorBuffer;

/**
 * Staging area for vertex and index data being assembled for rendering.
 */
public interface VertexStore {
  /**
   * Returns the buffer holding interleaved vertex data.
   *
   * @return the vertex buffer
   */
  CursorBuffer vertices();

  /**
   * Returns the buffer holding index data.
   *
   * @return the index buffer
   */
  CursorBuffer indices();

  /**
   * Returns the number of vertices written so far.
   *
   * @return the vertex count
   */
  int vertexCount();

  /**
   * Returns the number of indices written so far.
   *
   * @return the index count
   */
  int indexCount();

  /**
   * Records the given number of additional vertices.
   *
   * @param count the number of vertices written
   */
  void addVertex(int count);

  /**
   * Records the given number of additional indices.
   *
   * @param count the number of indices written
   */
  void addIndex(int count);

  /**
   * Resets the recorded counts and clears both buffers.
   */
  void clear();

  /**
   * Returns the recorded vertex data as a byte array; whether the array is a copy or
   * live backing storage depends on the implementation.
   *
   * @return the vertex data bytes
   */
  byte[] recordVertices();

  /**
   * Returns the recorded index data as a byte array; whether the array is a copy or
   * live backing storage depends on the implementation.
   *
   * @return the index data bytes
   */
  byte[] recordIndices();
}
