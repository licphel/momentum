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

import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyBinding;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.KeyMatch;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.math.Box2D;
import io.viki.momentum.gfx.tint.Color;
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
  public static final StyleKey<KeyBinding> ACTIVATE_BINDING = new StyleKey<>("button.activate",
      KeyBinding.class);
  private State state = State.IDLE;
  private boolean enabled = true;
  private boolean hovered;
  private boolean pointerPressed;
  private boolean keyboardPressed;
  private @Nullable KeyCode pointerKey;
  private @Nullable KeyCode keyboardKey;
  private @Nullable Runnable onClick;

  public Button(Box2D bounds, Look look) {
    super(bounds, look);
  }

  /** Creates the conventional mouse, Enter, and Space activation binding for one input snapshot. */
  public static KeyBinding defaultActivate(InputSnapshot snapshot) {
    return new KeyBinding("button.activate",
        KeyMatch.of(snapshot.key(KeyCode.MOUSE_LEFT)),
        KeyMatch.of(snapshot.key(KeyCode.ENTER)),
        KeyMatch.of(snapshot.key(KeyCode.KP_ENTER)),
        KeyMatch.of(snapshot.key(KeyCode.SPACE)));
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
      hovered = false;
      pointerPressed = false;
      keyboardPressed = false;
      pointerKey = null;
      keyboardKey = null;
    }
    refreshState();
  }

  public void setOnClick(@Nullable Runnable value) {
    onClick = value;
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
  public boolean onMouseMove(float x, float y) {
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button.mouseId() < 0 || action == KeyAction.REPEAT) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      KeyBinding binding = activationBinding();
      if (binding == null || !binding.transitioned()) {
        return false;
      }
      pointerPressed = true;
      pointerKey = button;
      refreshState();
      return true;
    }
    if (!pointerPressed || pointerKey != button) {
      return false;
    }
    pointerPressed = false;
    pointerKey = null;
    boolean clicked = containsLocal(x, y);
    refreshState();
    runClick(clicked);
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      KeyBinding binding = activationBinding();
      if (binding == null || !binding.transitioned()) {
        return false;
      }
      keyboardPressed = true;
      keyboardKey = key;
      refreshState();
      return true;
    }
    if (!keyboardPressed || keyboardKey != key) {
      return false;
    }
    if (action == KeyAction.RELEASE) {
      keyboardPressed = false;
      keyboardKey = null;
      refreshState();
      runClick(true);
    }
    return true;
  }

  @Override
  public void onPointerEnter() {
    hovered = true;
    refreshState();
  }

  @Override
  public void onPointerExit() {
    hovered = false;
    refreshState();
  }

  @Override
  public void onPointerCancel(KeyCode button) {
    if (pointerKey == button) {
      pointerPressed = false;
      pointerKey = null;
      refreshState();
    }
  }

  @Override
  public boolean acceptsFocus() {
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean focused) {
  }

  @Override
  public void onKeyCancel(KeyCode key) {
    if (keyboardKey == key) {
      keyboardPressed = false;
      keyboardKey = null;
      refreshState();
    }
  }

  private StyleKey<Object> backgroundKey() {
    return switch (state()) {
      case IDLE -> IDLE_BACKGROUND;
      case HOVERED -> HOVERED_BACKGROUND;
      case PRESSED -> PRESSED_BACKGROUND;
      case DISABLED -> DISABLED_BACKGROUND;
    };
  }

  private void refreshState() {
    if (pointerPressed || keyboardPressed) {
      state = State.PRESSED;
    } else {
      state = hovered ? State.HOVERED : State.IDLE;
    }
  }

  private void runClick(boolean clicked) {
    if (clicked && onClick != null) {
      onClick.run();
    }
  }

  private @Nullable KeyBinding activationBinding() {
    return look().get(ACTIVATE_BINDING);
  }

  public enum State {
    IDLE,
    HOVERED,
    PRESSED,
    DISABLED
  }
}
