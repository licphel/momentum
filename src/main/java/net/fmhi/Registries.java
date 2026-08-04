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

package net.fmhi;

import net.fmhi.collection.Palette;
import net.fmhi.fml.registry.IndirectRegistry;
import net.fmhi.fml.registry.Holder;
import net.fmhi.fml.registry.Registry;
import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.world.block.Block;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.item.Item;
import net.fmhi.world.light.Beam;
import net.fmhi.world.light.Channel;
import net.fmhi.world.physics.CollisionKind;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.physics.SBPhyObj;
import net.fmhi.world.physics.VoxelClip;
import net.fmhi.world.physics.VoxelPlatform;
import net.fmhi.world.util.BlockPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for built-in blocks, items, and other engine objects.
 *
 * <p>Call {@link #bootstrap()} to finalize block property definitions
 * and fill state palettes before using any blocks.
 */
@NullMarked
public final class Registries {

  public static final Registry<Block> BLOCKS = new IndirectRegistry<>(Core.NAMESPACE.resolve("block"));
  public static final Registry<Item> ITEMS = new IndirectRegistry<>(Core.NAMESPACE.resolve("item"));

  /** Global palette for all ImmutablePropertyMap states. */
  public static final Palette<ImmutablePropertyMap> PROPERTY_PALETTE = new Palette<>();

  private static final List<Block> ALL_BLOCKS = new ArrayList<>();

  // -- blocks -------------------------------------------------------------

  public static final Block AIR = registerBlock("air", new Block() {
    @Override
    public @Nullable Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return null;
    }

    @Override
    public Shape shape(BlockState state) {
      return Shape.VACUUM;
    }

    @Override
    public VoxelClip getVoxelShape(BlockState state) {
      return VoxelClip.EMPTY;
    }
  });

  public static final Block DIRT = registerBlock("dirt", new Block() {
    @Override
    public float bounce() {
      return 0.1F;
    }
  });
  public static final Block GRASS = registerBlock("grass", new Block() {});
  public static final Block STONE = registerBlock("stone", new Block() {});

  /** A wall block that emits warm torchlight. */
  public static final Block WALL = registerBlock("wall", new Block() {
    @Override public float emitAmbient(BlockState state, int x, int y, byte channel) {
      return switch (channel) {
        case Channel.RED -> 1.0F;
        case Channel.GREEN -> 0.7F;
        default -> 0.3F;
      };
    }
  });

  /** A pole that emits cool blue light and three rotating beams. */
  public static final Block COLORFUL = registerBlock("pole", new Block() {
    @Override public float emitAmbient(BlockState state, int x, int y, byte channel) {
      double fm = System.currentTimeMillis();
      return switch (channel) {
        case Channel.RED -> (float) Math.sin(fm / 1000.0) * 0.25F + 0.5F;
        case Channel.GREEN -> (float) Math.sin(fm / 1000.0 + 1) * 0.25F + 0.5F;
        default -> (float) Math.sin(fm / 1000.0 + 2) * 0.25F + 0.5F;
      };
    }

    @Override
    public java.util.Collection<Beam> emitBeams(BlockState state, int x, int y) {
      float f = (float) (System.currentTimeMillis() % 1000000) / 1000.0F;
      return List.of(
          Beam.pooled().set(x + 0.5F, y + 0.5F, 1, 0.2F, 0.2F,
              (float) -Math.PI / 2 + f, 0.5F, 0.0F, 15.0F, 2.0F),
          Beam.pooled().set(x + 0.5F, y + 0.5F, 0.2F, 1F, 0.2F,
              (float) -Math.PI / 2 + 2 + f * 2, 0.5F, 0.0F, 15.0F, 2.0F),
          Beam.pooled().set(x + 0.5F, y + 0.5F, 0.2F, 0.2F, 1F,
              (float) -Math.PI / 2 + 0.3F + f * 3, 0.5F, 0.0F, 15.0F, 2.0F)
      );
    }
  });

  // Y-down local coords: (0,0) top-left, (1,1) bottom-right.
  // ↗ ramp: walks right → goes up. Approximated by three boxes of
  // increasing height (Enchant VoxelOutline); step-up walks them.
  private static final Polygon SHAPE_SLOPE_RIGHT = new Polygon(
      new net.fmhi.math.Vector2(0, 1), new net.fmhi.math.Vector2(1, 0), new net.fmhi.math.Vector2(1, 1));
  // ↖ ramp: walks left → goes up. Solid bottom-left triangle.
  private static final Polygon SHAPE_SLOPE_LEFT = new Polygon(
      new net.fmhi.math.Vector2(0, 0), new net.fmhi.math.Vector2(1, 1), new net.fmhi.math.Vector2(0, 1));

  // Slopes approximated with a staircase of boxes (VoxelClip.generateSlope),
  // so the clip physics walks them smoothly and the step-up climbs them.
  // ↗ ramp: walks right → goes up (left edge low, right edge high).
  private static final VoxelClip VOXEL_SLOPE_RIGHT = VoxelClip.SLOPE_LEFT_DOWN;
  // ↖ ramp: walks left → goes up (left edge high, right edge low).
  private static final VoxelClip VOXEL_SLOPE_LEFT = VoxelClip.SLOPE_RIGHT_DOWN;

  public static final Block SLOPE_RIGHT = registerBlock("slope_right", new Block() {
    @Override
    public Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return SHAPE_SLOPE_RIGHT;
    }

    @Override
    public Shape shape(BlockState state) {
      return Shape.PARTIAL; // liquids fill the slope gaps
    }

    @Override
    public VoxelClip getVoxelShape(BlockState state) {
      return VOXEL_SLOPE_RIGHT;
    }

    @Override
    public int slope(BlockState state) {
      return 2; // ↗ high right (Terraria slope 2)
    }
  });

  public static final Block SLOPE_LEFT = registerBlock("slope_left", new Block() {
    @Override
    public Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return SHAPE_SLOPE_LEFT;
    }

    @Override
    public Shape shape(BlockState state) {
      return Shape.PARTIAL; // liquids fill the slope gaps
    }

    @Override
    public VoxelClip getVoxelShape(BlockState state) {
      return VOXEL_SLOPE_LEFT;
    }

    @Override
    public int slope(BlockState state) {
      return 1; // ↖ high left (Terraria slope 1)
    }
  });

  public static final Block PLATFORM = registerBlock("platform", new Block() {
    @Override public CollisionKind collisionKind() { return CollisionKind.PLATFORM; }
    @Override public Shape shape(BlockState state) { return Shape.PARTIAL; }
    @Override public VoxelClip getVoxelShape(BlockState state) { return new VoxelPlatform(); }
  });

  // -- items --------------------------------------------------------------

  public static final Holder<Item> AIR_ITEM = ITEMS.register(Core.NAMESPACE.resolve("air"), Item::new);

  // -- registration -------------------------------------------------------

  private static Block registerBlock(String name, Block block) {
    BLOCKS.register(Core.NAMESPACE.resolve(name), () -> block);
    ALL_BLOCKS.add(block);
    return block;
  }

  private static boolean bootstrapped;

  /**
   * Runs property collection + state generation for all registered blocks.
   * Must be called once before blocks are used.
   */
  public static void bootstrap() {
    if (bootstrapped) return;
    bootstrapped = true;

    for (Block block : ALL_BLOCKS) {
      block.collectProperties(block.propertyDef());
      block.propertyDef().collectStates(PROPERTY_PALETTE);
      block.fillStates();
    }

    BLOCKS.freeze();
    ITEMS.freeze();

    BlockState.EMPTY = Registries.AIR.defaultState();
  }

  private Registries() {}
}
