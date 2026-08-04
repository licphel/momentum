package net.fmhi.world.block;

import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.world.light.Beam;
import net.fmhi.world.physics.CollisionKind;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.physics.SBPhyObj;
import net.fmhi.world.physics.VoxelClip;
import net.fmhi.world.util.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A concrete state of a block type, delegating behavior to its {@link Block}.
 */
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

  /** How this block fills its tile (collision, light and liquid rules). */
  public Shape shape() {
    return block.shape(this);
  }

  /** Collision kind for the physics engine. */
  public CollisionKind collisionKind() {
    return block.collisionKind();
  }

  /** Restitution: 0 = no bounce, 1 = perfect. */
  public float bounce() {
    return block.bounce();
  }

  /** Friction: 0 = ice, 1 = rough. */
  public float friction() {
    return block.friction();
  }

  /** The collision polygon of this state, or {@code null} if none. */
  public @Nullable Polygon getPhysicsShape(BlockPos pos, SBPhyObj obj) {
    return block.getPhysicsShape(this, pos, obj);
  }

  /** The collision shape used by the physics engine. */
  public VoxelClip getVoxelShape() {
    return block.getVoxelShape(this);
  }

  /** Terraria slope type (0 = none, 1 = ↖, 2 = ↗, 3/4 = ceilings). */
  public int slope() {
    return block.slope(this);
  }

  /** Whether the tile only fills its bottom half (Terraria half brick). */
  public boolean halfBrick() {
    return block.halfBrick(this);
  }

  /** Whether the block renders dynamically (animated) and must be drawn
   * every frame instead of being baked into a chunk mesh. */
  public boolean isAnimatedRendering() {
    return block.isAnimatedRendering(this);
  }

  /** Whether the tile lets light through (not a full {@link Shape#SOLID}). */
  public float filterSkylight(int x, int y, float in, byte channel) {
    return block.filterSkylight(this, x, y, in, channel);
  }

  /** Filters incoming light through this state, per channel. */
  public float filterLight(int x, int y, float in, byte channel) {
    return block.filterLight(this, x, y, in, channel);
  }

  /** The ambient light emitted by this state on one channel, or {@code 0}. */
  public float emitAmbient(int x, int y, byte channel) {
    return block.emitAmbient(this, x, y, channel);
  }

  /** The directional beams emitted by this state; the caller draws and
   * recycles them. */
  public Collection<Beam> emitBeams(int x, int y) {
    return block.emitBeams(this, x, y);
  }
}
