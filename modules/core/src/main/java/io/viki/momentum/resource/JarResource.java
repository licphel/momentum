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

package io.viki.momentum.resource;

import org.jspecify.annotations.Nullable;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * A {@link Resource} backed by a JAR file.
 *
 * @param jarPath the JAR file
 */
public record JarResource(Path jarPath) implements Resource {
  /**
   * Creates a JAR-backed provider.
   *
   * @param jarPath the JAR file
   */
  public JarResource(Path jarPath) {
    this.jarPath = jarPath.toAbsolutePath().normalize();
  }

  /**
   * Returns the normalized JAR path.
   *
   * @return the JAR path
   */
  @Override
  public Path jarPath() {
    return jarPath;
  }

  @Override
  public @Nullable InputStream open(String path) throws IOException {
    String entryPath = Resource.normalizePath(path);
    JarFile jar = new JarFile(jarPath.toFile());
    JarEntry entry = jar.getJarEntry(entryPath);
    if (entry == null || entry.isDirectory()) {
      jar.close();
      return null;
    }
    try {
      return new JarResourceInputStream(jar, jar.getInputStream(entry));
    } catch (IOException e) {
      jar.close();
      throw e;
    }
  }

  @Override
  public Stream<String> walk(String relativeDir) throws IOException {
    String normalized = Resource.normalizePath(relativeDir);
    String prefix = normalized.isEmpty() ? "" : normalized + "/";
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      return jar.stream()
          .filter(entry -> !entry.isDirectory())
          .map(JarEntry::getName)
          .filter(name -> prefix.isEmpty() || name.startsWith(prefix))
          .sorted()
          .toList()
          .stream();
    }
  }

  private static class JarResourceInputStream extends FilterInputStream {
    private final JarFile jar;
    private boolean closed;

    JarResourceInputStream(JarFile jar, InputStream input) {
      super(input);
      this.jar = jar;
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      IOException failure = null;
      try {
        super.close();
      } catch (IOException e) {
        failure = e;
      }
      try {
        jar.close();
      } catch (IOException e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }
}
