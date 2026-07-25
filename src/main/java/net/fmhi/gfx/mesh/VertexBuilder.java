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

package net.fmhi.gfx.mesh;

import net.fmhi.codec.streaming.Buf;
import net.fmhi.math.Color;
import net.fmhi.math.MatrixStack;

/**
 * Writes interleaved vertex data and quad indices into staging buffers.
 *
 * <p>A {@code VertexBuilder} provides low-level helpers for building vertex and index buffer
 * content. It carries a transform matrix stack, a color tint, and draw flags that affect
 * subsequent vertex writes. Implementations manage the staging buffers and track vertex and
 * index counts.
 */
public interface VertexBuilder {
  /**
   * Returns the transform matrix stack applied to all draw calls.
   *
   * @return the transform matrix stack
   */
  MatrixStack transform();

  /**
   * Returns the current rendering color tint.
   *
   * @return the color tint
   */
  Color color();

  /**
   * Sets the rendering color tint.
   *
   * @param color the color tint
   */
  void setColor(Color color);

  /**
   * Returns the current draw flags bitmask.
   *
   * @return the flags bitmask; see {@link DrawingFlags}
   */
  int flags();

  /**
   * Sets the draw flags bitmask.
   *
   * @param flags the flags bitmask; see {@link DrawingFlags}
   */
  void setFlags(int flags);

  /**
   * Returns the staging vertex buffer into which {@code put*} methods write.
   *
   * @return the staging vertex buffer
   */
  Buf vertices();

  /**
   * Returns the staging index buffer into which {@link #putQuadIndices} writes.
   *
   * @return the staging index buffer
   */
  Buf indices();

  /**
   * Writes a vertex with three float32 position components and a half4 packed color.
   *
   * @param x           the X position
   * @param y           the Y position
   * @param z           the Z position
   * @param packedColor the RGBA color as four float16 values packed into a {@code long}
   */
  default void putPosColor(float x, float y, float z, long packedColor) {
    Buf buf = vertices();

    buf.writeFloat(x);
    buf.writeFloat(y);
    buf.writeFloat(z);
    buf.writeLong(packedColor);
  }

  /**
   * Writes a vertex with position, packed color, and two float32 texture coordinates.
   *
   * @param x           the X position
   * @param y           the Y position
   * @param z           the Z position
   * @param packedColor the RGBA color as four float16 values packed into a {@code long}
   * @param u           the texture coordinate U
   * @param v           the texture coordinate V
   */
  default void putPosColorUv(float x, float y, float z, long packedColor, float u, float v) {
    Buf buf = vertices();

    buf.writeFloat(x);
    buf.writeFloat(y);
    buf.writeFloat(z);
    buf.writeLong(packedColor);
    buf.writeFloat(u);
    buf.writeFloat(v);
  }

  /**
   * Writes six indices forming two triangles in counter-clockwise winding, starting at
   * {@code baseVertex}.
   *
   * @param baseVertex the index of the first vertex of the quad
   */
  default void putQuadIndices(int baseVertex) {
    Buf idx = indices();
    idx.writeInt(baseVertex);
    idx.writeInt(baseVertex + 2);
    idx.writeInt(baseVertex + 1);
    idx.writeInt(baseVertex + 2);
    idx.writeInt(baseVertex);
    idx.writeInt(baseVertex + 3);
  }

  /**
   * Returns the current vertex count.
   *
   * @return the number of vertices written so far
   */
  int vertexCount();

  /**
   * Returns the current index count.
   *
   * @return the number of indices written so far
   */
  int indexCount();

  /**
   * Increments the vertex count by the given amount.
   *
   * @param count the number of vertices to add
   */
  void addVertex(int count);

  /**
   * Increments the index count by the given amount.
   *
   * @param count the number of indices to add
   */
  void addIndex(int count);
}
