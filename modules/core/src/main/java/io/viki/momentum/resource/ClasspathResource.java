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

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

/**
 * A {@link Resource} backed by the classpath using ClassLoader.
 *
 * <p>This provider uses the specified classloader to locate resources via
 * {@link ClassLoader#getResourceAsStream(String)}. It works identically in
 * both development (exploded classes) and production (JAR) environments.
 *
 * <p>Paths are relative to the classpath root. For example, to access
 * {@code /META-INF/MANIFEST.MF}, use {@code "META-INF/MANIFEST.MF"}.
 *
 * <p>Note: This provider does not support {@link #walk(String)} and will
 * throw {@link UnsupportedOperationException} if called.
 */
public final class ClasspathResource implements Resource {
  private final ClassLoader classLoader;
  /**
   * Creates a classpath provider using the current thread's context classloader.
   */
  public ClasspathResource() {
    this(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a classpath provider using the specified classloader.
   *
   * @param classLoader the classloader to use
   * @throws NullPointerException if classLoader is null
   */
  public ClasspathResource(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public @Nullable InputStream open(String path) throws IOException {
    String normalized = Resource.normalizePath(path);
    return classLoader.getResourceAsStream(normalized);
  }

  @Override
  public Stream<String> walk(String relativeDir) throws IOException {
    throw new UnsupportedOperationException();
  }
}