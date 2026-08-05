package net.fmhi.fml.registry;

import net.fmhi.fml.Identifier;
import net.fmhi.fml.tag.Tag;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * An object that carries its own registry metadata, so it can know its
 * identifier and registration index without consulting the registry.
 *
 * @param <T> the concrete self type, i.e. the implementing class itself
 */
@NullMarked
public interface RegistryEntry<T> {
  /**
   * Returns the metadata holding this object's assigned identifier and index.
   *
   * @return the registry metadata
   */
  RegistryContext getRegistryContext();

  /**
   * Returns the identifier this object is registered under.
   *
   * @return the identifier
   * @throws NullPointerException if this object has not been registered yet
   */
  default Identifier registryId() {
    return Objects.requireNonNull(getRegistryContext().id());
  }

  /**
   * Returns the index at which this object was registered.
   *
   * @return the registration index
   * @throws NullPointerException if this object has not been registered yet
   */
  default int registryIndex() {
    return Objects.requireNonNull(getRegistryContext().index());
  }

  /**
   * Tests whether this object belongs to the given tag.
   *
   * @param tag the tag to test membership in
   * @return {@code true} if the tag contains this object
   */
  @SuppressWarnings("unchecked")
  default boolean is(Tag<T> tag) {
    return tag.contains((T) this);
  }

  /**
   * Tests whether this object is the given instance.
   *
   * @param t the instance to compare against
   * @return {@code true} if both refer to the same object
   */
  default boolean is(T t) {
    return this == t;
  }
}
