/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, publish, distribute, sublicense, and/or sell
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
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.quick2d.Alignment;
import io.viki.momentum.math.Box2D;
import io.viki.momentum.gfx.color.Color;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * Minimal logical-space button with injected look, hit testing, and click handling.
 *
 * <p>The background keys deliberately use {@code StyleKey<Object>} because a background may be
 * either a {@link Color} or a {@link Drawable2D}; Button performs the small supported union
 * dispatch while ownership and loading remain outside this module.
 *
 * <p>Button state is mutable and not thread-safe; use it on the owning UI/render thread.
 */
public final class Button extends Element {
  public static final StyleKey<Object> IDLE_BACKGROUND = new StyleKey<>("button.idle.background", Object.class);
  public static final StyleKey<Object> HOVERED_BACKGROUND = new StyleKey<>("button.hovered.background", Object.class);
  public static final StyleKey<Object> PRESSED_BACKGROUND = new StyleKey<>("button.pressed.background", Object.class);
  public static final StyleKey<Object> DISABLED_BACKGROUND = new StyleKey<>("button.disabled.background", Object.class);
  public static final StyleKey<Text> LABEL = new StyleKey<>("button.label", Text.class);
  public static final StyleKey<Color> LABEL_COLOR = new StyleKey<>("button.label.color", Color.class);
  private State state = State.IDLE;
  private boolean enabled = true;
  private @Nullable Runnable onClick;

  public Button(Box2D bounds, Look look) {
    super(bounds, look);
  }

  public State state() {
    return enabled ? state : State.DISABLED;
  }

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      state = State.IDLE;
    }
  }

  public void setOnClick(@Nullable Runnable value) {
    onClick = value;
  }

  public void move(Vector2 position) {
    if (!enabled) {
      return;
    }
    if (state != State.PRESSED) {
      state = contains(position.x(), position.y()) ? State.HOVERED : State.IDLE;
    }
  }

  /** Begins a primary-button press if the logical position is inside this button. */
  public boolean press(Vector2 position) {
    if (!enabled || !contains(position.x(), position.y())) {
      return false;
    }
    state = State.PRESSED;
    return true;
  }

  /** Releases a press, invoking the callback only when release remains inside the button. */
  public boolean release(Vector2 position) {
    boolean clicked = enabled && state == State.PRESSED && contains(position.x(), position.y());
    state = contains(position.x(), position.y()) ? State.HOVERED : State.IDLE;
    if (clicked && onClick != null) {
      onClick.run();
    }
    return clicked;
  }

  @Override
  protected void drawSelf(Graphics graphics, Box2D area) {
    Object background = look().get(backgroundKey());
    if (background == null) {
      background = look().get(IDLE_BACKGROUND);
    }
    if (background instanceof Color color) {
      graphics.setTint(color);
      graphics.drawRectangle(area);
    } else if (background instanceof Drawable2D drawable) {
      graphics.draw(drawable, area);
    }
    Text label = look().get(LABEL);
    if (label != null) {
      Color labelColor = look().get(LABEL_COLOR);
      graphics.setTint(labelColor == null ? Color.WHITE : labelColor);
      graphics.drawText(label, area.center(), Alignment.CENTRAL);
    }
    graphics.setTint(Color.WHITE);
  }

  @Override
  protected boolean onEvent(UiEvent event) {
    if (!enabled) {
      return false;
    }
    if (event instanceof UiEvent.MouseMove move) {
      boolean inside = containsLocal(move.position().x(), move.position().y());
      if (state != State.PRESSED) {
        state = inside ? State.HOVERED : State.IDLE;
      }
      return inside || state == State.PRESSED;
    }
    if (event instanceof UiEvent.MouseButton mouse) {
      boolean inside = containsLocal(mouse.position().x(), mouse.position().y());
      if (mouse.button() != KeyCode.MOUSE_LEFT || mouse.action() == KeyAction.REPEAT) {
        return false;
      }
      if (mouse.action() == KeyAction.PRESS) {
        if (!inside) {
          return false;
        }
        state = State.PRESSED;
        return true;
      }
      if (state != State.PRESSED) {
        return false;
      }
      state = inside ? State.HOVERED : State.IDLE;
      if (inside && onClick != null) {
        onClick.run();
      }
      return true;
    }
    return false;
  }

  private StyleKey<Object> backgroundKey() {
    return switch (state()) {
      case IDLE -> IDLE_BACKGROUND;
      case HOVERED -> HOVERED_BACKGROUND;
      case PRESSED -> PRESSED_BACKGROUND;
      case DISABLED -> DISABLED_BACKGROUND;
    };
  }

  public enum State {
    IDLE,
    HOVERED,
    PRESSED,
    DISABLED
  }
}
