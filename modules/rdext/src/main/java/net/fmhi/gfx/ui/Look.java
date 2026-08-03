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

package net.fmhi.gfx.ui;

import net.fmhi.gfx.brush.Alignment;
import net.fmhi.gfx.brush.Graphics2D;
import net.fmhi.gfx.text.Text;
import net.fmhi.gfx.ui.look.ModernFlat;
import net.fmhi.math.Box2D;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable rendering strategy for UI widgets.
 *
 * <p>Implement this interface to fully control the visual appearance of every widget type. Each
 * {@code draw*} method receives the widget's current state and absolute bounds, and must render using only the given
 * {@link Graphics2D}. Every draw method must restore the g color to {@code Color.WHITE} before returning so the
 * caller's
 * gradient state is not corrupted.
 *
 * <p>Metric methods such as {@link #titleBarHeight()}, {@link #resizeHandleSize()}, and
 * {@link #borderWidth()} are called during layout and must return stable values within a single frame. The framework
 * ships a default implementation, {@link ModernFlat}, that provides a flat dark fmt requiring no textures.
 *
 * <p>Implementations of this interface should be stateless or immutable.
 */
public interface Look {
  /**
   * Returns the height of a floating window's title bar in logical units.
   *
   * @return the title bar height
   */
  float titleBarHeight();

  /**
   * Returns the size of the resize handle square in the bottom-right corner of a window.
   *
   * @return the resize handle size in logical units
   */
  float resizeHandleSize();

  /**
   * Returns the width of a floating window's outer border in logical units.
   *
   * @return the border width
   */
  float borderWidth();

  /**
   * Draws a button in the given visual state.
   *
   * @param g      the drawing context
   * @param state  the current visual state of the button
   * @param bounds the button's absolute bounds in logical units
   * @param label  the label text centered on the button, or {@code null} for no text
   */
  void drawButton(Graphics2D g, ButtonState state, Box2D bounds, @Nullable Text label);

  /**
   * Draws a panel background.
   *
   * @param g      the drawing context
   * @param bounds the panel's absolute bounds in logical units
   */
  void drawPanel(Graphics2D g, Box2D bounds);

  /**
   * Draws a scroll pane background.
   *
   * @param g      the drawing context
   * @param bounds the scroll pane's absolute bounds in logical units
   */
  void drawScrollPane(Graphics2D g, Box2D bounds);

  /**
   * Draws the outer chrome of a floating window, including background, border, and shadow.
   *
   * @param g      the drawing context
   * @param state  the current visual state of the window
   * @param bounds the window's total absolute bounds in logical units
   */
  void drawWindowFrame(Graphics2D g, WindowState state, Box2D bounds);

  /**
   * Draws the title bar strip of a floating window.
   *
   * @param g              the drawing context
   * @param focused        whether this window currently has keyboard focus
   * @param titleBarBounds the title bar's absolute bounds in logical units
   * @param title          the title text, or {@code null} for no title
   */
  void drawWindowTitleBar(Graphics2D g, boolean focused, Box2D titleBarBounds, @Nullable Text title);

  /**
   * Draws the close button of a floating window.
   *
   * @param g       the drawing context
   * @param hovered whether the cursor is over the button
   * @param bounds  the button's absolute bounds in logical units
   */
  void drawWindowCloseButton(Graphics2D g, boolean hovered, Box2D bounds);

  /**
   * Draws the minimize button of a floating window.
   *
   * @param g       the drawing context
   * @param hovered whether the cursor is over the button
   * @param bounds  the button's absolute bounds in logical units
   */
  void drawWindowMinimizeButton(Graphics2D g, boolean hovered, Box2D bounds);

  /**
   * Draws the maximize button of a floating window.
   *
   * @param g       the drawing context
   * @param hovered whether the cursor is over the button
   * @param bounds  the button's absolute bounds in logical units
   */
  void drawWindowMaximizeButton(Graphics2D g, boolean hovered, Box2D bounds);

  /**
   * Draws a text label.
   *
   * @param g         the drawing context
   * @param bounds    the label's absolute bounds in logical units
   * @param text      the text to draw, or {@code null} for no text
   * @param alignment the alignment of the text within the bounds
   */
  default void drawLabel(Graphics2D g, Box2D bounds, @Nullable Text text, Alignment alignment) {
  }

  /**
   * Draws a checkbox.
   *
   * @param g       the drawing context
   * @param checked whether the checkbox is checked
   * @param hovered whether the cursor is over the checkbox
   * @param bounds  the checkbox's absolute bounds in logical units
   */
  default void drawCheckbox(Graphics2D g, boolean checked, boolean hovered, Box2D bounds) {
  }

  /**
   * Draws a slider track.
   *
   * @param g      the drawing context
   * @param bounds the track's absolute bounds in logical units
   */
  default void drawSliderTrack(Graphics2D g, Box2D bounds) {
  }

  /**
   * Draws a slider thumb.
   *
   * @param g           the drawing context
   * @param dragging    whether the thumb is currently being dragged
   * @param thumbBounds the thumb's absolute bounds in logical units
   */
  default void drawSliderThumb(Graphics2D g, boolean dragging, Box2D thumbBounds) {
  }

  /**
   * Called when the physical-to-logical pixel density changes, for example on window resize or DPI change.
   * Implementations that scale fonts or borders by density may cache the value.
   *
   * @param density the ratio of physical pixels per logical unit
   */
  default void setPixelDensity(float density) {
  }
}
