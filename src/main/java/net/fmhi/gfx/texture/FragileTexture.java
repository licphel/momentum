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

package net.fmhi.gfx.texture;

import net.fmhi.gfx.mesh.dim2.VertexBuilder2D;
import net.fmhi.gfx.sprite.Drawable2D;
import org.jspecify.annotations.Nullable;

/**
 * A potentially unstable texture reference whose backing {@link Texture} may change at runtime.
 *
 * <p>Call {@link #pin()} immediately before use to obtain the current backing texture. Implementors
 * that wrap a mutable reference (e.g. a growing glyph atlas) may return a different {@link Texture} on each call;
 * stable implementors (e.g. {@link Texture} itself) always return {@code this}.
 *
 * <p><b>Thread safety:</b> pin() must be called on the render thread.
 */
public interface FragileTexture extends Drawable2D {
  /**
   * Returns the current backing texture.
   *
   * @return the current {@link Texture}
   */
  @Nullable Texture pin();

  /**
   * A sensible default implementation of draw.
   *
   * @param g the vertex builder to draw with
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  @Override
  default void draw(VertexBuilder2D g, float x, float y, float w, float h) {
    Texture texture = pin();

    if (texture != null) {
      g.draw(texture, x, y, w, h, 0, 0, texture.width(), texture.height());
    }
  }

  /**
   * A sensible default implementation of draw.
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
  @Override
  default void draw(VertexBuilder2D g, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    g.draw(this, x, y, w, h, u, v, uw, vh);
  }
}
