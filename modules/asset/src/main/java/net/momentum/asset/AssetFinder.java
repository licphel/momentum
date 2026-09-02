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

package net.momentum.asset;

import net.momentum.util.Identifier;
import net.momentum.util.Namespace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates and streams resources, addressing every resource as a {@link Path}.
 *
 * <p>A finder maps each namespace to the root path its resources live under:
 * an ordinary directory in development or a directory inside an opened
 * {@code jar:} {@link java.nio.file.FileSystem} once resources are packaged,
 * so the same code path streams both. Implementations that open file systems
 * own them and must release them in {@link #close()}.
 *
 * <p>Resource paths use {@code '/'} separators and contain only ASCII
 * characters (asset paths are, by convention, restricted to such a set).
 */
public abstract class AssetFinder implements AutoCloseable {
  /**
   * Returns the root path of the given namespace's resources.
   *
   * @param namespace the resource namespace
   * @return the root path for the namespace
   */
  public abstract Path base(Namespace namespace);

  /**
   * Returns a path of the given identifier.
   *
   * @param id the resource identifier
   * @return a path for the resource
   */
  public Path toPath(Identifier id) {
    return base(id.namespace()).resolve(standardizePath(id.path()));
  }

  /**
   * Opens the resource identified by the given identifier.
   *
   * <p>The resource is read from {@code <base>/<path>} where the base comes
   * from {@link #base(Namespace)}. Callers close the returned stream.
   *
   * @param id the resource identifier
   * @return an input stream for the resource
   * @throws IOException if the resource cannot be opened
   */
  public InputStream open(Identifier id) throws IOException {
    return Files.newInputStream(toPath(id));
  }

  /**
   * Releases file systems opened by this finder.
   *
   * <p>The default implementation does nothing; implementations that open
   * {@code jar:} file systems override this and must release them. Paths and
   * streams obtained beforehand become invalid.
   */
  @Override
  public void close() {
  }

  /**
   * Normalizes a resource path: converts separators to {@code '/'} and drops
   * leading slashes, so the result is always relative to a base root.
   *
   * @param path the raw path, e.g. {@code "\assets\foo.png"}
   * @return the relative path, e.g. {@code "assets/foo.png"}
   */
  protected static String standardizePath(String path) {
    String p = path.replace('\\', '/');
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    return p;
  }
}
