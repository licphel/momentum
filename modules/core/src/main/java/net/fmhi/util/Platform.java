package net.fmhi.util;

/**
 * Identifies the operating system on which the application is running.
 *
 * <p>Detection is based on the {@code os.name} system property and considers
 * only the OS family, not the underlying architecture.
 *
 * @see NativeLookup
 */
public enum Platform {
  /** Microsoft Windows. */
  WINDOWS,
  /** Apple macOS. */
  MACOS,
  /** Desktop Linux distributions. */
  LINUX,
  /** Google Android. */
  ANDROID,
  /** Apple iOS. */
  IOS,
  /** An unrecognized operating system. */
  OTHER;

  /**
   * Returns the current operating system detected at runtime.
   *
   * @return the current platform; never {@code null}
   */
  public static Platform currentOS() {
    String OS_NAME = System.getProperty("os.name").toLowerCase();

    if (OS_NAME.contains("win")) {
      return WINDOWS;
    }
    if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
      return MACOS;
    }
    if (OS_NAME.contains("linux")) {
      return LINUX;
    }
    if (OS_NAME.contains("android")) {
      return ANDROID;
    }
    if (OS_NAME.contains("ios")) {
      return IOS;
    }
    return OTHER;
  }
}
