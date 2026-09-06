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

package io.viki.momentum.util;

import java.net.URI;

/**
 * A namespace-qualified resource identifier, similar to Minecraft's {@code ResourceLocation}.
 *
 * <p>Format: {@code namespace:path}. The namespace is a {@link Namespace} namespace;
 * the path is a forward-slash-separated resource path string (not a filesystem path).
 *
 * <p>If the string form contains no colon, the namespace defaults to
 * {@link Namespace#UNKNOWN}.
 *
 * <p>Instances are immutable and safe to use as map keys.
 *
 * @param namespace the namespace
 * @param path      the resource path within that namespace
 * @see Namespace
 */
public record Identifier(Namespace namespace, String path) {
  /**
   * Parses an identifier from the string form {@code namespace:path} or {@code path}.
   *
   * <p>If {@code full} contains no colon, the entire string is treated as
   * the path and the namespace defaults to {@link Namespace#UNKNOWN}.
   *
   * @param full the string to parse, e.g. {@code "mymod:textures/stone.png"}
   * @return the parsed identifier
   * @throws IllegalArgumentException if the format is invalid
   */
  public static Identifier of(String full) {
    if (full.isBlank()) {
      throw new IllegalArgumentException("Identifier must not be blank");
    }

    int colon = full.indexOf(':');
    if (colon < 0) {
      return new Identifier(Namespace.UNKNOWN, full);
    }
    if (colon == 0 || colon == full.length() - 1) {
      throw new IllegalArgumentException("Identifier has empty namespace or path: '" + full + "'");
    }

    String namespacePart = full.substring(0, colon);
    String pathPart = full.substring(colon + 1);
    if (full.indexOf(':', colon + 1) >= 0) {
      throw new IllegalArgumentException("Identifier must not contain more than one colon: '" + full + "'");
    }
    return new Identifier(Namespace.of(namespacePart), pathPart);
  }

  /**
   * Parses an identifier from the string form {@code namespace} and {@code path}.
   *
   * @param namespace the namespace
   * @param path      the path
   * @return the parsed identifier, e.g. {@code namespace:path}
   * @throws IllegalArgumentException if the format is invalid
   */
  public static Identifier of(String namespace, String path) {
    return new Identifier(Namespace.of(namespace), path);
  }

  /**
   * Returns this identifier as a {@link URI} with the namespace as the scheme.
   *
   * <p>The resulting URI has the form {@code namespace://path}.
   *
   * @return a URI representation
   */
  public URI toURI() {
    return URI.create(namespace.name() + "://" + path);
  }

  @Override
  public String toString() {
    return namespace.name() + ":" + path;
  }
}
