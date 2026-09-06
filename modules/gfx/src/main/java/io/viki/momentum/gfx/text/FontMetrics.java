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

package io.viki.momentum.gfx.text;

/**
 * Scaled metrics of a {@link Font} used for text layout calculations.
 *
 * <p>All values are in pixels at the font's current resolution. Positive values
 * extend upward from the baseline; descender values are typically negative.
 *
 * @param ascender           the maximum distance above the baseline
 * @param descender          the maximum distance below the baseline
 * @param lineHeight         the recommended distance between consecutive baselines
 * @param underlinePos       the position of the underline relative to the baseline
 * @param underlineThickness the thickness of the underline stroke
 */
public record FontMetrics(float ascender, float descender, float lineHeight, float underlinePos,
                          float underlineThickness) {
}
