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

package io.viki.momentum.input;

import org.jspecify.annotations.Nullable;

/**
 * Platform-agnostic key codes based on the USB HID Usage Tables (Keyboard/Keypad Page 0x07), with mouse buttons mapped
 * to virtual codes starting at {@value #MOUSE_OFFSET}.
 *
 * <p>Each backend maps its native key codes to these values. For example, a GLFW
 * backend maps {@code GLFW_KEY_A} → {@link #A}, an Android backend maps {@code KeyEvent.KEYCODE_A} → {@link #A}.
 *
 * <p>Use {@link #fromHidCode(int)} for reverse mapping from HID usage IDs,
 * and {@link #fromMouseId(int)} for mouse button IDs.
 */
public enum KeyCode {
  /** The A key. */ A(0x04),
  /** The B key. */ B(0x05),
  /** The C key. */ C(0x06),
  /** The D key. */ D(0x07),
  /** The E key. */ E(0x08),
  /** The F key. */ F(0x09),
  /** The G key. */ G(0x0A),
  /** The H key. */ H(0x0B),
  /** The I key. */ I(0x0C),
  /** The J key. */ J(0x0D),
  /** The K key. */ K(0x0E),
  /** The L key. */ L(0x0F),
  /** The M key. */ M(0x10),
  /** The N key. */ N(0x11),
  /** The O key. */ O(0x12),
  /** The P key. */ P(0x13),
  /** The Q key. */ Q(0x14),
  /** The R key. */ R(0x15),
  /** The S key. */ S(0x16),
  /** The T key. */ T(0x17),
  /** The U key. */ U(0x18),
  /** The V key. */ V(0x19),
  /** The W key. */ W(0x1A),
  /** The X key. */ X(0x1B),
  /** The Y key. */ Y(0x1C),
  /** The Z key. */ Z(0x1D),

  /** The 1 key. */ DIGIT_1(0x1E),
  /** The 2 key. */ DIGIT_2(0x1F),
  /** The 3 key. */ DIGIT_3(0x20),
  /** The 4 key. */ DIGIT_4(0x21),
  /** The 5 key. */ DIGIT_5(0x22),
  /** The 6 key. */ DIGIT_6(0x23),
  /** The 7 key. */ DIGIT_7(0x24),
  /** The 8 key. */ DIGIT_8(0x25),
  /** The 9 key. */ DIGIT_9(0x26),
  /** The 0 key. */ DIGIT_0(0x27),

  /** The Enter key. */ ENTER(0x28),
  /** The Escape key. */ ESCAPE(0x29),
  /** The Backspace key. */ BACKSPACE(0x2A),
  /** The Tab key. */ TAB(0x2B),
  /** The Space key. */ SPACE(0x2C),
  /** The minus key. */ MINUS(0x2D),
  /** The equals key. */ EQUALS(0x2E),
  /** The left bracket key. */ LEFT_BRACKET(0x2F),
  /** The right bracket key. */ RIGHT_BRACKET(0x30),
  /** The backslash key. */ BACKSLASH(0x31),
  /** The non-U.S. hash key. */ NON_US_HASH(0x32),
  /** The semicolon key. */ SEMICOLON(0x33),
  /** The apostrophe key. */ APOSTROPHE(0x34),
  /** The grave accent key. */ GRAVE_ACCENT(0x35),
  /** The comma key. */ COMMA(0x36),
  /** The period key. */ PERIOD(0x37),
  /** The slash key. */ SLASH(0x38),
  /** The Caps Lock key. */ CAPS_LOCK(0x39),

  /** The F1 function key. */ F1(0x3A),
  /** The F2 function key. */ F2(0x3B),
  /** The F3 function key. */ F3(0x3C),
  /** The F4 function key. */ F4(0x3D),
  /** The F5 function key. */ F5(0x3E),
  /** The F6 function key. */ F6(0x3F),
  /** The F7 function key. */ F7(0x40),
  /** The F8 function key. */ F8(0x41),
  /** The F9 function key. */ F9(0x42),
  /** The F10 function key. */ F10(0x43),
  /** The F11 function key. */ F11(0x44),
  /** The F12 function key. */ F12(0x45),
  /** The F13 function key. */ F13(0x68),
  /** The F14 function key. */ F14(0x69),
  /** The F15 function key. */ F15(0x6A),
  /** The F16 function key. */ F16(0x6B),
  /** The F17 function key. */ F17(0x6C),
  /** The F18 function key. */ F18(0x6D),
  /** The F19 function key. */ F19(0x6E),
  /** The F20 function key. */ F20(0x6F),
  /** The F21 function key. */ F21(0x70),
  /** The F22 function key. */ F22(0x71),
  /** The F23 function key. */ F23(0x72),
  /** The F24 function key. */ F24(0x73),
  /** The F25 function key. */ F25(0x74),

  /** The Print Screen key. */ PRINT_SCREEN(0x46),
  /** The Scroll Lock key. */ SCROLL_LOCK(0x47),
  /** The Pause key. */ PAUSE(0x48),
  /** The Insert key. */ INSERT(0x49),
  /** The Home key. */ HOME(0x4A),
  /** The Page Up key. */ PAGE_UP(0x4B),
  /** The Delete key. */ DELETE(0x4C),
  /** The End key. */ END(0x4D),
  /** The Page Down key. */ PAGE_DOWN(0x4E),

