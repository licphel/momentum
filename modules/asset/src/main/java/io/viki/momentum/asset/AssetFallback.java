package io.viki.momentum.asset;

import io.viki.momentum.util.Identifier;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for asset fallback values.
 *
 * <p>Fallbacks are returned by {@link Assets#getOrDefault(Identifier, Class)} when no
 * compatible asset is registered for the requested type. Typical use is a
 * placeholder asset — e.g. a checkerboard texture or a silent sound — so
 * consumers never have to special-case {@code null}.
 *
 * <p>This class is thread-safe.
 */
public final class AssetFallback {
  private static final ConcurrentHashMap<Class<?>, Object> STORE = new ConcurrentHashMap<>();

  private AssetFallback() {
  }

  /**
   * Registers a fallback value for the given type.
   *
   * @param type  the asset type the fallback covers
   * @param value the fallback value
   * @param <T>   the asset type
   */
  public static <T> void register(Class<T> type, T value) {
    STORE.put(type, value);
  }

  /**
   * Returns the fallback value registered for the given type.
   *
   * @param type the asset type
   * @param <T>  the asset type
   * @return the registered fallback
   * @throws NullPointerException if type fallback has not been registered
   */
  @SuppressWarnings("all")
  public static <T> T get(Class<T> type) {
    Object obj = STORE.getOrDefault(type, null);
    if  (obj == null) {
      throw new NullPointerException("Type fallback of " + type.getName() + " is not registered");
    }
    return type.cast(obj);
  }

  /**
   * Removes the fallback value for the given type.
   *
   * @param type the asset type
   */
  public static void remove(Class<?> type) {
    STORE.remove(type);
  }

  /**
   * Clears all registered fallbacks.
   */
  public static void clear() {
    STORE.clear();
  }

  /**
   * Returns whether a fallback is registered for the given type.
   *
   * @param type the asset type
   * @return {@code true} if a fallback is registered
   */
  public static boolean contains(Class<?> type) {
    return STORE.containsKey(type);
  }
}