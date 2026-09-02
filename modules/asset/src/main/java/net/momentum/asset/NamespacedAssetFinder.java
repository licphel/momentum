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

import net.momentum.util.Namespace;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AssetFinder} backed by an explicit namespace-to-base mapping.
 *
 * <p>Each registered namespace maps to a base {@link Path}: a directory, or
 * a JAR file whose entries are exposed as a directory tree of an opened
 * {@code jar:} {@link FileSystem}. JARs are opened lazily and cached by
 * path, so a finder shared by several loaders opens each archive once;
 * {@link #close()} releases them all.
 *
 * <p>Namespace registrations are thread-safe; reads may happen from any
 * thread.
 */
public final class NamespacedAssetFinder extends AssetFinder {
  private final Map<String, Path> bases = new ConcurrentHashMap<>();
  private final Map<Path, FileSystem> archives = new HashMap<>();

  /**
   * Creates a finder with the given namespace-to-base mapping.
   *
   * @param bases the namespace name to base path mapping
   */
  public NamespacedAssetFinder(Map<String, Path> bases) {
    this.bases.putAll(bases);
  }

  /**
   * Creates an empty finder; namespaces are added later via
   * {@link #register(String, Path)}.
   */
  public NamespacedAssetFinder() {
  }

  /**
   * Registers (or replaces) the base of the given namespace.
   *
   * @param namespace the namespace name, e.g. a mod ID
   * @param base      the base directory or JAR file of the namespace's resources
   */
  public void register(String namespace, Path base) {
    bases.put(namespace, base);
  }

  /**
   * Returns the root of the namespace's resources: its registered base when
   * that is a directory, otherwise the {@code /} directory of the lazily
   * opened JAR file system.
   *
   * @param namespace the resource namespace
   * @return the namespace's root path
   * @throws AssetException if no base is registered or the archive cannot be opened
   */
  @Override
  public Path base(Namespace namespace) {
    Path configured = bases.get(namespace.name());
    if (configured == null) {
      throw new AssetException("No asset base registered for namespace '" + namespace + "'");
    }
    if (Files.isDirectory(configured)) {
      return configured;
    }
    FileSystem result;
    Path key = configured.toAbsolutePath().normalize();
    synchronized (archives) {
      result = archives.computeIfAbsent(key, j -> {
        try {
          return FileSystems.newFileSystem(j);
        } catch (IOException e) {
          throw new AssetException("Failed to open archive: " + j, e);
        }
      });
    }
    return result.getPath("/");
  }

  /**
   * Closes all JAR file systems opened by this finder.
   *
   * <p>Paths and streams obtained from this finder become invalid.
   */
  @Override
  public void close() {
    IOException failure = null;
    synchronized (archives) {
      for (FileSystem fs : archives.values()) {
        try {
          fs.close();
        } catch (IOException e) {
          failure = e;
        }
      }
      archives.clear();
    }
    if (failure != null) {
      throw new AssetException("Failed to close an archive", failure);
    }
  }
}