  /** The Right Arrow key. */ RIGHT(0x4F),
  /** The Left Arrow key. */ LEFT(0x50),
  /** The Down Arrow key. */ DOWN(0x51),
  /** The Up Arrow key. */ UP(0x52),

  /** The Num Lock key. */ NUM_LOCK(0x53),
  /** The numeric keypad divide key. */ KP_DIVIDE(0x54),
  /** The numeric keypad multiply key. */ KP_MULTIPLY(0x55),
  /** The numeric keypad subtract key. */ KP_SUBTRACT(0x56),
  /** The numeric keypad add key. */ KP_ADD(0x57),
  /** The numeric keypad Enter key. */ KP_ENTER(0x58),
  /** The numeric keypad 1 key. */ KP_1(0x59),
  /** The numeric keypad 2 key. */ KP_2(0x5A),
  /** The numeric keypad 3 key. */ KP_3(0x5B),
  /** The numeric keypad 4 key. */ KP_4(0x5C),
  /** The numeric keypad 5 key. */ KP_5(0x5D),
  /** The numeric keypad 6 key. */ KP_6(0x5E),
  /** The numeric keypad 7 key. */ KP_7(0x5F),
  /** The numeric keypad 8 key. */ KP_8(0x60),
  /** The numeric keypad 9 key. */ KP_9(0x61),
  /** The numeric keypad 0 key. */ KP_0(0x62),
  /** The numeric keypad decimal key. */ KP_DECIMAL(0x63),

  /** The non-U.S. backslash key. */ NON_US_BACKSLASH(0x64),
  /** The application key. */ APPLICATION(0x65),
  /** The power key. */ POWER(0x66),
  /** The numeric keypad equals key. */ KP_EQUALS(0x67),

  /** The menu key. */ MENU(0x76),

  /** The left Control key. */ LEFT_CONTROL(0xE0),
  /** The left Shift key. */ LEFT_SHIFT(0xE1),
  /** The left Alt key. */ LEFT_ALT(0xE2),
  /** The left Super key. */ LEFT_SUPER(0xE3),
  /** The right Control key. */ RIGHT_CONTROL(0xE4),
  /** The right Shift key. */ RIGHT_SHIFT(0xE5),
  /** The right Alt key. */ RIGHT_ALT(0xE6),
  /** The right Super key. */ RIGHT_SUPER(0xE7),

  /** Left mouse button. */
  MOUSE_LEFT(480),
  /** Right mouse button. */
  MOUSE_RIGHT(481),
  /** Middle mouse button (wheel click). */
  MOUSE_MIDDLE(482),
  /** Back thumb button. */
  MOUSE_BACK(483),
  /** Forward thumb button. */
  MOUSE_FORWARD(484),
  /** Extra mouse button 5. */
  MOUSE_5(485),
  /** Extra mouse button 6. */
  MOUSE_6(486),
  /** Extra mouse button 7. */
  MOUSE_7(487);

  /** First virtual code assigned to mouse buttons. */
  public static final int MOUSE_OFFSET = 480;

  private static final KeyCode[] LOOKUP;
  private static final KeyCode[] MOUSE_LOOKUP = new KeyCode[8];

  static {
    int max = 0;
    for (KeyCode k : values()) {
      max = Math.max(k.code, max);
    }
    LOOKUP = new KeyCode[max + 1];
    for (KeyCode k : values()) {
      LOOKUP[k.code] = k;
    }
    System.arraycopy(LOOKUP, 480, MOUSE_LOOKUP, 0, 8);
  }

  private final int code;

  KeyCode(int code) {
    this.code = code;
  }

  /**
   * Returns the {@code KeyCode} for the given USB HID usage ID, or {@code null} if no matching key exists.
   *
   * @param hidCode USB HID usage ID (keyboard page 0x07)
   * @return the matching key code, or {@code null}
   */
  public static @Nullable KeyCode fromHidCode(int hidCode) {
    return (hidCode >= 0 && hidCode < LOOKUP.length) ? LOOKUP[hidCode] : null;
  }

  /**
   * Returns the {@code KeyCode} for the given standard mouse button ID ({@code 0} = left, {@code 1} = right, …,
   * {@code 7} = button 7).
   *
   * @param mouseId the mouse button ID in {@code [0, 7]}
   * @return the matching key code, or {@code null} if out of range
   */
  public static @Nullable KeyCode fromMouseId(int mouseId) {
    return (mouseId >= 0 && mouseId < MOUSE_LOOKUP.length) ? MOUSE_LOOKUP[mouseId] : null;
  }

  /**
   * Returns the USB HID usage ID for keyboard keys, or the virtual code for mouse buttons.
   *
   * @return the numeric code
   */
  public int hidCode() {
    return code;
  }

  /**
   * Returns the standard mouse button ID ({@code 0} = left, …, {@code 7} = button 7), or {@code -1} if this is not a
   * mouse button.
   *
   * @return the mouse button ID, or {@code -1}
   */
  public int mouseId() {
    return (code >= MOUSE_OFFSET && code < MOUSE_OFFSET + 8) ? code - MOUSE_OFFSET : -1;
  }
}
