package io.viki.momentum.resource;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * A {@link Resource} backed by the classpath using ClassLoader.
 *
 * <p>This provider uses the specified classloader to locate resources via
 * {@link ClassLoader#getResourceAsStream(String)}. It works identically in
 * both development (exploded classes) and production (JAR) environments.
 *
 * <p>Paths are relative to the classpath root. For example, to access
 * {@code /META-INF/MANIFEST.MF}, use {@code "META-INF/MANIFEST.MF"}.
 */
public final class ClasspathResource implements Resource {
  private final ClassLoader classLoader;
  private final @Nullable Path codeSource;

  /** Creates a provider using the current thread context class loader. */
  public ClasspathResource() {
    this(Thread.currentThread().getContextClassLoader(), null);
  }

  /**
   * Creates a provider without a defining-class code-source fallback.
   *
   * @param classLoader the source classloader
   */
  public ClasspathResource(ClassLoader classLoader) {
    this(classLoader, null);
  }

  /**
   * Creates a provider associated with a defining class.
   *
   * @param caller the source class
   */
  public ClasspathResource(Class<?> caller) {
    this(caller.getClassLoader(), codeSource(caller));
  }

  private ClasspathResource(ClassLoader classLoader, @Nullable Path codeSource) {
    this.classLoader = classLoader;
    this.codeSource = codeSource;
  }

  @Override
  public @Nullable InputStream open(String path) throws IOException {
    String normalized = Resource.normalizePath(path);
    if (codeSource != null) {
      Resource source = Files.isRegularFile(codeSource)
          ? Resource.jar(codeSource) : Resource.directory(codeSource);
      if (source.exists(normalized)) {
        return source.open(normalized);
      }
    }
    return classLoader.getResourceAsStream(normalized);
  }

  @Override
  public Stream<String> walk(String relativeDir) throws IOException {
    String directory = Resource.normalizePath(relativeDir);
    List<String> paths = new ArrayList<>();
    if (codeSource != null && Files.isRegularFile(codeSource)) {
      collectJar(codeSource, directory, paths);
      return paths.stream().distinct().sorted();
    }
    Enumeration<URL> roots = classLoader.getResources(directory);
    while (roots.hasMoreElements()) {
      collect(roots.nextElement(), directory, paths);
    }
    return paths.stream().distinct().sorted();
  }

  private static void collect(URL root, String directory, List<String> paths) throws IOException {
    switch (root.getProtocol()) {
      case "file" -> collectDirectory(root, directory, paths);
      case "jar" -> {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        try (JarFile jar = connection.getJarFile()) {
          collectJar(jar, directory, paths);
        }
      }
      default -> throw new IOException("Unknown protocol: " + root.getProtocol());
    }
  }

  private static void collectDirectory(URL root, String directory, List<String> paths) throws IOException {
    try {
      Path base = Path.of(root.toURI());
      try (Stream<Path> entries = Files.walk(base)) {
        entries.filter(Files::isRegularFile)
            .map(base::relativize)
            .map(Path::toString)
            .map(Resource::normalizePath)
            .map(path -> directory.isEmpty() ? path : directory + "/" + path)
            .forEach(paths::add);
      }
    } catch (URISyntaxException exception) {
      throw new IOException("Invalid classpath directory URL: " + root, exception);
    }
  }

  private static void collectJar(Path jarPath, String directory, List<String> paths) throws IOException {
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      collectJar(jar, directory, paths);
    }
  }

  private static void collectJar(JarFile jar, String directory, List<String> paths) {
    String prefix = directory.isEmpty() ? "" : directory + "/";
    jar.stream().filter(entry -> !entry.isDirectory())
        .map(JarEntry::getName)
        .filter(path -> prefix.isEmpty() || path.startsWith(prefix))
        .forEach(paths::add);
  }

  private static @Nullable Path codeSource(Class<?> caller) {
    try {
      URL location = caller.getProtectionDomain().getCodeSource().getLocation();
      if (!location.getProtocol().equals("file")) {
        return null;
      }
      return Path.of(new URI(location.toString())).toAbsolutePath().normalize();
    } catch (RuntimeException | URISyntaxException exception) {
      return null;
    }
  }
}
