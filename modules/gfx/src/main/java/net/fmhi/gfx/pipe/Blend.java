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

package net.fmhi.gfx.pipe;

import net.fmhi.math.Color;

/**
 * Creates a new {@code Blend} describing the blend state for a render pipeline.
 *
 * <p>Blending combines the output of the fragment shader (the <em>source</em>)
 * with the value already in the framebuffer (the <em>destination</em>). A {@code Blend} specifies the factors,
 * functions, and constant color used in that combination.
 *
 * <p>The RGB and alpha channels can use separate source/destination factors
 * and blend functions, enabling effects like:
 * <ul>
 *   <li>Alpha blending for transparency ({@link #ALPHA_MIX})
 *   <li>Additive blending for glows and particles ({@link #ADDITIVE})
 *   <li>Disabled blending for opaque geometry ({@link #DISABLED})
 * </ul>
 *
 * <p>Three presets are provided as constants; use the canonical constructor
 * for custom configurations.
 *
 * @param srcColor  source factor for the RGB channels
 * @param dstColor  destination factor for the RGB channels
 * @param colorFunc blend function applied to the RGB result
 * @param srcAlpha  source factor for the alpha channel
 * @param dstAlpha  destination factor for the alpha channel
 * @param alphaFunc blend function applied to the alpha result
 * @param enable    {@code true} to enable blending
 * @param constant  constant blend color (used with {@link BlendFactor#CONSTANT_COLOR} etc.)
 */
public record Blend(boolean enable, BlendFactor srcColor, BlendFactor dstColor, BlendFunc colorFunc,
                    BlendFactor srcAlpha, BlendFactor dstAlpha, BlendFunc alphaFunc, Color constant) {
  /** Blending disabled: source overwrites destination. */
  public static final Blend DISABLED = new Blend(false, BlendFactor.ONE, BlendFactor.ZERO, BlendFunc.ADD,
      BlendFactor.ONE, BlendFactor.ZERO, BlendFunc.ADD, Color.EMPTY);

  /** Standard alpha blending: {@code src.a * src + (1-src.a) * dst}. */
  public static final Blend ALPHA_MIX = new Blend(true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
      BlendFunc.ADD, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFunc.ADD, Color.EMPTY);

  /**
   * Additive blending: {@code src.a * src + dst} (for glows, particles).
   */
  public static final Blend ADDITIVE = new Blend(true, BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFunc.ADD,
      BlendFactor.ONE, BlendFactor.ONE, BlendFunc.ADD, Color.EMPTY);
}
