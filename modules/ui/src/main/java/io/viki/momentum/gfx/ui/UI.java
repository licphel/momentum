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

import io.viki.momentum.gfx.quick2d.impl.Graphics;
import io.viki.momentum.gfx.input.KeyAction;
import io.viki.momentum.gfx.input.KeyCode;
import io.viki.momentum.math.Box2D;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * A top-level UI surface coordinating one resolution, one look, and its element tree.
 *
 * <p>{@code Face} is not thread-safe; all methods must be called from the rendering thread.
 */
public final class UI extends Element {
  private PrimaryContext ctx;
  private @Nullable Resolution resolution;

  public UI(PrimaryContext ctx, Look look) {
    super(Box2D.create(Vector2.ZERO, ctx.getLogicalSize()), look);
    this.ctx = ctx;;
  }

  @Override
  public void draw(Graphics graphics) {
    resolution = Resolution.auto(
        (int) ctx.getLogicalSize().x(),
        (int) ctx.getLogicalSize().y(),
        ctx.getInputSize().x(),
        ctx.getInputSize().y(),
        false,
        ctx.getTransformHandler()
    );
    resolution.apply(graphics);
    drawChildren(graphics);
  }

  /** Dispatches an event after converting its position from window/input to logical coordinates. */
  public boolean dispatchInput(UiEvent event) {
    if (event instanceof UiEvent.Positioned positioned) {
      double inputX = positioned.position().x();
      double inputY = positioned.position().y();
      Vector2 logical = resolution.inputToLogical(inputX, inputY, ctx.getInputSize().x(), ctx.getInputSize().y());
      return dispatch(positioned.withPosition(logical));
    }
    return dispatch(event);
  }

  /** Dispatches a pointer move after converting window/input coordinates to logical space. */
  public void moveInput(double inputX, double inputY, double inputWidth, double inputHeight) {
    move(resolution.inputToLogical(inputX, inputY, inputWidth, inputHeight));
  }

  /** Dispatches a pointer press after converting window/input coordinates to logical space. */
  public boolean pressInput(double inputX, double inputY, double inputWidth, double inputHeight) {
    return press(resolution.inputToLogical(inputX, inputY, inputWidth, inputHeight));
  }

  /** Dispatches a pointer release after converting window/input coordinates to logical space. */
  public boolean releaseInput(double inputX, double inputY, double inputWidth, double inputHeight) {
    return release(resolution.inputToLogical(inputX, inputY, inputWidth, inputHeight));
  }

  /** Updates the topmost button under a logical cursor position. */
  public void move(Vector2 logicalPosition) {
    dispatch(new UiEvent.MouseMove(logicalPosition));
  }

  public boolean press(Vector2 logicalPosition) {
    return dispatch(new UiEvent.MouseButton(logicalPosition, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0));
  }

  public boolean release(Vector2 logicalPosition) {
    return dispatch(new UiEvent.MouseButton(logicalPosition, KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 0));
  }
}
