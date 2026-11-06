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

package io.viki.momentum.crypto;

import org.jspecify.annotations.Nullable;
import io.viki.momentum.resource.ResourceWalkableClassLoader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads classes from a {@link SecurePack} when the parent cannot find them and resolves packed
 * resources before delegating to the parent. Instances are not thread-safe with {@link #close()}.
 */
public final class SecurePackClassLoader extends ClassLoader implements AutoCloseable, ResourceWalkableClassLoader {
  private final SecurePack pack;

  /**
   * Creates a loader backed by an authenticated protected package.
   *
   * @param packFile path to the protected package
   * @param key AES-256 key used to open the package
   * @param parent parent loader used for classes and resources not present in the package
   * @throws IllegalArgumentException if {@code key} has an invalid length
   * @throws CryptoException if the package cannot be opened or authenticated
   */
  public SecurePackClassLoader(Path packFile, byte[] key, ClassLoader parent) {
    super(parent);
    this.pack = SecurePack.open(packFile, key);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] bytes = pack.read(name.replace('.', '/') + ".class");
    if (bytes == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytes, 0, bytes.length);
  }

  /**
   * Returns a packed resource when present, otherwise delegates to the parent loader.
   *
   * @param name resource path to locate
   * @return a stream over the resource, or {@code null} if neither loader provides it
   * @throws CryptoException if a matching packed resource cannot be authenticated or decrypted
   */
  @Override
  public @Nullable InputStream getResourceAsStream(String name) {
    InputStream stream = pack.stream(name);
    return stream != null ? stream : super.getResourceAsStream(name);
  }

  @Override
  public Stream<String> walkResources(String directory) {
    return pack.walk(directory);
  }

  /** Closes the package and clears its owned key and decrypted entry data. */
  @Override
  public void close() {
    pack.close();
  }
}
