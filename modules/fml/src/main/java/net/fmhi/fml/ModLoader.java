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

import net.fmhi.fml.event.EventBus;
import net.fmhi.fml.event.Subscribe;
import net.fmhi.fml.resource.ResourceFinder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
 * @see Mod
 * @see ModInfo
 */
public final class ModLoader {
  private final Map<String, Mod> mods = new ConcurrentHashMap<>();
  private final Map<ClassLoader, Mod> classLoaderMap = new ConcurrentHashMap<>();
  private final EventBus globalEventBus = new EventBus();
  private final ResourceFinder resourceFinder = new ResourceFinder(this);
  private @Nullable Mod core;

  private static URLClassLoader createClassLoader(Path jarPath) {
    try {
      return new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, Thread.currentThread().getContextClassLoader());
    } catch (IOException e) {
      throw new ModException("Failed to create class loader for: " + jarPath, e);
    }
  }

  private static Object loadEntrypoint(URLClassLoader classLoader, ModInfo info) {
    try {
      Class<?> clazz = classLoader.loadClass(info.entrypoint());
      return clazz.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new ModException("Failed to load entrypoint '" + info.entrypoint() + "' for mod '" + info.modId() + "'", e);
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
   * Returns the mod loader resource finder.
   *
   * @return the mod loader resource finder
   */
  public ResourceFinder resourceFinder() {
    return resourceFinder;
  }

  /**
   * Returns the mod with the given ID, or {@code null} if not loaded.
   *
   * @param modId the mod identifier
   * @return the mod, or {@code null}
   */
  @SuppressWarnings("all")
  public @Nullable Mod get(String modId) {
    return mods.getOrDefault(modId, null);
  }

  /**
   * Returns all currently loaded mods.
   *
   * @return an unmodifiable collection of mods
   */
  public Collection<Mod> all() {
    return List.copyOf(mods.values());
  }

  /**
   * Returns the first core mod loaded, or {@code null} if none.
   *
   * @return the single core mod, or {@code null}
   */
  public @Nullable Mod coreMod() {
    return core;
  }

  /**
   * Returns the mod that loaded the given class, or {@code null} if the class was not loaded from any mod's
   * {@link ClassLoader}.
   *
   * @param clazz the class to look up
   * @return the owning mod, or {@code null}
   */
  public @Nullable Mod forClass(Class<?> clazz) {
    ClassLoader loader = clazz.getClassLoader();
    if (loader == null) {
      return null;
    }
    return classLoaderMap.get(loader);
  }

  /**
   * Loads a single mod from the given JAR file.
   *
   * <p>The JAR must contain {@code mod.json} at its root. The entrypoint
   * class (if declared) is loaded from the same JAR via a {@link URLClassLoader}. {@code @Subscribe} methods on the
   * entrypoint are registered on both the mod's private event bus and the global event bus.
   *
   * @param jarPath path to the mod JAR file
   * @return the loaded mod
   * @throws ModException if the JAR is missing, mod.json is invalid, or a mod with the same ID is already loaded
   */
  public Mod load(Path jarPath) {
    if (!Files.exists(jarPath)) {
      throw new ModException("Mod JAR not found: " + jarPath);
    }

    String json;
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      JarEntry entry = jar.getJarEntry("mod.json");
      if (entry == null) {
        throw new ModException("No mod.json in JAR: " + jarPath);
      }
      json = new String(jar.getInputStream(entry).readAllBytes());
    } catch (IOException e) {
      throw new ModException("Failed to read mod.json from JAR: " + jarPath, e);
    }

    ModInfo info = ModInfo.fromJson(json);
    String modId = info.modId();

    if (mods.containsKey(modId)) {
      throw new ModException("Mod with id '" + modId + "' is already loaded");
    }

    Namespace namespace = Namespace.of(modId);

    Object entrypoint = null;
    URLClassLoader classLoader = null;
    if (info.hasProgram()) {
      classLoader = createClassLoader(jarPath);
      entrypoint = loadEntrypoint(classLoader, info);
    }

    Mod mod = new Mod(namespace, info, jarPath, entrypoint, classLoader);
    mods.put(modId, mod);
    if (classLoader != null) {
      classLoaderMap.put(classLoader, mod);
    }

    if (info.isCoreMod()) {
      /*
       * There will be only ONE bottom core,
       * serving as the application's core logic supplier.
       * All other mods are expected to be built on it.
       */
      if (core != null) {
        throw new ModException("Core mod " + core.namespace() + " has already been loaded");
      }
      core = mod;
    }

    if (entrypoint instanceof ModInitializer init) {
      init.onPreLoad();
    }

    if (entrypoint != null) {
      scanSubscribers(mod, entrypoint);
    }

    return mod;
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
  public List<Mod> loadDirectory(Path modsDir) {
    if (!Files.isDirectory(modsDir)) {
      return List.of();
    }

    List<Mod> loaded = new ArrayList<>();
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
   * Topologically sorts all loaded mods by their dependency graph using Kahn's algorithm.
   *
   * <p>After freezing, each entrypoint that implements {@link ModInitializer}
   * has its {@code onPostLoad()} called in the sorted order.
   *
   * @return the sorted list of mods in dependency-respecting load order
   * @throws ModException if a dependency cycle is detected or a required dependency is missing
   */
  public List<Mod> freeze() {
    Map<String, Mod> modMap = new HashMap<>(mods);
    Map<String, Integer> inDegree = new HashMap<>();
    Map<String, List<String>> adjacency = new HashMap<>();

    for (Mod mod : modMap.values()) {
      String id = mod.info().modId();
      inDegree.putIfAbsent(id, 0);
      for (Dependency dep : mod.info().dependencies()) {
        if (!modMap.containsKey(dep.modId())) {
          throw new ModException("Mod '" + id + "' depends on '" + dep.modId() + "', which is not loaded");
        }
        adjacency.computeIfAbsent(dep.modId(), k -> new ArrayList<>()).add(id);
        inDegree.merge(id, 1, Integer::sum);
      }
    }

    Deque<String> queue = new ArrayDeque<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    List<Mod> sorted = new ArrayList<>();
    while (!queue.isEmpty()) {
      String current = queue.poll();
      Mod mod = modMap.get(current);
      if (mod != null) {
        sorted.add(mod);
      }
      for (String neighbor : adjacency.getOrDefault(current, List.of())) {
        int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
        if (newDegree == 0) {
          queue.add(neighbor);
        }
      }
    }

    if (sorted.size() != modMap.size()) {
      throw new ModException("Dependency cycle detected among mods");
    }

    for (Mod mod : sorted) {
      if (mod.entrypoint() instanceof ModInitializer init) {
        init.onPostLoad();
      }
    }

    return List.copyOf(sorted);
  }

  private void scanSubscribers(Mod mod, Object entrypoint) {
    Class<?> clazz = entrypoint.getClass();
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.getAnnotation(Subscribe.class) == null) {
        continue;
      }
      if (Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      mod.eventBus().register(entrypoint);
      globalEventBus.register(entrypoint);
      return;
    }
  }
}
