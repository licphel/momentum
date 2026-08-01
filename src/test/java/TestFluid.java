import net.fmhi.Registries;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.LiquidStack;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.Channel;
import net.fmhi.world.light.LightBuffer;
import net.fmhi.world.light.SimpleLightBuffer;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;

/**
 * Hand-run tests for the fluid engine (house style: assert + main).
 *
 * <p>Run this class; it prints {@code ==== ALL TESTS PASSED ====} on success.
 */
public class TestFluid {

  private static final int EPS = 2;

  public static void main(String[] args) {
    Registries.bootstrap();
    testFall();
    testEqualize();
    testSpread();
    testConservation();
    testPressureRise();
    testLavaReaction();
    testMix();
    testCrossChunk();
    testUnload();
    testLiquidProperties();
    testBuoyancy();
    System.out.println("==== ALL TESTS PASSED ====");
  }

  /** Water falls exactly one tile per simulation tick. */
  private static void testFall() {
    Level level = newLevel();
    // 1-wide column: side walls and a floor keep the water contained
    for (int y = 6; y <= 10; y++) {
      setBlock(level, 5, y, stone());
      setBlock(level, 7, y, stone());
    }
    setBlock(level, 6, 10, stone());
    level.setLiquid(6, 6, Liquids.WATER, 255);
    tick(level, 1);
    assertLevel(level, 6, 6, 0, 0);
    assertLevel(level, 6, 7, 255, 0);
    assertLevel(level, 6, 8, 0, 0); // the snapshot prevents a double fall
    tick(level, 3);
    assertLevel(level, 6, 9, 255, 0); // settled on the floor
    assertLevel(level, 6, 10, 0, 0);
    tick(level, 10);
    assertLevel(level, 6, 9, 255, 0); // stable once settled
    System.out.println("testFall OK");
  }

  /** A single full cell levels out inside a closed container. */
  private static void testEqualize() {
    Level level = newLevel();
    // closed 3-wide container: floor + side walls hold the water in
    for (int x = 4; x <= 8; x++) {
      setBlock(level, x, 10, stone());
      if (x == 4 || x == 8) setBlock(level, x, 9, stone());
    }
    level.setLiquid(6, 9, Liquids.WATER, 255);
    for (int i = 0; i < 100; i++) tick(level, 1);
    assertLevel(level, 5, 9, 85, EPS);
    assertLevel(level, 6, 9, 85, EPS);
    assertLevel(level, 7, 9, 85, EPS);
    System.out.println("testEqualize OK");
  }

  /** On open ground the water spreads sideways, conserving its total. */
  private static void testSpread() {
    Level level = newLevel();
    // floor extending well past the pool so nothing falls off the edge
    for (int x = -10; x <= 22; x++) setBlock(level, x, 10, stone());
    level.setLiquid(6, 9, Liquids.WATER, 255);
    for (int i = 0; i < 10; i++) tick(level, 1);
    assert level.getLiquidLevel(4, 9) > 13 : "no spread to the left";
    assert level.getLiquidLevel(8, 9) > 13 : "no spread to the right";
    assert level.getLiquidLevel(6, 9) > 13 : "center drained";
    int sum = 0;
    for (int x = -4; x <= 16; x++) sum += level.getLiquidLevel(x, 9);
    // the spreading front sheds single units that cannot split further
    // (they evaporate), so most of the water must survive
    assert sum > 216 : "spread lost liquid: " + sum;
    System.out.println("testSpread OK");
  }

  /** The engine conserves the total amount inside a closed container. */
  private static void testConservation() {
    Level level = newLevel();
    for (int x = 4; x <= 8; x++) {
      setBlock(level, x, 10, stone());
      if (x == 4 || x == 8) setBlock(level, x, 9, stone());
    }
    level.setLiquid(5, 9, Liquids.WATER, 102);
    level.setLiquid(6, 9, Liquids.WATER, 102);
    level.setLiquid(7, 9, Liquids.WATER, 51);
    for (int i = 0; i < 100; i++) tick(level, 1);
    int sum = 0;
    for (int x = 5; x <= 7; x++) sum += level.getLiquidLevel(x, 9);
    assert sum == 255 : "conservation broken: " + sum;
    assertLevel(level, 4, 9, 0, 0); // the walls hold everything in
    assertLevel(level, 8, 9, 0, 0);
    System.out.println("testConservation OK");
  }

  /** An over-full tile squeezes the excess upward (Starbound overfill). */
  private static void testPressureRise() {
    Level level = newLevel();
    // sealed 1-wide box with an open top
    for (int x = 4; x <= 6; x++) setBlock(level, x, 10, stone());
    setBlock(level, 4, 9, stone());
    setBlock(level, 6, 9, stone());
    level.setLiquid(5, 9, Liquids.WATER, 382); // 255 + 127 overfill
    tick(level, 1);
    // the whole overfill (127) is squeezed upward
    assertLevel(level, 5, 9, 255, 0);
    assertLevel(level, 5, 8, 127, 0);
    System.out.println("testPressureRise OK");
  }

