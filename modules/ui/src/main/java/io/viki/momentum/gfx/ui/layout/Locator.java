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

package io.viki.momentum.gfx.ui.layout;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.math.Vector2;

/**
 * Recomputes an element's bounds when its parent layout size changes.
 *
 * <p>The callback receives logical coordinates in the parent-local space. Implementations may
 * update the supplied element's bounds and may derive placement from the previous and current
 * parent sizes.
 */
@FunctionalInterface
public interface Locator {
  /**
   * Updates an element in response to a parent-size change.
   *
   * @param element element whose bounds should be recomputed
   * @param oldSize previous parent size in logical coordinates
   * @param newSize current parent size in logical coordinates
   */
  void locate(Element element, Vector2 oldSize, Vector2 newSize);
}
