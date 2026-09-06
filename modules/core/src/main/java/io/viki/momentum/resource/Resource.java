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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

/**
 * Provides access to resources from a directory, JAR, or classpath code
 * source.
 *
 * <p>Paths are provider-local and use forward slashes. Directory-backed,
 * JAR-backed, and ordinary classpath providers support both opening resources
 * and recursively walking their complete resource root. Custom providers may
 * support only opening and reject {@link #walk(String)}.
 *
 * @see #classpath(Class)
 * @see #directory(Path)
 * @see #jar(Path)
 * @see #combined(Resource...)
 */
public interface Resource extends AutoCloseable {
  /**
   * Creates a provider backed by the code source containing the caller class.
   *
   * <p>The caller's code source may be an exploded classes/resources
   * directory or a JAR. The returned provider uses that same root for opening
   * and walking resources. Paths are root-relative; a leading slash is
   * accepted for compatibility.
   *
   * @param caller the class whose code source supplies resources
   * @return a classpath-backed provider
   */
  static Resource classpath(Class<?> caller) {
    return new ClasspathResource(caller.getClassLoader());
  }

  /**
   * Creates a provider backed by a directory.
   *
   * @param basePath the resource directory
   * @return a directory-backed provider
   */
  static Resource directory(Path basePath) {
    return new SystemResource(basePath);
  }

  /**
   * Creates a provider backed by a directory path string.
   *
   * @param basePath the resource directory path
   * @return a directory-backed provider
   */
  static Resource directory(String basePath) {
    return directory(Paths.get(basePath));
  }

  /**
   * Creates a provider backed by a JAR file.
   *
   * @param jarPath the JAR file
   * @return a JAR-backed provider
   */
  static Resource jar(Path jarPath) {
    return new JarResource(jarPath);
  }

  /**
   * Creates a provider backed by a JAR file string.
   *
   * @param jarPath the JAR file
   * @return a JAR-backed provider
   */
  static Resource jar(String jarPath) {
    return new JarResource(Paths.get(jarPath));
  }

  /**
   * Creates a provider that tries sources in order and returns the first
   * matching resource.
   *
   * @param providers the resource providers in precedence order
   * @return a combined provider
   * @throws NullPointerException if the array or one of its entries is null
   */
  static Resource combined(Resource... providers) {
    return new CombinedResource(providers);
  }

  /**
   * Opens an input stream for a provider-local resource path.
   *
   * @param path the resource path
   * @return an input stream, or {@code null} when the resource does not exist
   * @throws IOException if an I/O error occurs
   * @throws NullPointerException if {@code path} is null
   */
  @Nullable InputStream open(String path) throws IOException;

  /**
   * Walks all regular files below a provider-local directory.
   *
   * <p>The returned paths are relative to the provider root and normalized
   * with forward slashes. The returned stream is owned by the caller.
   *
   * @param relativeDir the directory to walk, or an empty string for the root
   * @return a stream of resource paths
   * @throws IOException if walking fails
   * @throws UnsupportedOperationException if this provider cannot walk
   */
  Stream<String> walk(String relativeDir) throws IOException;

  /**
   * Closes resources owned by this provider.
   *
   * @throws IOException if closing fails
   */
  @Override
  default void close() throws IOException {
  }

  /**
   * Reads a resource as a UTF-8 string.
   *
   * @param path the resource path
   * @return the resource content
   * @throws RuntimeException if the resource cannot be read or is missing
   */
  default String readString(String path) {
    return new String(readBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Reads a resource as a byte array.
   *
   * @param path the resource path
   * @return the resource content
   * @throws RuntimeException if the resource cannot be read or is missing
   */
  default byte[] readBytes(String path) {
    try (InputStream in = open(path)) {
      if (in == null) {
        throw new FileNotFoundException("Resource not found: " + path);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }

  /**
   * Checks whether a resource exists at the given path.
   *
   * @param path the resource path
   * @return true if the resource exists
   */
  default boolean exists(String path) {
    try (InputStream in = open(path)) {
      return in != null;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Normalizes a provider-local resource path and rejects paths that escape
   * the provider root.
   *
   * @param path the raw resource path
   * @return a normalized relative path
   * @throws IllegalArgumentException if the path escapes the provider root
   */
  static String normalizePath(String path) {
    String raw = path.replace('\\', '/');
    while (raw.startsWith("/")) {
      raw = raw.substring(1);
    }

    Deque<String> segments = new ArrayDeque<>();
    for (String segment : raw.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".")) {
        continue;
      }
      if (segment.equals("..")) {
        if (segments.isEmpty()) {
          throw new IllegalArgumentException("Path escapes resource root: " + path);
        }
        segments.removeLast();
      } else {
        segments.addLast(segment);
      }
    }
    return String.join("/", segments);
  }

  /**
   * Resolves a normalized resource path below a directory root.
   *
   * @param base the normalized absolute directory root
   * @param path the provider-local path
   * @return the resolved path
   * @throws IOException if the path escapes the root
   */
  static Path resolve(Path base, String path) throws IOException {
    Path resolved = base.resolve(normalizePath(path)).normalize();
    if (!resolved.startsWith(base)) {
      throw new IOException("Path traversal detected: " + path);
    }
    return resolved;
  }
}
