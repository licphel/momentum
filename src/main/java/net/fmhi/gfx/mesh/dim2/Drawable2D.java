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

package net.fmhi.gfx.mesh.dim2;

/**
 * A drawable object that renders itself using a {@link VertexBuilder2D}.
 *
 * <p>This is a functional interface whose functional method is
 * {@link #draw(VertexBuilder2D, float, float, float, float, float, float, float, float)}.
 * Implementations define custom drawing logic and can be passed to the various
 * {@code VertexBuilder2D.draw(...)} overloads.
 *
 * @see VertexBuilder2D
 */
@FunctionalInterface
public interface Drawable2D {
  /**
   * Draws this object at the given position, size, and texture coordinate region.
   *
   * @param g  the vertex builder to draw with
   * @param x  the X position in world units
   * @param y  the Y position in world units
   * @param w  the width in world units
   * @param h  the height in world units
   * @param u  the U texture coordinate offset in texels
   * @param v  the V texture coordinate offset in texels
   * @param uw the U texture coordinate range in texels
   * @param vh the V texture coordinate range in texels
   */
  void draw(VertexBuilder2D g, float x, float y, float w, float h, float u, float v, float uw, float vh);
}
