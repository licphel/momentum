package net.fmhi.util;

import net.fmhi.audio.Mixer;
import net.fmhi.audio.openal.OpenALMixer;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.View;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.opengl.OpenGLDevice;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A registry that maps abstract service types to their concrete default implementations.
 *
 * <p>Default mappings are registered at class initialization time based on the
 * current {@link Platform}. Use {@link #create(Class)} to instantiate the
 * implementation registered for a given type.
 *
 * <p>This class is thread-safe.
 *
 * @see Platform
 */
public class NativeLookup {
  private static final ConcurrentMap<Class<?>, Class<?>> LOOKUP = new ConcurrentHashMap<>();

  static {
    registerDefaults();
  }

  /**
   * Creates a new instance of the concrete implementation registered for the given type.
   *
   * @param type the abstract service type to look up
   * @param <T>  the service type
   * @return a new instance of the registered implementation
   * @throws IllegalArgumentException if no implementation is registered for {@code type},
   *         or if instantiation fails
   */
  @SuppressWarnings("unchecked")
  public static <T> T create(Class<T> type) {
    if (!LOOKUP.containsKey(type)) {
      throw new IllegalArgumentException("Class " + type.getName() + " has no lookup");
    }

    Class<?> found = LOOKUP.get(type);

    try {
      return (T) found.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static void registerDefaults() {
    switch (Platform.currentOS()) {
      case WINDOWS:
      case MACOS:
      case LINUX:
        LOOKUP.put(Device.class, OpenGLDevice.class);
        LOOKUP.put(Mixer.class, OpenALMixer.class);
        LOOKUP.put(View.class, GlfwView.class);
        break;
      default:
        throw new PlatformNotSupportedException("Platform " + Platform.currentOS() + " is not supported");
    }
  }
}
