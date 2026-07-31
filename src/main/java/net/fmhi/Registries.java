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
import net.fmhi.fml.registry.DeferredRegister;
import net.fmhi.fml.registry.Holder;
import net.fmhi.math.FastTrigonometric;
import net.fmhi.property.ImmutablePropertyMap;
import net.fmhi.world.block.Block;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.item.Item;
import net.fmhi.world.light.LightBuffer;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.physics.SBPhyObj;
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

  public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Core.NAMESPACE, "block");
  public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Core.NAMESPACE, "item");

  /** Global palette for all ImmutablePropertyMap states. */
  public static final Palette<ImmutablePropertyMap> PROPERTY_PALETTE = new Palette<>();

  private static final List<Block> ALL_BLOCKS = new ArrayList<>();

  // -- blocks -------------------------------------------------------------

  public static final Block AIR = registerBlock("air", new Block() {
    @Override
    public @Nullable Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return null;
    }

    @Override public boolean isSolid(BlockState state) { return false; }
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
    @Override public boolean getLight(BlockState state, LightBuffer buf) {
      buf.r(1.0F); buf.g(0.7F); buf.b(0.3F); return true;
    }
  });

  /** A pole that emits cool blue light. */
  public static final Block COLORFUL = registerBlock("pole", new Block() {
    @Override public boolean getLight(BlockState state, LightBuffer buf) {
      double fm = System.currentTimeMillis();
      buf.r((float) Math.sin(fm / 1000.0) * 0.25F + 0.5F);
      buf.g((float) Math.sin(fm / 1000.0 + 1) * 0.25F + 0.5F);
      buf.b((float) Math.sin(fm / 1000.0 + 2) * 0.25F + 0.5F);
      return true;
    }
  });

  // Y-down local coords: (0,0) top-left, (1,1) bottom-right.
  // ↗ ramp: walks right → goes up. Solid bottom-right triangle.
  private static final Polygon SHAPE_SLOPE_RIGHT = new Polygon(
      new net.fmhi.math.Vector2(0, 1), new net.fmhi.math.Vector2(1, 0), new net.fmhi.math.Vector2(1, 1));
  // ↖ ramp: walks left → goes up. Solid bottom-left triangle.
  private static final Polygon SHAPE_SLOPE_LEFT = new Polygon(
      new net.fmhi.math.Vector2(0, 0), new net.fmhi.math.Vector2(1, 1), new net.fmhi.math.Vector2(0, 1));

  public static final Block SLOPE_RIGHT = registerBlock("slope_right", new Block() {
    @Override
    public Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return SHAPE_SLOPE_RIGHT;
    }
  });

  public static final Block SLOPE_LEFT = registerBlock("slope_left", new Block() {
    @Override
    public Polygon getPhysicsShape(BlockState state, BlockPos pos, SBPhyObj obj) {
      return SHAPE_SLOPE_LEFT;
    }
  });

  public static final Block PLATFORM = registerBlock("platform", new Block() {
    @Override public int collisionKind() { return 2; } // PLATFORM
    @Override public boolean isSolid(BlockState state) { return false; }
  });

  // -- items --------------------------------------------------------------

  public static final Holder<Item> AIR_ITEM = ITEMS.register("air", Item::new);

  // -- registration -------------------------------------------------------

  private static Block registerBlock(String name, Block block) {
    BLOCKS.register(name, () -> block);
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

    BlockState.EMPTY = Registries.AIR.defaultState();
  }

  private Registries() {}
}
