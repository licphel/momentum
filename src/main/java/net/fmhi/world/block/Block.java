/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fmhi.world.block;

import net.fmhi.fml.registry.RegistryContext;
import net.fmhi.fml.registry.RegistryEntry;
import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.property.PropertyDef;
import net.fmhi.world.light.Beam;
import net.fmhi.world.light.LightEngine;
import net.fmhi.world.physics.CollisionKind;
import net.fmhi.world.physics.DynamicObject;
import net.fmhi.world.physics.VoxelClip;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.item.ItemLike;
import net.fmhi.world.physics.Polygon;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stub for a block type.
 */
public class Block implements ItemLike, RegistryEntry<Block> {
  private final RegistryContext registryContext = new RegistryContext();

  @Override
  public RegistryContext getRegistryContext() {
    return registryContext;
  }

  final PropertyDef propertyDef = new PropertyDef();
  final List<BlockState> states = new ArrayList<>();
  @Nullable BlockState defaultState;

  public PropertyDef propertyDef() { return propertyDef; }

  public void collectProperties(PropertyDef def) {
  }

  public void fillStates() {
    for (ImmutablePropertyMap map : propertyDef.maps()) {
      BlockState state = new BlockState(this, map);
      BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE.assign(state);
      states.add(state);
    }

    defaultState = BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE.get(propertyDef.defaultMap().identity());
  }

  public @Nullable Polygon getPhysicsShape(BlockState state, BlockPos pos, DynamicObject obj) {
    return Polygon.CUBE;
  }

  /** The collision shape used by the physics engine (Enchant-style clip
   * shapes: boxes, outlines for slopes, one-way platforms). */
  public VoxelClip getVoxelShape(BlockState state) {
    return VoxelClip.CUBE;
  }

  /** Terraria slope type: 0 = none, 1 = ↖ high left, 2 = ↗ high right,
   * 3/4 = ceiling slopes. Used by the Terraria physics port. */
  public int slope(BlockState state) {
    return 0;
  }

  /** Whether the tile only fills its bottom half (Terraria half brick):
   * its top surface sits 8 px lower. */
  public boolean halfBrick(BlockState state) {
    return false;
  }

  /** Whether the block renders dynamically (animated texture, etc.) and
   * must be drawn every frame instead of being baked into a chunk mesh. */
  public boolean isAnimatedRendering(BlockState state) {
    return false;
  }

  /** Restitution: 0 = no bounce, 1 = perfect. */
  public float bounce() { return 0F; }

  /** Friction: 0 = ice, 1 = rough. */
  public float friction() { return 0.5F; }

  /** Collision kind for the physics engine. */
  public CollisionKind collisionKind() { return CollisionKind.BLOCK; }

  /** How this block fills its tile (collision, light and liquid rules). */
  public Shape shape(BlockState state) { return Shape.SOLID; }

  public float filterSkylight(BlockState state, int x, int y, float in, byte channel) {
    if (shape(state) == Shape.SOLID) {
      return 0.0F;
    }
    return in;
  }

  /** Filters incoming light through this state, per channel. */
  public float filterLight(BlockState state, int x, int y, float in, byte channel) {
    if (shape(state) == Shape.SOLID) {
      return in * 0.92F - LightEngine.UNIT;
    } else {
      return in * 0.99F - LightEngine.UNIT;
    }
  }

  /** The ambient light emitted by this state on one channel, or {@code 0}. */
  public float emitAmbient(BlockState state, int x, int y, byte channel) {
    return 0F;
  }

  /** The directional beams emitted by this state; the caller draws and
   * recycles them. */
  public List<Beam> emitBeams(BlockState state, int x, int y) {
    return List.of();
  }

  public BlockState defaultState() {
    return Objects.requireNonNull(defaultState);
  }
}
