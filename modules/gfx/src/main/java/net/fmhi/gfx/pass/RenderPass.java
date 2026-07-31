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

package net.fmhi.gfx.pass;

import net.fmhi.math.Color;
import org.jspecify.annotations.Nullable;

/**
 * Creates a new {@code RenderPass} describing the render target and which attachments to clear.
 *
 * <p>The {@link #clearMask} bitmask selects which attachments are cleared.
 * Convenience factory {@link #of(RenderTarget, Color)} clears all three.
 *
 * @param target       the render target to bind for this pass, or {@code null} to use the swapchain
 * @param clearColor   the clear color for the color attachment
 * @param clearDepth   the clear depth value (0.0–1.0)
 * @param clearStencil the clear stencil value
 * @param clearMask    bitmask of {@link #CLEAR_COLOR}, {@link #CLEAR_DEPTH}, {@link #CLEAR_STENCIL}
 */
public record RenderPass(@Nullable RenderTarget target, Color clearColor, double clearDepth, int clearStencil,
                         int clearMask) {
  /** Clear the color attachment. */
  public static final int CLEAR_COLOR = 1;
  /** Clear the depth attachment. */
  public static final int CLEAR_DEPTH = 2;
  /** Clear the stencil attachment. */
  public static final int CLEAR_STENCIL = 4;

  /** Clear color + depth only (no stencil). Depth = 1.0, stencil = 0. Binds the swapchain. */
  public static final RenderPass DEFAULT = new Builder().build();
  /** Clear color + depth only (no stencil). Depth = 1.0, stencil = 0. Binds the swapchain. */
  public static final RenderPass NOT_CLEAR = new Builder().clearMask(0).build();

  /**
   * Clears all three attachments on the given target. Depth = 1.0, stencil = 0.
   *
   * @param target the render target to bind, or {@code null} for the swapchain
   * @param color  the color for the color attachment
   * @return a descriptor with all clear flags set
   */
  public static RenderPass of(@Nullable RenderTarget target, Color color) {
    Builder b = new Builder();
    b.target = target;
    b.clearColor = color;
    b.clearMask = CLEAR_COLOR | CLEAR_DEPTH | CLEAR_STENCIL;
    return b.build();
  }

  /**
   * Clears all three attachments on the swapchain. Depth = 1.0, stencil = 0.
   *
   * @param color the color for the color attachment
   * @return a descriptor with all clear flags set
   */
  public static RenderPass of(Color color) {
    return of(null, color);
  }

  /**
   * Builder for {@link RenderPass} with sensible defaults — clear color and depth, no stencil, swapchain target.
   */
  public static final class Builder {
    public @Nullable RenderTarget target = null;
    public Color clearColor = Color.BLACK;
    public double clearDepth = 1.0;
    public int clearStencil = 0;
    public int clearMask = CLEAR_COLOR | CLEAR_DEPTH;

    /**
     * Sets the render target to bind for this pass.
     *
     * @param v the render target, or {@code null} to use the swapchain
     * @return this builder
     */
    public Builder target(@Nullable RenderTarget v) {
      target = v;
      return this;
    }

    /**
     * Sets the clear color for the color attachment.
     *
     * @param v the clear color
     * @return this builder
     */
    public Builder clearColor(Color v) {
      clearColor = v;
      return this;
    }

    /**
     * Sets the clear depth value.
     *
     * @param v the clear depth value (0.0–1.0)
     * @return this builder
     */
    public Builder clearDepth(double v) {
      clearDepth = v;
      return this;
    }

    /**
     * Sets the clear stencil value.
     *
     * @param v the clear stencil value
     * @return this builder
     */
    public Builder clearStencil(int v) {
      clearStencil = v;
      return this;
    }

    /**
     * Sets the bitmask of attachments to clear.
     *
     * @param v bitmask of {@link #CLEAR_COLOR}, {@link #CLEAR_DEPTH}, {@link #CLEAR_STENCIL}
     * @return this builder
     */
    public Builder clearMask(int v) {
      clearMask = v;
      return this;
    }

    /**
     * Builds the render pass descriptor.
     *
     * @return a new {@link RenderPass} with the current builder values
     */
    public RenderPass build() {
      return new RenderPass(target, clearColor, clearDepth, clearStencil, clearMask);
    }
  }
}
