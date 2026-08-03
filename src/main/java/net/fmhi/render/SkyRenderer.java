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

package net.fmhi.render;

import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.brush.tint.QuadGradient;
import net.fmhi.math.Color;
import net.fmhi.util.Util;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.CelestialUtil;

/**
 * Screen-space sky rendering: gradient background, twinkling stars,
 * drifting clouds and the sun/moon bodies, ported from Enchant's
 * {@code View/Ambient/Sky}.
 *
 * <p>The sky is drawn in screen coordinates (Y-up, {@code y = 0} at the
 * bottom), fixed to the viewport regardless of the world camera. Positions
 * are derived from deterministic hashes instead of persistent objects, so
 * the sky needs no tick state and never shimmers.
 */
public class SkyRenderer {
  /** Star cells across the screen at the reference zoom (≈ Enchant's 210 stars). */
  private static final float STAR_CELLS = 17.8F;
  /** Reference viewport width the size constants are tuned for. */
  private static final float REF_WIDTH = 1280F;
  /** Number of clouds on screen. */
  private static final int CLOUD_COUNT = 10;

  private final QuadGradient skyGradient = new QuadGradient();
  private final Color[] skyColors = new Color[4];

  /**
   * Draws the full sky into the current render pass, covering the whole
   * viewport.
   *
   * @param g      the graphics to draw with
   * @param level  the level whose day phase to use
   * @param w      the viewport width in pixels
   * @param h      the viewport height in pixels
   * @param worldY the world Y of the viewport center (for space factor)
   */
  public void render(BatchedGraphics2D g, Level level, float w, float h, int worldY) {
    float day = CelestialUtil.sin(level);
    float space = CelestialUtil.spaceFactor(level, worldY);
    float time = Util.frameTime();

    drawGradient(g, level, w, h, worldY);
    drawStars(g, w, h, day, space, time);
    drawClouds(g, level, w, h, worldY, time);
    drawBodies(g, level, w, h);
  }

  private void drawGradient(BatchedGraphics2D g, Level level, float w, float h, int worldY) {
    CelestialUtil.skyQuadColors(level, worldY, skyColors);
    skyGradient.setColors(skyColors[0], skyColors[1], skyColors[2], skyColors[3]);
    g.setGradient(skyGradient);
    g.drawRectangle(0F, 0F, w, h);
    g.setColor(Color.WHITE);
  }

  private void drawStars(BatchedGraphics2D g, float w, float h, float day, float space, float time) {
    float cell = w / STAR_CELLS;
    int cellsX = (int) Math.ceil(w / cell);
    int cellsY = (int) Math.ceil(h / cell);
    float size = (float) Math.sqrt(w * w + h * h) / 600F;
    for (int cy = 0; cy < cellsY; cy++) {
      for (int cx = 0; cx < cellsX; cx++) {
        float ox = rand01(hash(cx, cy, 0x1234567L));
        float oy = rand01(hash(cx, cy, 0x7654321L));
        float sx = (cx + ox) * cell;
        float sy = (cy + oy) * cell;
        // visible at night, fading with daylight; slow twinkle per star
        float opa = Math.clamp(rand01(hash(cx, cy, 0x5DEECE66DL)) - day + space, 0F, 1F)
            * (float) (Math.sin(time + ox * 100F) * 0.5 + 0.25);
        if (opa <= 0.01F) continue;
        g.setColor(new Color(1F, 1F, 1F, Math.clamp(opa, 0F, 1F)));
        g.drawRectangle(sx, sy, size, size);
      }
    }
  }

  private void drawClouds(BatchedGraphics2D g, Level level, float w, float h, int worldY, float time) {
    float spf = CelestialUtil.spaceFactor(level, worldY);
    Color base = CelestialUtil.backgroundLight(level, worldY);
    Color cloudColor = new Color(
        Math.min(1F, base.red() * 1.2F),
        Math.min(1F, base.green() * 1.2F),
        Math.min(1F, base.blue() * 1.2F),
        base.alpha() * (1F - spf));
    float diag = (float) Math.sqrt(w * w + h * h);
    for (int i = 0; i < CLOUD_COUNT; i++) {
      // wide flat ellipse in the upper half, drifting across the screen
      float cw = (1F + 3F * rand01(hash(i, 0xBEEFCAFEL))) * diag / 24F;
      float ch = cw * 60F / 275F;
      float speed = (0.1F + 0.4F * rand01(hash(i, 0xDEADBEEFL))) * 60F;
      float x0 = rand01(hash(i, 0xCAFEBABEL)) * (w + cw);
      float sx = (time * speed + x0) % (w + cw) - cw;
      float sy = h / 2F + rand01(hash(i, 0xFEEDFACEL)) * h / 2F;
      g.setColor(cloudColor);
      g.drawOval(sx, sy, cw, ch);
    }
  }

  private void drawBodies(BatchedGraphics2D g, Level level, float w, float h) {
    float rad = CelestialUtil.bodyRadians(level);
    // the bodies orbit a point 1/8 of the viewport height above the bottom
    // edge; the sun peaks near the top at noon, the moon mirrors it
    float cty = h / 8F;
    float hx = (float) Math.cos(rad) * h * 1.15F;
    float hy = (float) Math.sin(rad) * h * 0.75F;
    float sunR = 16F * w / REF_WIDTH;
    g.setColor(new Color(1F, 0.95F, 0.85F));
    g.drawOval(w / 2F + hx - sunR, cty + hy - sunR, sunR * 2F, sunR * 2F);
    float moonR = sunR / 2F;
    g.setColor(new Color(0.85F, 0.9F, 1F, 0.75F));
    g.drawOval(w / 2F - hx - moonR, cty - hy - moonR, moonR * 2F, moonR * 2F);
  }

  private static long hash(int a, long salt) {
    return hash(a, 0, salt);
  }

  private static long hash(int a, int b, long salt) {
    long h = (a * 73856093L) ^ (b * 19349663L) ^ salt;
    h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
    return h ^ (h >>> 16);
  }

  private static float rand01(long h) {
    return (h & 0xFFFFFF) / (float) 0x1000000;
  }
}
