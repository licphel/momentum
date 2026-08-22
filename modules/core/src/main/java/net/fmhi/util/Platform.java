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

package net.fmhi.util;

import net.fmhi.util.internal.NativeLookup;

/**
 * Identifies the operating system on which the application is running.
 *
 * <p>Detection is based on the {@code os.name} system property and considers
 * only the OS family, not the underlying architecture.
 *
 * @see NativeLookup
 */
public enum Platform {
  /** Microsoft Windows. */
  WINDOWS,
  /** Apple macOS. */
  MACOS,
  /** Desktop Linux distributions. */
  LINUX,
  /** Google Android. */
  ANDROID,
  /** Apple iOS. */
  IOS,
  /** An unrecognized operating system. */
  OTHER;

  /**
   * Returns the current operating system detected at runtime.
   *
   * @return the current platform; never {@code null}
   */
  public static Platform currentOS() {
    String OS_NAME = System.getProperty("os.name").toLowerCase();

    if (OS_NAME.contains("win")) {
      return WINDOWS;
    }
    if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
      return MACOS;
    }
    if (OS_NAME.contains("linux")) {
      return LINUX;
    }
    if (OS_NAME.contains("android")) {
      return ANDROID;
    }
    if (OS_NAME.contains("ios")) {
      return IOS;
    }
    return OTHER;
  }
}
