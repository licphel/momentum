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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A provider that combines multiple providers in precedence order.
 */
public final class CombinedResource implements Resource {
  private final Resource[] providers;

  /**
   * Creates a combined provider.
   *
   * @param providers providers in precedence order
   */
  public CombinedResource(Resource... providers) {
    this.providers = providers.clone();
  }

  @Override
  public @Nullable InputStream open(String path) throws IOException {
    for (Resource provider : providers) {
      try {
        InputStream input = provider.open(path);
        if (input != null) {
          return input;
        }
      } catch (IOException e) {
        // A provider may use an I/O exception to represent a missing path.
      }
    }
    return null;
  }

  @Override
  public Stream<String> walk(String relativeDir) throws IOException {
    Set<String> paths = new LinkedHashSet<>();
    for (Resource provider : providers) {
      try (Stream<String> sourcePaths = provider.walk(relativeDir)) {
        sourcePaths.forEach(paths::add);
      }
    }
    return paths.stream();
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    for (Resource provider : providers) {
      try {
        provider.close();
      } catch (IOException e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
