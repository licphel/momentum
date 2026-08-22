package net.fmhi.registry;

import net.fmhi.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Mutable registry metadata attached to a registered object, recording the
 * {@link Identifier} and registration index assigned by its registry.
 *
 * <p>The values are filled in at registration time and remain unset until
 * then.
 */
public final class RegistryContext {
  private @Nullable Identifier id;
  private @Nullable Integer index;

  /**
   * Stores the identifier assigned to this object by the registry.
   *
   * @param id the identifier to store
   */
  public void putId(Identifier id) {
    this.id = id;
  }

  /**
   * Stores the position at which this object was registered.
   *
   * @param index the registration index to store
   */
  public void putIndex(int index) {
    this.index = index;
  }

  /**
   * Returns the identifier assigned at registration.
   *
   * @return the identifier, or {@code null} if not registered yet
   */
  public @Nullable Identifier id() {
    return id;
  }

  /**
   * Returns the registration index assigned at registration.
   *
   * @return the index, or {@code null} if not registered yet
   */
  public @Nullable Integer index() {
    return index;
  }
}
