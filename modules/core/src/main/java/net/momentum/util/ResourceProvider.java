package net.momentum.util;

import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides access to application resources from various sources.
 *
 * <p>This interface serves as an abstraction for resource loading, allowing
 * resources to be retrieved from the classpath, file system, or other custom
 * sources. It provides convenience methods for reading resources as strings
 * or byte arrays.
 *
 * @see #classpath(Class)
 * @see #file(Path)
 * @see #file(String)
 * @see #combined(ResourceProvider...)
 */
@FunctionalInterface
public interface ResourceProvider {
  /**
   * Creates a {@code ResourceProvider} that loads resources from the classpath
   * relative to the given caller class.
   *
   * <p>The returned provider uses {@link Class#getResourceAsStream(String)}
   * to locate resources. Paths starting with {@code '/'} are resolved against
   * the classpath root; paths without a leading slash are resolved relative
   * to the caller's package.
   *
   * @param caller the class used to resolve classpath resources
   * @return a resource provider that loads from the classpath
   */
  static ResourceProvider classpath(Class<?> caller) {
    return caller::getResourceAsStream;
  }

  /**
   * Creates a {@code ResourceProvider} that loads resources from the file
   * system under the specified base path.
   *
   * <p>The returned provider resolves all paths relative to the base directory
   * and prevents path traversal attacks by ensuring resolved paths stay within
   * the base directory.
   *
   * @param basePath the base directory path
   * @return a resource provider that loads from the file system
   * @throws NullPointerException if {@code basePath} is null
   */
  static ResourceProvider file(Path basePath) {
    Path absolute = basePath.toAbsolutePath().normalize();
    return path -> {
      Path resolved = absolute.resolve(path).normalize();
      if (!resolved.startsWith(absolute)) {
        throw new IOException("Path traversal detected: " + path);
      }
      return Files.newInputStream(resolved);
    };
  }

  /**
   * Creates a {@code ResourceProvider} that loads resources from the file
   * system under the specified base path.
   *
   * <p>This is a convenience method equivalent to
   * {@code file(Paths.get(basePath))}.
   *
   * @param basePath the base directory path as a string
   * @return a resource provider that loads from the file system
   * @throws NullPointerException if {@code basePath} is null
   */
  static ResourceProvider file(String basePath) {
    return file(Paths.get(basePath));
  }

  /**
   * Creates a {@code ResourceProvider} that attempts to load resources from
   * multiple providers in order.
   *
   * <p>The returned provider iterates through the given providers and returns
   * the first non-null input stream found. If no provider returns a stream,
   * {@code null} is returned.
   *
   * @param providers the resource providers to chain, in order of preference
   * @return a combined resource provider
   * @throws NullPointerException if {@code providers} is null or contains null
   */
  static ResourceProvider combined(ResourceProvider... providers) {
    return path -> {
      for (ResourceProvider provider : providers) {
        InputStream in = null;
        try {
          in = provider.openStream(path);
        } catch (IOException e) {
          // Ignored
        }
        if (in != null) {
          return in;
        }
      }
      return null;
    };
  }

  /**
   * Opens an input stream for the given resource path.
   *
   * <p>Implementations should return {@code null} if the resource does not
   * exist, rather than throwing an exception, to support chained providers.
   * However, throwing an exception is acceptable for fatal errors such as
   * I/O errors or access violations.
   *
   * @param path the resource path (implementation-specific format)
   * @return an input stream, or {@code null} if the resource does not exist
   * @throws IOException          if an I/O error occurs while opening the stream
   * @throws NullPointerException if {@code path} is null
   */
  @Nullable InputStream openStream(String path) throws IOException;

  /**
   * Reads a resource as a UTF-8 string.
   *
   * <p>This is a convenience method that reads the entire resource content
   * into memory and decodes it as UTF-8. For large resources, consider using
   * {@link #openStream(String)} directly for streaming processing.
   *
   * @param path the resource path
   * @return the resource content as a UTF-8 string
   * @throws RuntimeException     if an I/O error occurs or the resource is not found
   * @throws NullPointerException if {@code path} is null
   * @see #readBytes(String)
   * @see #openStream(String)
   */
  default String readString(String path) {
    return new String(readBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Reads a resource as a byte array.
   *
   * <p>This is a convenience method that reads the entire resource content
   * into memory. For large resources, consider using {@link #openStream(String)}
   * directly for streaming processing.
   *
   * @param path the resource path
   * @return the resource content as a byte array
   * @throws RuntimeException     if an I/O error occurs or the resource is not found
   * @throws NullPointerException if {@code path} is null
   * @see #readString(String)
   * @see #openStream(String)
   */
  default byte[] readBytes(String path) {
    try (InputStream in = openStream(path)) {
      if (in == null) {
        throw new FileNotFoundException("Resource not found: " + path);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }

  /**
   * Checks whether a resource exists at the given path.
   *
   * <p>The default implementation attempts to open the stream and closes it
   * immediately. Subclasses may provide more efficient implementations.
   *
   * @param path the resource path
   * @return {@code true} if the resource exists, {@code false} otherwise
   * @throws NullPointerException if {@code path} is null
   */
  default boolean exists(String path) {
    try (InputStream in = openStream(path)) {
      return in != null;
    } catch (IOException e) {
      return false;
    }
  }
}