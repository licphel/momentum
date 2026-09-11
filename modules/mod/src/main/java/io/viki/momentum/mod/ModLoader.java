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

package io.viki.momentum.mod;

import io.viki.momentum.event.EventBus;
import io.viki.momentum.math.util.Mutil;
import io.viki.momentum.util.Namespace;
import io.viki.momentum.util.SemanticVersion;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads mod JARs, manages the mod registry, and resolves dependency order.
 *
 * <p>Each loader instance maintains its own set of loaded mods and a shared
 * global {@link EventBus}.
 *
 * <p>Loading is not thread-safe — call {@code load} and {@code freeze} from
 * a single coordinating thread. Reads ({@code get}, {@code all}, {@code forClass}) may be called concurrently.
 *
 * @see ModInstance
 * @see Mod
 */
public final class ModLoader {
  private final Map<String, ModInstance> insts = new ConcurrentHashMap<>();
  private final Map<ClassLoader, ModInstance> classLoaderMap = new ConcurrentHashMap<>();
  private final EventBus globalEventBus = new EventBus();
  private @Nullable ModInstance core;

  private static URLClassLoader createClassLoader(Path jarPath) {
    try {
      return new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, Thread.currentThread().getContextClassLoader());
    } catch (IOException e) {
      throw new ModException("Failed to create class loader for: " + jarPath, e);
    }
  }

  /**
   * Finds the single annotated mod main class in a JAR.
   *
   * @param jarPath     the mod JAR to inspect
   * @param classLoader the class loader that can resolve the JAR's classes
   * @return the concrete class implementing {@link Mod}
   * @throws ModException if no valid entrypoint exists or multiple entrypoints are declared
   */
  private static Class<? extends Mod> findEntrypoint(Path jarPath, URLClassLoader classLoader) {
    List<String> classNames;
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      classNames = jar.stream()
          .map(JarEntry::getName)
          .filter(name -> name.endsWith(".class"))
          .filter(name -> !name.startsWith("META-INF/"))
          .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
          .filter(name -> !name.equals("module-info") && !name.endsWith(".package-info"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new ModException("Failed to scan classes in mod JAR: " + jarPath, e);
    }

    Class<? extends Mod> entrypoint = null;
    for (String className : classNames) {
      Class<?> clazz;
      try {
        clazz = Class.forName(className, false, classLoader);
      } catch (ClassNotFoundException | LinkageError ignored) {
        continue;
      }

      if (clazz.getAnnotation(Entrypoint.class) == null) {
        continue;
      }
      if (!Mod.class.isAssignableFrom(clazz)) {
        throw new ModException("Entrypoint class '" + className + "' in mod JAR '" + jarPath
            + "' must implement " + Mod.class.getName());
      }
      if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
        throw new ModException("Entrypoint class '" + className + "' in mod JAR '" + jarPath
            + "' must be a concrete class");
      }
      if (entrypoint != null) {
        throw new ModException("Mod JAR '" + jarPath + "' declares multiple @Entrypoint classes: "
            + entrypoint.getName() + " and " + className);
      }

      @SuppressWarnings("unchecked") Class<? extends Mod> modClass = (Class<? extends Mod>) clazz;
      entrypoint = modClass;
    }
    if (entrypoint == null) {
      throw new ModException("No class annotated with @Entrypoint found in mod JAR: " + jarPath);
    }
    return entrypoint;
  }

  /**
   * Validates the metadata contract exposed by a mod main class.
   *
   * @param mod     the mod main class instance
   * @param jarPath the JAR that supplied the mod
   * @throws ModException if the mod identifier or a dependency identifier is blank
   */
  private static void validateMod(Mod mod, Path jarPath) {
    if (mod.modId().isBlank()) {
      throw new ModException("Mod main class '" + mod.getClass().getName() + "' in JAR '" + jarPath
          + "' returned a blank mod identifier");
    }
    for (Dependency dependency : mod.dependencies()) {
      if (dependency.modId().isBlank()) {
        throw new ModException("Mod '" + mod.modId() + "' in JAR '" + jarPath
            + "' declares a dependency with a blank identifier");
      }
    }
  }

  /**
   * Returns the global event bus shared by all mods in this loader.
   *
   * @return the global event bus
   */
  public EventBus globalEventBus() {
    return globalEventBus;
  }

  /**
   * Returns the mod with the given ID, or {@code null} if not loaded.
   *
   * @param modId the mod identifier
   * @return the mod, or {@code null}
   */
  @SuppressWarnings("all")
  public @Nullable ModInstance get(String modId) {
    return insts.getOrDefault(modId, null);
  }

  /**
   * Returns all currently loaded mods.
   *
   * @return an unmodifiable collection of mods
   */
  public Collection<ModInstance> all() {
    return List.copyOf(insts.values());
  }

  /**
   * Returns the first core mod loaded, or {@code null} if none.
   *
   * @return the single core mod, or {@code null}
   */
  public @Nullable ModInstance coreMod() {
    return core;
  }

  /**
   * Returns the mod that loaded the given class, or {@code null} if the class was not loaded from any mod's
   * {@link ClassLoader}.
   *
   * @param clazz the class to look up
   * @return the owning mod, or {@code null}
   */
  public @Nullable ModInstance forClass(Class<?> clazz) {
    ClassLoader loader = clazz.getClassLoader();
    if (loader == null) {
      return null;
    }
    return classLoaderMap.get(loader);
  }

  /**
   * Loads a single mod from the given JAR file.
   *
   * <p>The JAR is scanned for one class annotated with {@link Entrypoint}. That
   * class must implement {@link Mod}; it supplies the mod metadata and is loaded
   * from the same JAR via a {@link URLClassLoader}. {@code @Subscribe} methods
   * on the entrypoint are registered on both the mod's private event bus and the
   * global event bus.
   *
   * @param jarPath path to the mod JAR file
   * @return the loaded mod
   * @throws ModException if the JAR is missing, no valid entrypoint is found, or a mod with the same ID is already
   *                      loaded
   */
  public ModInstance load(Path jarPath) {
    if (!Files.isRegularFile(jarPath)) {
      throw new ModException("Mod JAR not found: " + jarPath);
    }

    URLClassLoader classLoader = createClassLoader(jarPath);

    try {
      Class<? extends Mod> entrypointClass = findEntrypoint(jarPath, classLoader);
      Mod result;
      try {
        result = entrypointClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new ModException("Failed to instantiate entrypoint '" + entrypointClass.getName()
            + "'", e);
      }
      Mod entrypoint = result;
      validateMod(entrypoint, jarPath);

      String modId = entrypoint.modId();
      if (insts.containsKey(modId)) {
        throw new ModException("Mod with id '" + modId + "' is already loaded");
      }

      Namespace namespace = Namespace.of(modId);
      boolean isCoreMod = entrypoint.isCoreMod();
      if (isCoreMod && core != null) {
        throw new ModException("Core mod " + core.namespace() + " has already been loaded");
      }

      ModInstance mod = new ModInstance(namespace, entrypoint, jarPath, classLoader);
      insts.put(modId, mod);
      classLoaderMap.put(classLoader, mod);

      if (isCoreMod) {
        /*
         * There will be only ONE bottom core,
         * serving as the application's core logic supplier.
         * All other mods are expected to be built on it.
         */
        core = mod;
      }

      return mod;
    } catch (RuntimeException e) {
      try {
        classLoader.close();
      } catch (IOException closeException) {
        e.addSuppressed(new ModException("Failed to close class loader for JAR: " + jarPath, closeException));
      }
      throw e;
    }
  }

  /**
   * Loads all mods from the given directory by scanning for {@code .jar} files.
   *
   * <p>Mods are loaded in file-name order. Call {@link #freeze()} after
   * loading to obtain a dependency-sorted load order.
   *
   * @param modsDir the directory containing mod JAR files
   * @return the list of loaded mods, in discovery order
   * @throws UncheckedIOException if an I/O error occurs while scanning
   */
  public List<ModInstance> loadDirectory(Path modsDir) {
    if (!Files.isDirectory(modsDir)) {
      return List.of();
    }

    List<ModInstance> loaded = new ArrayList<>();
    try (Stream<Path> entries = Files.list(modsDir)) {
      List<Path> jars =
          entries.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
      for (Path jar : jars) {
        loaded.add(load(jar));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list mods directory: " + modsDir, e);
    }

    return loaded;
  }

  /**
   * Topologically sorts all loaded mods by their dependency graph.
   *
   * @return the sorted list of mods in dependency-respecting load order
   * @throws ModException if a dependency cycle is detected or a required dependency is missing
   */
  @SuppressWarnings("all")
  public List<ModInstance> freeze() {
    Map<String, ModInstance> insts = new HashMap<>(this.insts);

    for (Map.Entry<String, ModInstance> entry : insts.entrySet()) {
      for (Dependency dep : entry.getValue().mod().dependencies()) {
        ModInstance inst = insts.getOrDefault(dep.modId(), null);
        SemanticVersion detectedVer = inst.mod().version();
        if (inst == null || !dep.isSatisfiedBy(detectedVer)) {
          throw dep.createNotSatisfiedException(inst.mod().modId(), detectedVer);
        }
      }
    }

    try {
      final Function<String, List<String>> depSolver =
          id -> Arrays.stream(insts.get(id).mod().dependencies()).map(Dependency::modId).toList();
      return Mutil.topologicalSort(insts.keySet(), depSolver).stream().map(insts::get).toList();
    } catch (IllegalStateException exception) {
      throw new ModException("Dependency cycle detected among mods", exception);
    }
  }
}
