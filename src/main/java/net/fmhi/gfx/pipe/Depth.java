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
 * Creates a new {@code Depth} describing depth-test and depth-write behavior.
 *
 * <p>Depth testing discards fragments that fail a comparison against the
 * existing depth-buffer value. Depth writes control whether passing fragments update the depth buffer.
 *
 * <p>Two presets are provided in addition to {@link #DISABLED}:
 * <ul>
 *   <li>{@link #LEQ} — standard less-than-or-equal test with writes enabled
 *   <li>{@link #GEQ} — greater-than-or-equal (for reversed-Z approaches)
 * </ul>
 *
 * @param depthTest    {@code true} to enable depth testing
 * @param depthWrite   {@code true} to allow writes to the depth buffer
 * @param depthCompare the comparison function used for the depth test
 */
public record Depth(boolean depthTest, boolean depthWrite, CompareOp depthCompare) {
  /** Depth testing and writing disabled. */
  public static final Depth DISABLED = new Depth(false, false, CompareOp.ALWAYS);
  /** Standard depth test: pass if the new depth ≤ the stored depth, writes enabled. */
  public static final Depth LEQ = new Depth(true, true, CompareOp.LESS_OR_EQUAL);
  /** Reversed depth test: pass if the new depth ≥ the stored depth (used with floating-point depth buffers). */
  public static final Depth GEQ = new Depth(true, true, CompareOp.GREATER_OR_EQUAL);
}
