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

package net.fmhi.gfx.sprite;

import net.fmhi.gfx.brush.VertexBuilder2D;
import net.fmhi.gfx.texture.Drawable2D;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.Box2D;

/**
 * A classic 9-patch image that scales to fill an arbitrary destination rectangle without distorting the borders.
 *
 * <p>The source texture is divided into a 3×3 grid. The four corners are drawn at their original
 * size, the four edges stretch or repeat along their axis, and the center fills the remaining area. This enables
 * resizable UI elements like panels, buttons, and borders from a single texture.
 *
 * <p>When {@link #fit} is {@code true}, edge and center tiles may shrink or expand slightly so
 * the drawn result exactly matches the destination size. Keep {@code fit} {@code false} when tiles are translucent and
 * overlap would be visible.
 *
 * @see Drawable2D
 * @see VertexBuilder2D#draw(Drawable2D, float, float, float, float)
 */
public final class NinePatches implements Drawable2D {
  private static final float P13 = 1.0F / 3.0F;
  private static final float P23 = 2.0F / 3.0F;

  private final TexturePart top;
  private final TexturePart central;
  private final TexturePart left;
  private final TexturePart topLeft;
  private final TexturePart bottomLeft;
  private final TexturePart right;
  private final TexturePart topRight;
  private final TexturePart bottomRight;
  private final TexturePart bottom;
  private final float scale;
  private final float th;
  private final float tw;

  /** Whether tiles may overlap to fit the destination exactly. Keep {@code false} for translucent tiles. */
  public boolean fit;

  /**
   * Creates a 9-patch from a texture with the given scale.
   *
   * @param tex   the source texture
   * @param scale the tile scale factor
   */
  public NinePatches(Texture tex, float scale) {
    this(new TexturePart(tex), scale);
  }

  /**
   * Creates a 9-patch from a texture with a scale of {@code 1.0}.
   *
   * @param tex the source texture
   */
  public NinePatches(Texture tex) {
    this(tex, 1.0F);
  }

  /**
   * Creates a 9-patch from a texture part with the given scale.
   *
   * @param texPart the source texture part
   * @param scale   the tile scale factor
   */
  public NinePatches(TexturePart texPart, float scale) {
    this.scale = scale;

    tw = P13 * texPart.width();
    th = P13 * texPart.height();

    bottomLeft = slice(texPart, 0, P23, P13, P13);
    bottom = slice(texPart, P13, P23, P13, P13);
    bottomRight = slice(texPart, P23, P23, P13, P13);
    left = slice(texPart, 0, P13, P13, P13);
    central = slice(texPart, P13, P13, P13, P13);
    right = slice(texPart, P23, P13, P13, P13);
    topLeft = slice(texPart, 0, 0, P13, P13);
    top = slice(texPart, P13, 0, P13, P13);
    topRight = slice(texPart, P23, 0, P13, P13);
  }

  /**
   * Creates a 9-patch from a texture part with a scale of {@code 1.0}.
   *
   * @param texPart the source texture part
   */
  public NinePatches(TexturePart texPart) {
    this(texPart, 1.0F);
  }

  private static TexturePart slice(TexturePart texPart, float u, float v,
                                   float w, float h) {
    float w0 = texPart.width();
    float h0 = texPart.height();
    return new TexturePart(texPart, Box2D.create(w0 * u, h0 * v, w0 * w, h0 * h));
  }

  /**
   * Draws the 9-patch into the given destination rectangle.
   *
   * <p>The source region parameters {@code (u, v, uw, vh)} are ignored — this drawable uses
   * its own internal grid derived from the source texture.
   *
   * @param g the g to draw with
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the destination width in world units
   * @param h the destination height in world units
   */
  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h) {
    int nw = cntX(w);
    int nh = cntY(h);

    float atw = tw * scale;
    float ath = th * scale;

    if (fit) {
      float lcw = w - atw * (nw - 2);
      float lch = h - ath * (nh - 2);

      float rx = x + w - atw;
      float by = y + h - ath;

      // Central
      for (int j = 1; j < nh - 1; j++) {
        for (int i = 1; i < nw - 1; i++) {
          float drawX = x + i * atw;
          float drawY = y + j * ath;
          float drawW = i == nw - 2 ? lcw : atw;
          float drawH = j == nh - 2 ? lch : ath;
          g.drawTexture(central, drawX, drawY, drawW, drawH);
        }
      }

      // Edges
      for (int j = 1; j < nh - 1; j++) {
        float drawY = y + j * ath;
        float drawH = j == nh - 2 ? lch : ath;
        g.drawTexture(left, x, drawY, atw, drawH);
        g.drawTexture(right, rx, drawY, atw, drawH);
      }

      for (int i = 1; i < nw - 1; i++) {
        float drawX = x + i * atw;
        float drawW = i == nw - 2 ? lcw : atw;
        g.drawTexture(top, drawX, y, drawW, ath);
        g.drawTexture(bottom, drawX, by, drawW, ath);
      }

      // Corners
      g.drawTexture(topLeft, x, y, atw, ath);
      g.drawTexture(topRight, rx, y, atw, ath);
      g.drawTexture(bottomLeft, x, by, atw, ath);
      g.drawTexture(bottomRight, rx, by, atw, ath);
    } else {
      float rx = x + atw * (nw - 1);
      float by = y + ath * (nh - 1);

      // Central
      for (int j = 1; j < nh - 1; j++) {
        for (int i = 1; i < nw - 1; i++) {
          float drawX = x + i * atw;
          float drawY = y + j * ath;
          g.drawTexture(central, drawX, drawY, atw, ath);
        }
      }

      // Edges
      for (int j = 1; j < nh - 1; j++) {
        float drawY = y + j * ath;
        g.drawTexture(left, x, drawY, atw, ath);
        g.drawTexture(right, rx, drawY, atw, ath);
      }

      for (int i = 1; i < nw - 1; i++) {
        float drawX = x + i * atw;
        g.drawTexture(top, drawX, y, atw, ath);
        g.drawTexture(bottom, drawX, by, atw, ath);
      }

      // Corners
      g.drawTexture(topLeft, x, y, atw, ath);
      g.drawTexture(topRight, rx, y, atw, ath);
      g.drawTexture(bottomLeft, x, by, atw, ath);
      g.drawTexture(bottomRight, rx, by, atw, ath);
    }
  }

  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    draw(g, x, y, w, h);
  }

  private int cntX(float mw) {
    return Math.max(2, (int) Math.ceil(mw / tw / scale));
  }

  private int cntY(float mh) {
    return Math.max(2, (int) Math.ceil(mh / th / scale));
  }

  @SuppressWarnings("unused")
  private float tileWidth() {
    return tw * scale;
  }

  @SuppressWarnings("unused")
  private float tileHeight() {
    return th * scale;
  }
}
