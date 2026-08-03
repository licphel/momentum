package net.fmhi.world.item;

import net.fmhi.fml.registry.RegistryContext;
import net.fmhi.fml.registry.RegistryEntry;

public class Item implements RegistryEntry<Item>, ItemLike {
  private final RegistryContext registryContext = new RegistryContext();

  @Override
  public RegistryContext getRegistryContext() {
    return registryContext;
  }
}
