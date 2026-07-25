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

package net.fmhi.event;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A thread-safe event bus for dispatching {@link Event} instances to registered handlers.
 *
 * <h3>Registration</h3>
 * <p>Handlers can be registered in several ways:
 * <ul>
 *   <li><strong>Lambda:</strong> via {@link #register(Class, EventListener)}</li>
 *   <li><strong>Instance methods:</strong> via {@link #register(Object)}, which discovers
 *       {@link Subscribe @Subscribe}-annotated methods on the object</li>
 *   <li><strong>Static methods:</strong> via {@link #register(Class)} for a single class, or
 *       {@link #scan(String)} for classpath scanning of entire packages</li>
 * </ul>
 *
 * <h3>Dispatch</h3>
 * <p>Handlers run in priority order, from {@link Priority#HIGHEST} to {@link Priority#LOWEST}.
 * All handlers always receive the event — canceling an event does not stop propagation.
 * Handlers that wish to respect cancellation should check {@link EventContext#isCanceled()}
 * and return early.
 *
 * <p>Handlers registered for a supertype or interface also receive events of subtypes.
 *
 * <h3>Phases</h3>
 * <p>Use {@link #post(Event, Phase)} to post the same event type at different lifecycle
 * points. Handlers filter by phase via {@link Subscribe#phase()}.
 *
 * <p>This class is thread-safe. All public methods may be called concurrently.
 *
 * @see Event
 * @see EventContext
 * @see EventListener
 * @see Subscribe
 * @see Priority
 * @see Result
 */
public final class EventBus {
  private final Map<Class<?>, TreeMap<Integer, List<HandlerWrapper>>> handlers;
  private final Map<Class<?>, List<HandlerWrapper>> cache;
  private final ReentrantReadWriteLock lock;

  /**
   * Creates a new, empty event bus.
   */
  public EventBus() {
    this.handlers = new ConcurrentHashMap<>();
    this.cache = new ConcurrentHashMap<>();
    this.lock = new ReentrantReadWriteLock();
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends Event> getEventType(Method method) {
    return (Class<? extends Event>) method.getParameterTypes()[1];
  }

  private static void validateMethod(Method method) {
    Class<?>[] params = method.getParameterTypes();
    if (params.length != 2) {
      throw new IllegalArgumentException("@Subscribe method " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + " must have exactly two parameters " + "(EventContext, Event), got " + params.length);
    }
    if (!EventContext.class.isAssignableFrom(params[0])) {
      throw new IllegalArgumentException("@Subscribe method " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + " first parameter must be EventContext");
    }
    if (!Event.class.isAssignableFrom(params[1])) {
      throw new IllegalArgumentException("@Subscribe method " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + " second parameter must implement Event");
    }
  }

  /**
   * Registers all {@link Subscribe @Subscribe}-annotated static methods on the given class as event handlers.
   *
   * <p>Annotated methods must accept an {@link EventContext} as the first parameter and an
   * {@link Event} subtype as the second.
   *
   * @param clazz the class to scan for annotated static handler methods
   * @throws IllegalArgumentException if any annotated method has an invalid signature
   */
  public void register(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      Subscribe sub = method.getAnnotation(Subscribe.class);
      if (sub == null) {
        continue;
      }
      validateMethod(method);
      Class<? extends Event> eventType = getEventType(method);
      HandlerWrapper wrapper = new HandlerWrapper(method, null, sub.priority(), sub.phase());
      addHandler(eventType, wrapper);
    }
  }

  /**
   * Registers all {@link Subscribe @Subscribe}-annotated instance methods on the given object as event handlers.
   *
   * <p>Annotated methods must accept an {@link EventContext} as the first parameter and an
   * {@link Event} subtype as the second. Static methods are skipped. Handlers registered this way can be removed via
   * {@link #unregister(Object)}.
   *
   * @param instance the object whose annotated instance methods should be registered
   * @throws IllegalArgumentException if any annotated method has an invalid signature
   */
  public void register(Object instance) {
    Class<?> clazz = instance.getClass();
    for (Method method : clazz.getDeclaredMethods()) {
      Subscribe sub = method.getAnnotation(Subscribe.class);
      if (sub == null) {
        continue;
      }
      if (Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      validateMethod(method);
      Class<? extends Event> eventType = getEventType(method);
      HandlerWrapper wrapper = new HandlerWrapper(method, instance, sub.priority(), sub.phase());
      addHandler(eventType, wrapper);
    }
  }

  /**
   * Registers a listener for events of the given type at {@link Priority#NORMAL} matching all phases.
   *
   * @param eventType the event type to listen for
   * @param listener  the handler callback
   * @param <T>       the event type
   */
  public <T extends Event> void register(Class<T> eventType, EventListener<T> listener) {
    register(eventType, listener, Priority.NORMAL, Phase.NONE, null);
  }

  /**
   * Registers a listener for events of the given type with the specified priority and phase filter.
   *
   * @param eventType the event type to listen for
   * @param listener  the handler callback
   * @param priority  the handler priority; determines dispatch order
   * @param phase     the phase filter; {@link Phase#NONE} to match all phases
   * @param <T>       the event type
   */
  public <T extends Event> void register(Class<T> eventType, EventListener<T> listener, Priority priority,
                                         Phase phase) {
    register(eventType, listener, priority, phase, null);
  }

  /**
   * Registers a listener with an owner object that can later deregister all its handlers at once via
   * {@link #deregister(Object)}.
   *
   * @param eventType the event type to listen for
   * @param listener  the handler callback
   * @param owner     an object that identifies this registration group; use with {@link #deregister(Object)}
   * @param <T>       the event type
   */
  public <T extends Event> void register(Class<T> eventType, EventListener<T> listener, Object owner) {
    register(eventType, listener, Priority.NORMAL, Phase.NONE, owner);
  }

  /**
   * Registers a listener with explicit priority, phase, and owner.
   *
   * @param eventType the event type to listen for
   * @param listener  the handler callback
   * @param priority  the handler priority; determines dispatch order
   * @param phase     the phase filter; {@link Phase#NONE} to match all phases
   * @param owner     an object that identifies this registration group; use with {@link #deregister(Object)}
   * @param <T>       the event type
   */
  @SuppressWarnings("unchecked")
  public <T extends Event> void register(Class<T> eventType, EventListener<T> listener, Priority priority,
                                         Phase phase, @Nullable Object owner) {
    HandlerWrapper wrapper = new HandlerWrapper((EventListener<Event>) listener, priority, phase, owner);
    addHandler(eventType, wrapper);
  }

  /**
   * Removes all instance-method handlers that were registered via {@link #register(Object)} with the given instance.
   *
   * <p>Handlers registered via lambda or static method registration are not affected.
   *
   * @param instance the object whose handlers should be removed
   */
  public void unregister(Object instance) {
    lock.writeLock().lock();
    try {
      for (TreeMap<Integer, List<HandlerWrapper>> priorityMap : handlers.values()) {
        for (List<HandlerWrapper> list : priorityMap.values()) {
          list.removeIf(w -> w.owner == instance);
        }
      }
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Removes all handlers whose listener or owner matches the given reference.
   *
   * <p>When a listener was registered with an owner via
   * {@link #register(Class, EventListener, Object)}, passing that same owner removes all handlers in the group. When a
   * listener was registered without an owner, passing the listener itself removes it. For handlers registered via
   * {@link #register(Object)} (reflective discovery), use {@link #unregister(Object)} instead.
   *
   * @param token the listener or owner object to deregister
   */
  public void deregister(Object token) {
    lock.writeLock().lock();
    try {
      for (TreeMap<Integer, List<HandlerWrapper>> priorityMap : handlers.values()) {
        for (List<HandlerWrapper> list : priorityMap.values()) {
          list.removeIf(w -> w.listener == token || w.owner == token);
        }
      }
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Removes all registered handlers from this bus.
   */
  public void clear() {
    lock.writeLock().lock();
    try {
      handlers.clear();
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Scans the given package and its sub-packages for classes with {@link Subscribe @Subscribe}-annotated static methods
   * and registers them.
   *
   * <p>The scanner searches both directory-based and JAR-based classpath entries. Inner
   * classes are skipped.
   *
   * @param packageName the fully qualified package name to scan, e.g. {@code "com.example.mymod.events"}
   * @throws UncheckedIOException if an I/O error occurs while reading classpath entries
   */
  public void scan(String packageName) {
    String path = packageName.replace('.', '/');
    try {
      Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        String protocol = resource.getProtocol();
        if ("file".equals(protocol)) {
          scanDirectory(new File(resource.getFile()), packageName);
        } else if ("jar".equals(protocol)) {
          scanJar(resource, path, packageName);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to scan package: " + packageName, e);
    }
  }

  /**
   * Posts an event to this bus at {@link Phase#NONE}.
   *
   * @param event the event to dispatch
   * @param <T>   the event type
   * @return the dispatch context with final cancel and result state
   */
  public <T extends Event> EventContext<T> post(T event) {
    return post(event, Phase.NONE);
  }

  /**
   * Posts an event to this bus at the given lifecycle phase.
   *
   * <p>All matching handlers are invoked in priority order. Canceled events still
   * propagate to remaining handlers — handlers that wish to respect cancellation should check
   * {@link EventContext#isCanceled()} and return early.
   *
   * @param event the event to dispatch
   * @param phase the lifecycle phase for filtering handlers
   * @param <T>   the event type
   * @return the dispatch context with final cancel and result state
   */
  @SuppressWarnings("unchecked")
  public <T extends Event> EventContext<T> post(T event, Phase phase) {
    EventContext<T> ctx = new EventContext<>(event, phase);
    Class<? extends Event> eventType = event.getClass();
    List<HandlerWrapper> resolved = getHandlers(eventType);
    if (resolved.isEmpty()) {
      return ctx;
    }
    for (HandlerWrapper handler : resolved) {
      if (!handler.phase.matches(ctx.phase())) {
        continue;
      }
      handler.invoke((EventContext<Event>) ctx, event);
    }
    return ctx;
  }

  /**
   * Posts an event asynchronously at {@link Phase#NONE}.
   *
   * @param event the event to dispatch
   * @param <T>   the event type
   * @return a {@link CompletableFuture} that completes with the dispatch context
   */
  public <T extends Event> CompletableFuture<EventContext<T>> postAsync(T event) {
    return CompletableFuture.supplyAsync(() -> post(event));
  }

  /**
   * Posts an event asynchronously at the given lifecycle phase.
   *
   * @param event the event to dispatch
   * @param phase the lifecycle phase for filtering handlers
   * @param <T>   the event type
   * @return a {@link CompletableFuture} that completes with the dispatch context
   */
  public <T extends Event> CompletableFuture<EventContext<T>> postAsync(T event, Phase phase) {
    return CompletableFuture.supplyAsync(() -> post(event, phase));
  }

  private List<HandlerWrapper> getHandlers(Class<? extends Event> eventType) {
    List<HandlerWrapper> cached = cache.get(eventType);
    if (cached != null) {
      return cached;
    }

    lock.readLock().lock();
    try {
      List<HandlerWrapper> all = new ArrayList<>();
      Class<?> current = eventType;
      while (current != null && current != Object.class) {
        TreeMap<Integer, List<HandlerWrapper>> priorityMap = handlers.get(current);
        if (priorityMap != null) {
          for (List<HandlerWrapper> list : priorityMap.values()) {
            all.addAll(list);
          }
        }
        for (Class<?> iface : current.getInterfaces()) {
          if (Event.class.isAssignableFrom(iface)) {
            TreeMap<Integer, List<HandlerWrapper>> ifaceHandlers = handlers.get(iface);
            if (ifaceHandlers != null) {
              for (List<HandlerWrapper> list : ifaceHandlers.values()) {
                all.addAll(list);
              }
            }
          }
        }
        current = current.getSuperclass();
      }
      cache.put(eventType, all);
      return all;
    } finally {
      lock.readLock().unlock();
    }
  }

  private void addHandler(Class<?> eventType, HandlerWrapper wrapper) {
    lock.writeLock().lock();
    try {
      handlers.computeIfAbsent(eventType, k -> new TreeMap<>()).computeIfAbsent(wrapper.priority.ordinal(),
          k -> new ArrayList<>()).add(wrapper);
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void scanDirectory(File dir, String packageName) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      String name = file.getName();
      if (file.isDirectory()) {
        scanDirectory(file, packageName + "." + name);
      } else if (name.endsWith(".class") && !name.contains("$")) {
        String className = packageName + "." + name.substring(0, name.length() - 6);
        scanClass(className);
      }
    }
  }

  private void scanJar(URL jarUrl, String path, String packageName) {
    String jarPath = jarUrl.getPath();
    int sep = jarPath.indexOf("!/");
    if (sep < 0) {
      return;
    }
    String filePath = jarPath.substring(5, sep);
    try (JarFile jar = new JarFile(filePath)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!name.startsWith(path) || !name.endsWith(".class") || name.contains("$")) {
          continue;
        }
        String className = name.substring(0, name.length() - 6).replace('/', '.');
        scanClass(className);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to scan JAR: " + filePath, e);
    }
  }

  private void scanClass(String className) {
    try {
      Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getAnnotation(Subscribe.class) != null && Modifier.isStatic(method.getModifiers())) {
          register(clazz);
          return;
        }
      }
    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
      // Class cannot be loaded — skip it
    }
  }

  private static final class HandlerWrapper {
    final Priority priority;
    final Phase phase;
    final @Nullable Object owner;
    final @Nullable Method method;
    final @Nullable EventListener<Event> listener;

    HandlerWrapper(Method method, @Nullable Object owner, Priority priority, Phase phase) {
      this.method = method;
      this.owner = owner;
      this.priority = priority;
      this.phase = phase;
      this.listener = null;
      method.setAccessible(true);
    }

    HandlerWrapper(EventListener<Event> listener, Priority priority, Phase phase, @Nullable Object owner) {
      this.listener = listener;
      this.priority = priority;
      this.phase = phase;
      this.method = null;
      this.owner = owner;
    }

    void invoke(EventContext<Event> ctx, Event event) {
      try {
        if (method != null) {
          method.invoke(owner, ctx, event);
        } else if (listener != null) {
          listener.handle(ctx, event);
        }
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException("Failed to invoke event handler for " + event.getClass().getSimpleName(), e);
      }
    }
  }
}
