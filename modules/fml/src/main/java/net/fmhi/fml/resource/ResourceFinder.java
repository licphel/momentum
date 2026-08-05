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

package net.fmhi.fml.resource;

import net.fmhi.fml.Identifier;
import net.fmhi.fml.Mod;
import net.fmhi.fml.ModLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Locates and opens resources from mod JAR files via a {@link ModLoader}.
 *
 * <p>Resources are resolved at {@code <namespace>/<path>} inside the
 * owning mod's JAR.
 */
public final class ResourceFinder {
  private final ModLoader loader;

  /**
   * Creates a resource finder backed by the given mod loader.
   *
   * @param loader the mod loader to query for mod lookups
   */
  public ResourceFinder(ModLoader loader) {
    this.loader = loader;
  }

  /**
   * Returns the application root directory (the current working directory).
   *
   * @return the current working directory
   */
  public static Path getAppRoot() {
    return Path.of(System.getProperty("user.dir"));
  }

  /**
   * Opens a resource directly from a JAR at the given path.
   *
   * @param jarPath      the path to the JAR file
   * @param resourcePath the path of the resource inside the JAR
   * @return an input stream for the resource
   * @throws ResourceException if the resource is not found in the JAR
   */
  public static InputStream openFromJar(Path jarPath, String resourcePath) {
    try {
      JarFile jar = new JarFile(jarPath.toFile());
      JarEntry entry = jar.getJarEntry(resourcePath);
      if (entry == null) {
        throw new ResourceException("Resource '" + resourcePath + "' not found in " + jarPath.getFileName());
      }
      return jar.getInputStream(entry);
    } catch (IOException e) {
      throw new ResourceException("Failed to open resource '" + resourcePath + "' from " + jarPath.getFileName(), e);
    }
  }

  /**
   * Opens a resource from the mod JAR identified by the given identifier.
   *
   * <p>The resource is located at {@code assets/<namespace>/<path>} inside the
   * JAR of the mod whose ID matches the identifier's namespace.
   *
   * @param id the resource identifier (namespace = mod ID, path = resource path)
   * @return an input stream for the resource
   * @throws ResourceException if the mod is not loaded or the resource is not found
   */
  public InputStream open(Identifier id) {
    Mod mod = loader.get(id.namespace().name());
    if (mod == null) {
      throw new ResourceException("Mod not loaded: '" + id.namespace() + "'");
    }
    String resourcePath = id.namespace().name() + "/" + id.path();
    return openFromJar(mod.jarPath(), resourcePath);
  }

  /**
   * Opens a resource from the mod JAR that loaded the given class.
   *
   * <p>The resource is located at {@code assets/<namespace>/<path>} inside the
   * JAR, where the namespace is the mod ID of the mod that owns {@code owner}.
   *
   * @param owner a class loaded by the target mod's {@link java.net.URLClassLoader}
   * @param path  the resource path (without the {@code assets/<namespace>/} prefix)
   * @return an input stream for the resource
   * @throws ResourceException if the class does not belong to any loaded mod or the resource is not found
   */
  public InputStream open(Class<?> owner, String path) {
    Mod mod = loader.forClass(owner);
    if (mod == null) {
      throw new ResourceException("Class '" + owner.getName() + "' does not belong to any loaded mod");
    }
    String resourcePath = mod.namespace().name() + "/" + path;
    return openFromJar(mod.jarPath(), resourcePath);
  }
}
