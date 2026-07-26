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

import java.net.URI;

/**
 * A domain-qualified resource identifier, similar to Minecraft's {@code ResourceLocation}.
 *
 * <p>Format: {@code domain:path}. The domain is a {@link Domain} namespace;
 * the path is a forward-slash-separated resource path string (not a filesystem path).
 *
 * <p>If the string form contains no colon, the domain defaults to
 * {@link Domain#UNKNOWN}.
 *
 * <p>Instances are immutable and safe to use as map keys.
 *
 * @param domain the namespace
 * @param path   the resource path within that namespace
 * @see Domain
 */
public record Identifier(Domain domain, String path) {
  /**
   * Parses an identifier from the string form {@code domain:path} or {@code path}.
   *
   * <p>If {@code full} contains no colon, the entire string is treated as
   * the path and the domain defaults to {@link Domain#UNKNOWN}.
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
      return new Identifier(Domain.UNKNOWN, full);
    }
    if (colon == 0 || colon == full.length() - 1) {
      throw new IllegalArgumentException("Identifier has empty domain or path: '" + full + "'");
    }

    String domainPart = full.substring(0, colon);
    String pathPart = full.substring(colon + 1);
    if (full.indexOf(':', colon + 1) >= 0) {
      throw new IllegalArgumentException("Identifier must not contain more than one colon: '" + full + "'");
    }
    return new Identifier(Domain.of(domainPart), pathPart);
  }

  /**
   * Parses an identifier from the string form {@code domain} and {@code path}.
   *
   * @param domain the domain
   * @param path   the path
   * @return the parsed identifier, e.g. {@code domain:path}
   * @throws IllegalArgumentException if the format is invalid
   */
  public static Identifier of(String domain, String path) {
    return new Identifier(Domain.of(domain), path);
  }

  /**
   * Returns this identifier as a {@link URI} with the domain as the scheme.
   *
   * <p>The resulting URI has the form {@code domain://path}.
   *
   * @return a URI representation
   */
  public URI toURI() {
    return URI.create(domain.name() + "://" + path);
  }

  @Override
  public String toString() {
    return domain.name() + ":" + path;
  }
}
