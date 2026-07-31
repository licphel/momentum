package net.fmhi.world.block;

import net.fmhi.Registries;
import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.world.physics.SBPhyObj;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.physics.PhysicsObject;
import net.fmhi.world.physics.Polygon;
import org.jspecify.annotations.Nullable;

public final class BlockState extends BlockStateHolder {
  public static BlockState EMPTY;
  private final Block block;

  BlockState(Block block, ImmutablePropertyMap propertyMap) {
    super(propertyMap);
    this.block = block;
  }

  public Block block() {
    return block;
  }

  public @Nullable Polygon getPhysicsShape(BlockPos pos, SBPhyObj obj) {
    return block.getPhysicsShape(this, pos, obj);
  }
}
