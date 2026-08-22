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

package net.fmhi.util.internal;

/**
 * Utility class for detecting the availability of native system libraries. Uses JNA (Java Native Access) to safely
 * probe for library presence without triggering restricted API warnings in the Java module system.
 */
public final class Native {
  private Native() {
  }

  /**
   * Checks whether the specified native library is available and can be loaded.
   * <p>
   * This method attempts to locate and load the library using JNA's platform-specific search mechanism. It does not
   * actually link any native methods, making it safe for probing library availability.
   * <p>
   * The library name should be specified without the platform-specific prefix or suffix. For example:
   * <ul>
   *   <li>{@code "openal"} will search for {@code openal.dll} on Windows,
   *       {@code libopenal.so} on Linux, and {@code libopenal.dylib} on macOS</li>
   *   <li>{@code "X11"} will search for {@code libX11.so} on Linux</li>
   *   <li>{@code "user32"} will search for {@code user32.dll} on Windows</li>
   * </ul>
   *
   * @param libraryNames the bases name of the libraries without platform prefix/suffix
   * @return {@code true} if the library was found and successfully loaded into memory; {@code false} if the
   * library could not be found, failed to load, or any of its dependencies are missing
   */
  @SuppressWarnings("restricted")
  public static boolean check(String... libraryNames) {
    for (String libraryName : libraryNames) {
      try {
        Runtime.getRuntime().loadLibrary(libraryName);
        return true;
      } catch (UnsatisfiedLinkError e) {
        // Ignored
      }
    }

    return false;
  }
}
