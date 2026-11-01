package io.viki.momentum.resource;

import java.util.stream.Stream;

/**
 * Directory enumeration for class loaders whose resources have no filesystem URL.
 * Returned paths are classpath-root-relative. Streams must be consumed before
 * the owning loader closes; concurrent reads are supported while it is open.
 */
public interface ResourceWalkableClassLoader {
  /**
   * Gets a resource list of the current directory.
   *
   * @param directory current dir
   * @return the children of current dir
   */
  Stream<String> walkResources(String directory);
}
