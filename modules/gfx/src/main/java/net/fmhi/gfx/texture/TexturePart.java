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

import net.fmhi.gfx.brush.VertexBuilder2D;
import net.fmhi.math.Box2D;
import org.jspecify.annotations.Nullable;

/**
 * Identifies a sub-region of a texture, defined by a {@link FragileTexture} reference and a bounding rectangle in texel
 * coordinates.
 *
 * <p>The backing texture is held via {@link FragileTexture} so that atlas-backed parts remain
 * valid even when the underlying texture is swapped (e.g. when a glyph atlas grows). Call {@link #src()} to pin and
 * obtain the current backing texture at draw time.
 *
 * <p>Texture parts are immutable. Create one from a full texture or offset from a parent part to
 * describe a relative sub-region.
 *
 * @param fragile the fragile reference to the source texture
 * @param region  the region within the texture in texel coordinates
 */
public record TexturePart(FragileTexture fragile, Box2D region) implements Drawable2D {
  /**
   * Creates a {@code TexturePart} covering the entire texture.
   *
   * @param tex the source texture
   */
  public TexturePart(Texture tex) {
    this(tex, Box2D.create(0, 0, tex.width(), tex.height()));
  }

  /**
   * Creates a {@code TexturePart} offset from a parent part's region origin.
   *
   * @param parent         the parent texture part
   * @param relativeRegion the region relative to the parent's origin, in texels
   */
  public TexturePart(TexturePart parent, Box2D relativeRegion) {
    this(parent.fragile, relativeRegion.translate(parent.region.minX(), parent.region.minY()));
  }

  /**
   * Pins the fragile reference and returns the current backing texture.
   *
   * <p>Call this immediately before use — the backing texture may have changed since last call.
   *
   * @return the current backing {@link Texture}
   */
  public @Nullable Texture src() {
    return fragile.pin();
  }

  /**
   * Returns the U coordinate of the region origin in texels.
   *
   * @return the U coordinate
   */
  public float u() {
    return region.minX();
  }

  /**
   * Returns the V coordinate of the region origin in texels.
   *
   * @return the V coordinate
   */
  public float v() {
    return region.minY();
  }

  /**
   * Returns the width of the region in texels.
   *
   * @return the width of the region
   */
  public float width() {
    return region.width();
  }

  /**
   * Returns the height of the region in texels.
   *
   * @return the height of the region
   */
  public float height() {
    return region.height();
  }

  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h) {
    g.drawTexture(this, x, y, w, h);
  }

  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    g.drawTexture(this, x, y, w, h, u, v, uw, vh);
  }
}
