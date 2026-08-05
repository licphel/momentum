package net.fmhi.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A registry that maps abstract service types to their concrete implementations.
 *
 * <p>Backend modules ({@code fmhi-opengl}, {@code fmhi-openal}, {@code fmhi-glfw})
 * register their implementations via {@link #register(Class, Class)} at class-load time.
 * Callers obtain instances via {@link #create(Class)}.
 *
 * <p>This class is thread-safe.
 *
 * @see Platform
 */
public class NativeLookup {
  private static final ConcurrentMap<Class<?>, Class<?>> LOOKUP = new ConcurrentHashMap<>();

  /**
   * Registers a concrete implementation for the given service type.
   *
   * <p>Typically called from a static initializer in the backend module's
   * implementation class. If an implementation is already registered for
   * {@code type}, the new registration is silently ignored.
   *
   * @param type the abstract service type
   * @param impl the concrete implementation class (must have a no-arg constructor)
   * @param <T>  the service type
   */
  public static <T> void register(Class<T> type, Class<? extends T> impl) {
    LOOKUP.putIfAbsent(type, impl);
  }

  /**
   * Creates a new instance of the concrete implementation registered for the given type.
   *
   * @param type the abstract service type to look up
   * @param <T>  the service type
   * @return a new instance of the registered implementation
   * @throws IllegalArgumentException if no implementation is registered for {@code type},
   *                                  or if instantiation fails
   */
  @SuppressWarnings("unchecked")
  public static <T> T create(Class<T> type) {
    Class<?> found = LOOKUP.get(type);
    if (found == null) {
      throw new IllegalArgumentException(
          "Class " + type.getName() + " has no lookup. " +
              "Ensure the backend module (fmhi-opengl/fmhi-openal/fmhi-glfw) is on the classpath.");
    }

    try {
      return (T) found.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
