package net.fmhi.world.block;

import net.fmhi.collection.Palette;
import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.property.Property;

import java.util.Iterator;
import java.util.Map;

public class BlockStateHolder implements ImmutablePropertyMap {
  public static final Palette<BlockState> BLOCK_STATE_PROPERTY_PALETTE = new Palette<>();

  private final ImmutablePropertyMap propertyMap;

  protected BlockStateHolder(ImmutablePropertyMap propertyMap) {
    this.propertyMap = propertyMap;
  }

  @Override
  public int identity() {
    return propertyMap.identity();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T, C extends ImmutablePropertyMap> C with(Property<T> property, T value) {
    return (C) BLOCK_STATE_PROPERTY_PALETTE.get(propertyMap.with(property, value).identity());
  }

  @Override
  public <T> T get(Property<T> property) {
    return propertyMap.get(property);
  }

  @Override
  public boolean has(Property<?> property) {
    return propertyMap.has(property);
  }

  @Override
  public Iterator<Map.Entry<Property<?>, Object>> iterator() {
    return propertyMap.iterator();
  }
}
