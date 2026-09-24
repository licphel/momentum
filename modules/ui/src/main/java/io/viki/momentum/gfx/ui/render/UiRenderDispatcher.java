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

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.Window;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Traverses the UI tree and dispatches rendering to each element's renderer.
 *
 * <p>Children are rendered in their insertion order, while parts are rendered in a second local
 * pass so popups and other overlays remain above sibling content. A layer boundary is rendered as
 * one unit, which keeps an overlay from an older window above a newer window.
 */
public final class UiRenderDispatcher implements AutoCloseable {
  /** Dispatcher used by elements that are drawn without an explicitly configured Canvas. */
  public static final UiRenderDispatcher INSTANCE = new UiRenderDispatcher();

  private final @Nullable BackdropBlurEffect backdropBlur;
  private final List<Rectangle> renderedWindows = new ArrayList<>();
  private final List<Rectangle> blurRegions = new ArrayList<>();

  /**
   * Creates a dispatcher without optional device-backed effects.
   *
   * <p>This constructor is suitable for ordinary UI rendering when backdrop blur resources are
   * not required.
   */
  public UiRenderDispatcher() {
    this(null);
  }

  private UiRenderDispatcher(@Nullable Device device) {
    backdropBlur = device == null ? null : new BackdropBlurEffect(device);
  }

  /**
   * Creates a dispatcher with the UI-owned GPU effects enabled for a device.
   *
   * <p>The returned dispatcher owns the optional backdrop effect and should be closed when it is
   * no longer used. A dispatcher is mutable only through its graphics resources and is intended
   * for use on the device's owning thread.
   *
   * @param device graphics device that supplies the backdrop resources
   * @return a dispatcher backed by the supplied device
   */
  public static UiRenderDispatcher create(Device device) {
    return new UiRenderDispatcher(device);
  }

  /**
   * Renders a root element and all of its visible descendants.
   *
   * <p>Traversal preserves child order, renders overlay parts after normal content, and applies
   * each element's clipping policy while descending into its children.
   *
   * @param graphics graphics context receiving the UI
   * @param root root element to render
   */
  public void render(Graphics graphics, Element root) {
    Rectangle absolute = root.absoluteBounds();
    Rectangle local = root.bounds();
    renderedWindows.clear();
    blurRegions.clear();
    try {
      renderLayer(graphics, root, absolute.minX() - local.minX(),
          absolute.minY() - local.minY());
    } finally {
      renderedWindows.clear();
      blurRegions.clear();
    }
  }

  @Override
  public void close() {
    if (backdropBlur != null) {
      backdropBlur.close();
    }
  }

  private void renderLayer(Graphics graphics, Element element, float parentX, float parentY) {
    if (!element.visible()) {
      return;
    }
    renderContent(graphics, element, parentX, parentY);
    Rectangle absolute = absoluteBounds(element, parentX, parentY);
    @Nullable Rectangle clip = element.childrenClipForRender(absolute);
    if (clip != null) {
      graphics.pushScissor(clip);
    }
    try {
      renderParts(graphics, element, parentX, parentY);
    } finally {
      if (clip != null) {
        graphics.popScissor();
      }
    }
  }

  private void renderContent(Graphics graphics, Element element, float parentX,
                             float parentY) {
    if (!element.visible()) {
      return;
    }
    Rectangle absolute = absoluteBounds(element, parentX, parentY);
    if (element instanceof Window) {
      drawOccludedBackdrop(graphics, absolute);
      renderedWindows.add(absolute);
    }
    element.renderer().render(graphics, element, absolute);
    @Nullable Rectangle clip = element.childrenClipForRender(absolute);
    if (clip != null) {
      graphics.pushScissor(clip);
    }
    try {
      for (Element child : element.children()) {
        if (child.isRenderLayerBoundary()) {
          renderLayer(graphics, child, absolute.minX(), absolute.minY());
        } else {
          renderContent(graphics, child, absolute.minX(), absolute.minY());
        }
      }
    } finally {
      if (clip != null) {
        graphics.popScissor();
      }
    }
  }

  private void renderParts(Graphics graphics, Element element, float parentX, float parentY) {
    if (!element.visible()) {
      return;
    }
    float absoluteX = parentX + element.bounds().minX();
    float absoluteY = parentY + element.bounds().minY();
    for (Element child : element.children()) {
      if (!child.isRenderLayerBoundary()) {
        renderParts(graphics, child, absoluteX, absoluteY);
      }
    }
    for (Element part : element.parts()) {
      renderContent(graphics, part, absoluteX, absoluteY);
      renderParts(graphics, part, absoluteX, absoluteY);
    }
  }

  private static Rectangle absoluteBounds(Element element, float parentX, float parentY) {
    Rectangle local = element.bounds();
    return Rectangle.of(parentX + local.minX(), parentY + local.minY(),
        local.width(), local.height());
  }

  private void drawOccludedBackdrop(Graphics graphics, Rectangle windowBounds) {
    if (backdropBlur == null) {
      return;
    }
    blurRegions.clear();
    for (Rectangle renderedWindow : renderedWindows) {
      if (!windowBounds.intersects(renderedWindow)) {
        continue;
      }
      Rectangle intersection = Rectangle.getIntersection(windowBounds, renderedWindow);
      int coveredRegionCount = blurRegions.size();
      appendUncovered(intersection, blurRegions, 0, coveredRegionCount);
    }
    for (Rectangle region : blurRegions) {
      backdropBlur.draw(graphics, region);
    }
  }

  private static void appendUncovered(Rectangle area, List<Rectangle> covered,
                                      int coveredIndex, int coveredCount) {
    for (int index = coveredIndex; index < coveredCount; index++) {
      Rectangle blocker = covered.get(index);
      if (!area.intersects(blocker)) {
        continue;
      }
      Rectangle overlap = Rectangle.getIntersection(area, blocker);
      appendRegion(area.minX(), area.minY(), overlap.minX(), area.maxY(),
          covered, index + 1, coveredCount);
      appendRegion(overlap.maxX(), area.minY(), area.maxX(), area.maxY(),
          covered, index + 1, coveredCount);
      appendRegion(overlap.minX(), area.minY(), overlap.maxX(), overlap.minY(),
          covered, index + 1, coveredCount);
      appendRegion(overlap.minX(), overlap.maxY(), overlap.maxX(), area.maxY(),
          covered, index + 1, coveredCount);
      return;
    }
    covered.add(area);
  }

  private static void appendRegion(float minX, float minY, float maxX, float maxY,
                                   List<Rectangle> covered, int coveredIndex,
                                   int coveredCount) {
    if (maxX <= minX || maxY <= minY) {
      return;
    }
    appendUncovered(new Rectangle(minX, minY, maxX, maxY), covered,
        coveredIndex, coveredCount);
  }
}
