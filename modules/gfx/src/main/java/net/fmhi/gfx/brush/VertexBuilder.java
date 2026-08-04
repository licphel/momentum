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

package net.fmhi.gfx.brush;

import net.fmhi.codec.streaming.CursorBuffer;
import net.fmhi.gfx.brush.tint.Gradient;
import net.fmhi.gfx.brush.tint.SimpleGradient;
import net.fmhi.gfx.math.TransformHandler;
import net.fmhi.math.Color;
import net.fmhi.math.MatrixStack;

/**
 * Writes interleaved vertex data and quad indices into staging buffers.
 *
 * <p>Carries the transform matrix stack, gradient tint, and draw flags that apply to
 * subsequent vertex writes; every position passes through the current top of the transform
 * stack before being stored. Implementations provide the staging buffers and track the
 * vertex and index counts. Not thread-safe; each instance must be confined to a single thread.
 */
public class VertexBuilder {
  protected final float[] trCache = new float[3];
  protected final TransformHandler transformHandler;
  protected final MatrixStack matrixStack = new MatrixStack();
  protected Gradient gradient = new SimpleGradient(Color.WHITE.pack());
  protected int flags = 0;
  protected VertexStore data;

  /**
   * Creates a new {@code VertexBuilder} writing into the given staging area.
   *
   * @param data              the staging area receiving vertices and indices
   * @param transformHandler  the transform handler used for coordinate conversion
   */
  public VertexBuilder(VertexStore data, TransformHandler transformHandler) {
    this.data = data;
    this.transformHandler = transformHandler;
  }

  /**
   * Returns the transform matrix stack applied to subsequent vertex writes.
   *
   * @return the transform stack
   */
  public MatrixStack transform() {
    return matrixStack;
  }

  /**
   * Returns the current gradient tint.
   *
   * @return the current gradient
   */
  public Gradient gradient() {
    return gradient;
  }

  /**
   * Sets a uniform color as the gradient tint.
   *
   * @param color the color to apply to subsequent draws
   */
  public void setColor(Color color) {
    this.gradient = new SimpleGradient(color);
  }

  /**
   * Sets the gradient tint.
   *
   * @param gradient the gradient to apply to subsequent draws
   */
  public void setGradient(Gradient gradient) {
    this.gradient = gradient;
  }

  /**
   * Returns the current draw flags.
   *
   * @return the current {@link DrawingFlags} bitmask
   */
  public int flags() {
    return flags;
  }

  /**
   * Sets the draw flags applied to subsequent draws.
   *
   * @param flags the bitmask of {@link DrawingFlags}
   */
  public void setFlags(int flags) {
    this.flags = flags;
  }

  /**
   * Returns the staging area receiving vertex and index data.
   *
   * @return the vertex data
   */
  public VertexStore data() {
    return data;
  }

  /**
   * Writes a vertex with a position and a packed gradient color.
   *
   * <p>The position is transformed by the current top of the transform stack, and the
   * color is stored as four float16 values packed into a {@code long}.
   *
   * @param x           the X position
   * @param y           the Y position
   * @param z           the Z position
   * @param packedColor the packed gradient color
   */
  public void putPosColor(float x, float y, float z, long packedColor) {
    CursorBuffer buf = data.vertices();
    float[] arr = trCache;
    arr[0] = x;
    arr[1] = y;
    arr[2] = z;
    matrixStack.top().transformInplace(arr);
    buf.writeFloat(arr[0]);
    buf.writeFloat(arr[1]);
    buf.writeFloat(arr[2]);
    buf.writeLong(packedColor);
  }

  /**
   * Writes a vertex with a position, packed gradient color, and texture coordinates.
   *
   * <p>The position is transformed by the current top of the transform stack, and the
   * color is stored as four float16 values packed into a {@code long}.
   *
   * @param x           the X position
   * @param y           the Y position
   * @param z           the Z position
   * @param packedColor the packed gradient color
   * @param u           the texture U coordinate
   * @param v           the texture V coordinate
   */
  public void putPosColorUv(float x, float y, float z, long packedColor, float u, float v) {
    CursorBuffer buf = data.vertices();
    float[] arr = trCache;
    arr[0] = x;
    arr[1] = y;
    arr[2] = z;
    matrixStack.top().transformInplace(arr);
    buf.writeFloat(arr[0]);
    buf.writeFloat(arr[1]);
    buf.writeFloat(arr[2]);
    buf.writeLong(packedColor);
    buf.writeFloat(u);
    buf.writeFloat(v);
  }

  /**
   * Closes the current quad by writing six indices in counter-clockwise winding order.
   *
   * <p>The indices reference the four most recently written vertices, so exactly four
   * vertices must be written before calling this method.
   */
  public void endQuad() {
    CursorBuffer idx = data.indices();
    int baseVertex = data.vertexCount();
    idx.writeInt(baseVertex);
    idx.writeInt(baseVertex + 2);
    idx.writeInt(baseVertex + 1);
    idx.writeInt(baseVertex + 2);
    idx.writeInt(baseVertex);
    idx.writeInt(baseVertex + 3);
    data.addVertex(4);
    data.addIndex(6);
  }
}