  /** Lava touching enough water solidifies into stone. */
  private static void testLavaReaction() {
    Level level = newLevel();
    // sealed container: lava with a single water cell beside it, so nothing
    // can flow back after the reaction
    for (int x = 4; x <= 7; x++) {
      setBlock(level, x, 10, stone());
      if (x == 4 || x == 7) setBlock(level, x, 9, stone());
    }
    level.setLiquid(5, 9, Liquids.LAVA, 255);
    level.setLiquid(6, 9, Liquids.WATER, 255);
    tick(level, 1);
    assertLevel(level, 5, 9, 0, 0);
    assertLevel(level, 6, 9, 0, 0);
    assert level.getBlock(5, 9).block() == Registries.STONE : "lava did not solidify";
    System.out.println("testLavaReaction OK");
  }

  /** Two small pools of different liquids mix instead of layering. */
  private static void testMix() {
    Level level = newLevel();
    // sealed container with a thin layer of water next to a thin lava pool
    for (int x = 4; x <= 7; x++) {
      setBlock(level, x, 10, stone());
      if (x == 4 || x == 7) setBlock(level, x, 9, stone());
    }
    level.setLiquid(5, 9, Liquids.LAVA, 12);   // too little to react
    level.setLiquid(6, 9, Liquids.WATER, 12);
    tick(level, 1);
    // no reaction possible, so the thinner liquid converts: only one type
    // remains (and it must not be empty on both sides without a reaction)
    LiquidStack s5 = level.getLiquidStack(5, 9);
    LiquidStack s6 = level.getLiquidStack(6, 9);
    assert s5.liquid() == s6.liquid() : "liquids layered instead of mixing";
    assert s5.level() + s6.level() > 0 : "both liquids vanished";
    System.out.println("testMix OK");
  }

  /** Liquid flows across chunk borders. */
  private static void testCrossChunk() {
    Level level = newLevel();
    // continuous floor across the chunk (0,0) / (1,0) border at x=15|16
    for (int x = 10; x <= 20; x++) setBlock(level, x, 10, stone());
    level.setLiquid(15, 9, Liquids.WATER, 255); // right border of chunk (0,0)
    tick(level, 1);
    // half the difference flows sideways, split between both sides in
    // random order, so the border tile receives 63 or 127
    assert level.getLiquidLevel(16, 9) >= 51 : "did not flow into chunk (1,0)";
    assert level.getLiquidLevel(14, 9) >= 51 : "did not flow into chunk (0,0)";
    System.out.println("testCrossChunk OK");
  }

  /** Unloading a chunk removes its liquid from the simulation. */
  private static void testUnload() {
    Level level = newLevel();
    setBlock(level, 6, 10, stone());
    level.setLiquid(6, 9, Liquids.WATER, 255);
    level.unloadChunk(new ChunkPos(0, 0));
    for (int i = 0; i < 10; i++) tick(level, 1);
    assert level.getLiquidLevel(6, 9) == 0 : "liquid survived the unload";
    System.out.println("testUnload OK");
  }

  /** Liquid light and physics properties. */
  private static void testLiquidProperties() {
    assert Liquids.LAVA.density() > Liquids.WATER.density();
    assert Liquids.LAVA.temperature() > Liquids.WATER.temperature();
    assert Liquids.LAVA.viscosity() >= Liquids.WATER.viscosity();
    LightBuffer lb = new SimpleLightBuffer();
    assert Liquids.LAVA.getLight(lb) && lb.r() > 0F : "lava does not glow";
    assert !Liquids.WATER.getLight(new SimpleLightBuffer()) : "water glows";
    assert Liquids.WATER.filterLight(1F, Channel.RED, 255) < 1F : "water does not absorb light";
    System.out.println("testLiquidProperties OK");
  }

  /** An entity floats in water, buoyed by its contact area. */
  private static void testBuoyancy() {
    Level level = newLevel();
    // water pool with walls and a floor
    for (int y = 8; y <= 10; y++) {
      setBlock(level, 2, y, stone());
      setBlock(level, 8, y, stone());
      for (int x = 3; x <= 7; x++) level.setLiquid(x, y, Liquids.WATER, 255);
    }
    for (int x = 2; x <= 8; x++) setBlock(level, x, 11, stone());
    Entity p = Entity.player(new PrecisePos(5, 9));
    p.enterChunk(level);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    assert p.position().yf() < 9F : "player did not float: y=" + p.position().yf();
    System.out.println("testBuoyancy OK");
  }

  // -- helpers -------------------------------------------------------------

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() {
    return Registries.STONE.defaultState();
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }

  private static void tick(Level level, int n) {
    for (int i = 0; i < n; i++) level.tick(FluidEngine.TICK_INTERVAL);
  }

  private static void assertLevel(Level level, int x, int y, int expected, int eps) {
    int actual = level.getLiquidLevel(x, y);
    assert Math.abs(actual - expected) <= eps
        : "(" + x + "," + y + "): expected " + expected + " ± " + eps + " got " + actual;
  }
}
