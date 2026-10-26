package io.viki.momentum.codec.nbt;

/**
 * Persistent state of an existing object. Restoration retains the object's identity;
 * callers must obey its owning thread's mutation rules. This contract adds no synchronization
 * and does not define session-local network encoding.
 */
public interface NBTSerializable {
  /**
   * Encodes this object's persistent state as a compound tag.
   *
   * @return a compound representation suitable for persistent storage
   */
  CompoundNBT serialize();

  /**
   * Restores this object's state from a previously encoded compound tag while retaining its identity.
   *
   * @param data the compound tag containing the persistent state
   */
  void deserialize(CompoundNBT data);
}
