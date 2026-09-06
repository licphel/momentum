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

package io.viki.momentum.gfx.glfw;

import io.viki.momentum.gfx.input.KeyAction;
import io.viki.momentum.gfx.input.KeyCode;
import io.viki.momentum.internal.InternalApi;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.glfw.GLFW.*;

@InternalApi
final class GlfwKeyMapper {
  private static final KeyCode[] GLFW_KEY_MAP = new KeyCode[GLFW_KEY_LAST + 1];

  private static void map(int glfwKey, KeyCode code) {
    if (glfwKey >= 0 && glfwKey < GLFW_KEY_MAP.length) {
      GLFW_KEY_MAP[glfwKey] = code;
    }
  }

  static @Nullable KeyCode kc(int glfwKey) {
    return (glfwKey >= 0 && glfwKey < GLFW_KEY_MAP.length) ? GLFW_KEY_MAP[glfwKey] : null;
  }

  static KeyAction ac(int action) {
    return switch (action) {
      case GLFW_PRESS -> KeyAction.PRESS;
      case GLFW_REPEAT -> KeyAction.REPEAT;
      default -> KeyAction.RELEASE;
    };
  }

  static {
    map(GLFW_KEY_A, KeyCode.A);
    map(GLFW_KEY_B, KeyCode.B);
    map(GLFW_KEY_C, KeyCode.C);
    map(GLFW_KEY_D, KeyCode.D);
    map(GLFW_KEY_E, KeyCode.E);
    map(GLFW_KEY_F, KeyCode.F);
    map(GLFW_KEY_G, KeyCode.G);
    map(GLFW_KEY_H, KeyCode.H);
    map(GLFW_KEY_I, KeyCode.I);
    map(GLFW_KEY_J, KeyCode.J);
    map(GLFW_KEY_K, KeyCode.K);
    map(GLFW_KEY_L, KeyCode.L);
    map(GLFW_KEY_M, KeyCode.M);
    map(GLFW_KEY_N, KeyCode.N);
    map(GLFW_KEY_O, KeyCode.O);
    map(GLFW_KEY_P, KeyCode.P);
    map(GLFW_KEY_Q, KeyCode.Q);
    map(GLFW_KEY_R, KeyCode.R);
    map(GLFW_KEY_S, KeyCode.S);
    map(GLFW_KEY_T, KeyCode.T);
    map(GLFW_KEY_U, KeyCode.U);
    map(GLFW_KEY_V, KeyCode.V);
    map(GLFW_KEY_W, KeyCode.W);
    map(GLFW_KEY_X, KeyCode.X);
    map(GLFW_KEY_Y, KeyCode.Y);
    map(GLFW_KEY_Z, KeyCode.Z);
    map(GLFW_KEY_0, KeyCode.DIGIT_0);
    map(GLFW_KEY_1, KeyCode.DIGIT_1);
    map(GLFW_KEY_2, KeyCode.DIGIT_2);
    map(GLFW_KEY_3, KeyCode.DIGIT_3);
    map(GLFW_KEY_4, KeyCode.DIGIT_4);
    map(GLFW_KEY_5, KeyCode.DIGIT_5);
    map(GLFW_KEY_6, KeyCode.DIGIT_6);
    map(GLFW_KEY_7, KeyCode.DIGIT_7);
    map(GLFW_KEY_8, KeyCode.DIGIT_8);
    map(GLFW_KEY_9, KeyCode.DIGIT_9);
    map(GLFW_KEY_SPACE, KeyCode.SPACE);
    map(GLFW_KEY_ENTER, KeyCode.ENTER);
    map(GLFW_KEY_ESCAPE, KeyCode.ESCAPE);
    map(GLFW_KEY_BACKSPACE, KeyCode.BACKSPACE);
    map(GLFW_KEY_TAB, KeyCode.TAB);
    map(GLFW_KEY_MINUS, KeyCode.MINUS);
    map(GLFW_KEY_EQUAL, KeyCode.EQUALS);
    map(GLFW_KEY_LEFT_BRACKET, KeyCode.LEFT_BRACKET);
    map(GLFW_KEY_RIGHT_BRACKET, KeyCode.RIGHT_BRACKET);
    map(GLFW_KEY_BACKSLASH, KeyCode.BACKSLASH);
    map(GLFW_KEY_SEMICOLON, KeyCode.SEMICOLON);
    map(GLFW_KEY_APOSTROPHE, KeyCode.APOSTROPHE);
    map(GLFW_KEY_GRAVE_ACCENT, KeyCode.GRAVE_ACCENT);
    map(GLFW_KEY_COMMA, KeyCode.COMMA);
    map(GLFW_KEY_PERIOD, KeyCode.PERIOD);
    map(GLFW_KEY_SLASH, KeyCode.SLASH);
    map(GLFW_KEY_CAPS_LOCK, KeyCode.CAPS_LOCK);
    map(GLFW_KEY_F1, KeyCode.F1);
    map(GLFW_KEY_F2, KeyCode.F2);
    map(GLFW_KEY_F3, KeyCode.F3);
    map(GLFW_KEY_F4, KeyCode.F4);
    map(GLFW_KEY_F5, KeyCode.F5);
    map(GLFW_KEY_F6, KeyCode.F6);
    map(GLFW_KEY_F7, KeyCode.F7);
    map(GLFW_KEY_F8, KeyCode.F8);
    map(GLFW_KEY_F9, KeyCode.F9);
    map(GLFW_KEY_F10, KeyCode.F10);
    map(GLFW_KEY_F11, KeyCode.F11);
    map(GLFW_KEY_F12, KeyCode.F12);
    map(GLFW_KEY_F13, KeyCode.F13);
    map(GLFW_KEY_F14, KeyCode.F14);
    map(GLFW_KEY_F15, KeyCode.F15);
    map(GLFW_KEY_F16, KeyCode.F16);
    map(GLFW_KEY_F17, KeyCode.F17);
    map(GLFW_KEY_F18, KeyCode.F18);
    map(GLFW_KEY_F19, KeyCode.F19);
    map(GLFW_KEY_F20, KeyCode.F20);
    map(GLFW_KEY_F21, KeyCode.F21);
    map(GLFW_KEY_F22, KeyCode.F22);
    map(GLFW_KEY_F23, KeyCode.F23);
    map(GLFW_KEY_F24, KeyCode.F24);
    map(GLFW_KEY_F25, KeyCode.F25);
    map(GLFW_KEY_PRINT_SCREEN, KeyCode.PRINT_SCREEN);
    map(GLFW_KEY_SCROLL_LOCK, KeyCode.SCROLL_LOCK);
    map(GLFW_KEY_PAUSE, KeyCode.PAUSE);
    map(GLFW_KEY_INSERT, KeyCode.INSERT);
    map(GLFW_KEY_HOME, KeyCode.HOME);
    map(GLFW_KEY_PAGE_UP, KeyCode.PAGE_UP);
    map(GLFW_KEY_DELETE, KeyCode.DELETE);
    map(GLFW_KEY_END, KeyCode.END);
    map(GLFW_KEY_PAGE_DOWN, KeyCode.PAGE_DOWN);
    map(GLFW_KEY_RIGHT, KeyCode.RIGHT);
    map(GLFW_KEY_LEFT, KeyCode.LEFT);
    map(GLFW_KEY_DOWN, KeyCode.DOWN);
    map(GLFW_KEY_UP, KeyCode.UP);
    map(GLFW_KEY_NUM_LOCK, KeyCode.NUM_LOCK);
    map(GLFW_KEY_KP_DIVIDE, KeyCode.KP_DIVIDE);
    map(GLFW_KEY_KP_MULTIPLY, KeyCode.KP_MULTIPLY);
    map(GLFW_KEY_KP_SUBTRACT, KeyCode.KP_SUBTRACT);
    map(GLFW_KEY_KP_ADD, KeyCode.KP_ADD);
    map(GLFW_KEY_KP_ENTER, KeyCode.KP_ENTER);
    map(GLFW_KEY_KP_0, KeyCode.KP_0);
    map(GLFW_KEY_KP_1, KeyCode.KP_1);
    map(GLFW_KEY_KP_2, KeyCode.KP_2);
    map(GLFW_KEY_KP_3, KeyCode.KP_3);
    map(GLFW_KEY_KP_4, KeyCode.KP_4);
    map(GLFW_KEY_KP_5, KeyCode.KP_5);
    map(GLFW_KEY_KP_6, KeyCode.KP_6);
    map(GLFW_KEY_KP_7, KeyCode.KP_7);
    map(GLFW_KEY_KP_8, KeyCode.KP_8);
    map(GLFW_KEY_KP_9, KeyCode.KP_9);
    map(GLFW_KEY_KP_DECIMAL, KeyCode.KP_DECIMAL);
    map(GLFW_KEY_KP_EQUAL, KeyCode.KP_EQUALS);
    map(GLFW_KEY_LEFT_SHIFT, KeyCode.LEFT_SHIFT);
    map(GLFW_KEY_RIGHT_SHIFT, KeyCode.RIGHT_SHIFT);
    map(GLFW_KEY_LEFT_CONTROL, KeyCode.LEFT_CONTROL);
    map(GLFW_KEY_RIGHT_CONTROL, KeyCode.RIGHT_CONTROL);
    map(GLFW_KEY_LEFT_ALT, KeyCode.LEFT_ALT);
    map(GLFW_KEY_RIGHT_ALT, KeyCode.RIGHT_ALT);
    map(GLFW_KEY_LEFT_SUPER, KeyCode.LEFT_SUPER);
    map(GLFW_KEY_RIGHT_SUPER, KeyCode.RIGHT_SUPER);
    map(GLFW_KEY_MENU, KeyCode.MENU);
  }
}
