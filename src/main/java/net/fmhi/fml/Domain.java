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

package net.fmhi.fml;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A namespace that uniquely identifies a mod or resource origin.
 *
 * <p>Domains are interned — {@link #of(String)} returns the same instance for
 * equal names. Valid domain names consist of alphanumeric characters plus {@code :_/$.-}
 *
 * <p>The {@link #UNKNOWN} constant serves as a fallback when no specific domain
 * is known.
 *
 * <p>Instances are safe to use as map keys and compare by identity after interning.
 *
 * @see Identifier
 */
public final class Domain {
  private static final ConcurrentHashMap<String, Domain> CACHE = new ConcurrentHashMap<>();

  /**
   * Fallback domain used when no specific domain is known.
   */
  public static final Domain UNKNOWN = of("unknown");

  private final String name;

  private Domain(String name) {
    this.name = name;
  }

  /**
   * Returns {@code true} if the given string is a valid domain name.
   *
   * <p>Valid characters are ASCII alphanumerics and {@code :_/$.-}.
   *
   * @param name the candidate name
   * @return {@code true} if valid
   */
  public static boolean validate(String name) {
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (ch >= '0' && ch <= '9') {
        continue;
      }
      if (ch >= 'a' && ch <= 'z') {
        continue;
      }
      if (ch >= 'A' && ch <= 'Z') {
        continue;
      }
      if (":_/$.-".indexOf(ch) >= 0) {
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * Returns an interned domain with the given name.
   *
   * @param name the domain name; must pass {@link #validate(String)}
   * @return the domain
   * @throws IllegalArgumentException if the name is blank or invalid
   */
  public static Domain of(String name) {
    if (name.isBlank()) {
      throw new IllegalArgumentException("Domain name must not be blank");
    }
    if (!validate(name)) {
      throw new IllegalArgumentException("Invalid domain name: '" + name + "'");
    }
    return CACHE.computeIfAbsent(name, Domain::new);
  }

  /**
   * Returns the domain name.
   *
   * @return the name
   */
  public String name() {
    return name;
  }

  /**
   * Creates an identifier with this domain and the given path.
   *
   * @param path the resource path
   * @return a new identifier
   */
  public Identifier resolve(String path) {
    return new Identifier(this, path);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Domain other && name.equals(other.name);
  }

  @Override
  public String toString() {
    return name;
  }
}
