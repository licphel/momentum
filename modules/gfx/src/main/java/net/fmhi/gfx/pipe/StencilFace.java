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
 * Creates a new {@code StencilFace} with the specified stencil operation and comparison parameters for a single face.
 *
 * @param failOp      action when the stencil test fails
 * @param passOp      action when both stencil and depth tests pass
 * @param depthFailOp action when the stencil test passes but depth fails
 * @param compareOp   comparison function for the stencil test
 * @param compareMask bitmask ANDed with the reference and stored values before comparison
 * @param writeMask   bitmask ANDed with the value before writing to the stencil buffer
 * @param reference   reference value compared against the stored stencil value
 */
public record StencilFace(StencilFunc failOp, StencilFunc passOp, StencilFunc depthFailOp, CompareOp compareOp,
                          int compareMask, int writeMask, int reference) {
  /** Stencil operations for a single face disabled. */
  public static final StencilFace DISABLED = new StencilFace(StencilFunc.KEEP, StencilFunc.KEEP, StencilFunc.KEEP,
      CompareOp.ALWAYS, 0xFF, 0xFF, 0);
}
