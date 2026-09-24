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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.math.shape.Rectangle;

import java.util.List;

/**
 * Defines how a container positions its child elements inside a local area.
 *
 * <p>Layouts mutate child bounds during a container's layout pass and do not own the child list.
 * Implementations should preserve the supplied gap where space permits and may clamp geometry
 * when the available area is smaller than the requested content.
 */
@FunctionalInterface
public interface Layout {
  /**
   * Arranges the supplied children within the given area.
   *
   * @param area the local region available to the children
   * @param children the children whose bounds may be updated
   * @param gap the spacing to preserve between adjacent children
   */
  void arrange(Rectangle area, List<Element> children, float gap);
}
