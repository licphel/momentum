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

package net.fmhi.gfx.input;

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
  A(0x04),
  B(0x05),
  C(0x06),
  D(0x07),
  E(0x08),
  F(0x09),
  G(0x0A),
  H(0x0B),
  I(0x0C),
  J(0x0D),
  K(0x0E),
  L(0x0F),
  M(0x10),
  N(0x11),
  O(0x12),
  P(0x13),
  Q(0x14),
  R(0x15),
  S(0x16),
  T(0x17),
  U(0x18),
  V(0x19),
  W(0x1A),
  X(0x1B),
  Y(0x1C),
  Z(0x1D),

  DIGIT_1(0x1E),
  DIGIT_2(0x1F),
  DIGIT_3(0x20),
  DIGIT_4(0x21),
  DIGIT_5(0x22),
  DIGIT_6(0x23),
  DIGIT_7(0x24),
  DIGIT_8(0x25),
  DIGIT_9(0x26),
  DIGIT_0(0x27),

  ENTER(0x28),
  ESCAPE(0x29),
  BACKSPACE(0x2A),
  TAB(0x2B),
  SPACE(0x2C),
  MINUS(0x2D),
  EQUALS(0x2E),
  LEFT_BRACKET(0x2F),
  RIGHT_BRACKET(0x30),
  BACKSLASH(0x31),
  NON_US_HASH(0x32),
  SEMICOLON(0x33),
  APOSTROPHE(0x34),
  GRAVE_ACCENT(0x35),
  COMMA(0x36),
  PERIOD(0x37),
  SLASH(0x38),
  CAPS_LOCK(0x39),

  F1(0x3A),
  F2(0x3B),
  F3(0x3C),
  F4(0x3D),
  F5(0x3E),
  F6(0x3F),
  F7(0x40),
  F8(0x41),
  F9(0x42),
  F10(0x43),
  F11(0x44),
  F12(0x45),
  F13(0x68),
  F14(0x69),
  F15(0x6A),
  F16(0x6B),
  F17(0x6C),
  F18(0x6D),
  F19(0x6E),
  F20(0x6F),
  F21(0x70),
  F22(0x71),
  F23(0x72),
  F24(0x73),
  F25(0x74),

  PRINT_SCREEN(0x46),
  SCROLL_LOCK(0x47),
  PAUSE(0x48),
  INSERT(0x49),
  HOME(0x4A),
  PAGE_UP(0x4B),
  DELETE(0x4C),
  END(0x4D),
  PAGE_DOWN(0x4E),

  RIGHT(0x4F),
  LEFT(0x50),
  DOWN(0x51),
  UP(0x52),

  NUM_LOCK(0x53),
  KP_DIVIDE(0x54),
  KP_MULTIPLY(0x55),
  KP_SUBTRACT(0x56),
  KP_ADD(0x57),
  KP_ENTER(0x58),
  KP_1(0x59),
  KP_2(0x5A),
  KP_3(0x5B),
  KP_4(0x5C),
  KP_5(0x5D),
  KP_6(0x5E),
  KP_7(0x5F),
  KP_8(0x60),
  KP_9(0x61),
  KP_0(0x62),
  KP_DECIMAL(0x63),

  NON_US_BACKSLASH(0x64),
  APPLICATION(0x65),
  POWER(0x66),
  KP_EQUALS(0x67),

  MENU(0x76),

  LEFT_CONTROL(0xE0),
  LEFT_SHIFT(0xE1),
  LEFT_ALT(0xE2),
  LEFT_SUPER(0xE3),
  RIGHT_CONTROL(0xE4),
  RIGHT_SHIFT(0xE5),
  RIGHT_ALT(0xE6),
  RIGHT_SUPER(0xE7),

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
  public static KeyCode fromHidCode(int hidCode) {
    return (hidCode >= 0 && hidCode < LOOKUP.length) ? LOOKUP[hidCode] : null;
  }

  /**
   * Returns the {@code KeyCode} for the given standard mouse button ID ({@code 0} = left, {@code 1} = right, …,
   * {@code 7} = button 7).
   *
   * @param mouseId the mouse button ID in {@code [0, 7]}
   * @return the matching key code, or {@code null} if out of range
   */
  public static KeyCode fromMouseId(int mouseId) {
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
