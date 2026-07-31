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

/**
 * An immutable representation of a <a href="https://semver.org">Semantic Version</a> string
 * ({@code major.minor.patch}).
 *
 * <p>Instances are comparable and sort in ascending version order.
 * All version components must be non-negative integers.
 *
 * @param major the major version number, must be &ge; 0
 * @param minor the minor version number, must be &ge; 0
 * @param patch the patch version number, must be &ge; 0
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {
  /**
   * Creates a semantic version, validating that all components are non-negative.
   *
   * @param major the major version number, must be &ge; 0
   * @param minor the minor version number, must be &ge; 0
   * @param patch the patch version number, must be &ge; 0
   * @throws IllegalArgumentException if any component is negative
   */
  public SemanticVersion {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new IllegalArgumentException(String.format("Version components must be >= 0, got: %d.%d.%d", major, minor
          , patch));
    }
  }

  /**
   * Parses a version string in the form {@code major.minor.patch}.
   *
   * <p>Each component must be a non-negative integer. Leading zeros are
   * accepted by {@link Integer#parseInt} but should be avoided per the semver specification.
   *
   * @param version the version string to parse, e.g. {@code "2.1.0"}
   * @return the parsed semantic version
   * @throws IllegalArgumentException if the string does not contain exactly three dot-separated integer components
   * @throws NumberFormatException    if any component is not a valid integer
   */
  public static SemanticVersion parse(String version) {
    String[] parts = version.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Expected major.minor.patch format, got: " + version);
    }

    int major = Integer.parseInt(parts[0]);
    int minor = Integer.parseInt(parts[1]);
    int patch = Integer.parseInt(parts[2]);

    return new SemanticVersion(major, minor, patch);
  }

  @Override
  public int compareTo(SemanticVersion o) {
    int c = Integer.compare(major, o.major);
    if (c != 0) {
      return c;
    }
    c = Integer.compare(minor, o.minor);
    if (c != 0) {
      return c;
    }
    return Integer.compare(patch, o.patch);
  }

  @Override
  public String toString() {
    return String.format("%d.%d.%d", major, minor, patch);
  }
}
