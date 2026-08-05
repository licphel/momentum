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

/**
 * Creates a new {@code Stencil} describing the stencil test and per-face stencil operations.
 *
 * <p>The stencil test discards fragments based on a comparison between a
 * reference value and the stencil buffer. Separate front-face and back-face configurations enable effects like
 * two-sided stencil volumes.
 *
 * <p>The {@link #DISABLED} preset disables stencil testing entirely.
 *
 * @param front the stencil configuration for front-facing fragments
 * @param back  the stencil configuration for back-facing fragments
 * @see StencilFace
 */
public record Stencil(StencilFace front, StencilFace back) {
  /** Stencil testing disabled for both faces. */
  public static final Stencil DISABLED = new Stencil(StencilFace.DISABLED, StencilFace.DISABLED);
}
