import net.fmhi.Registries;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.PrecisePos;

/**
 * Hand-run tests for the Enchant-style clip physics, Y-up world (y grows
 * upward; the ground is at low y; the player position is the feet).
 */
public class TestPhysics {

  public static void main(String[] args) {
    Registries.bootstrap();
    testStandOnBlock();
    testWallBlocks();
    testStepUpSlope();
    testNoAutoStepWall();
    testHeadRoom();
    testPlatform();
    testFallThrough();
    System.out.println("==== ALL TESTS PASSED ====");
  }

  /** A body falls and stops on top of a block (block at y=0 spans
   * [0, 1], so the feet rest on 1). */
  private static void testStandOnBlock() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    // the player position is the feet: drop from above the floor
    Entity p = Entity.player(new PrecisePos(5, 5F));
    p.enterChunk(level);
    for (int i = 0; i < 120; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert Math.abs(feet - 1F) < 0.05F : "feet should rest on y=1, got " + feet;
    assert p.onGround() : "player should be on the ground";
    System.out.println("testStandOnBlock OK");
  }

  /** A wall blocks horizontal movement. */
  private static void testWallBlocks() {
    Level level = newLevel();
    for (int y = -2; y <= 2; y++) setBlock(level, 6, y, stone());
    for (int x = 3; x <= 5; x++) setBlock(level, x, -3, stone());
    Entity p = Entity.player(new PrecisePos(3, -2F));
    p.enterChunk(level);
    p.setVelocity(10F, 0F);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    float right = p.bounds().maxX();
    assert right <= 6.01F : "should stop at the wall, right edge " + right;
    System.out.println("testWallBlocks OK");
  }

  /** Walking right up a ↗ slope climbs it smoothly onto the high ground. */
  private static void testStepUpSlope() {
    Level level = newLevel();
    for (int x = 3; x <= 10; x++) setBlock(level, x, 0, stone());
    setBlock(level, 5, 1, slopeRight());
    setBlock(level, 6, 1, slopeRight());
    setBlock(level, 7, 1, slopeRight());
    for (int x = 8; x <= 10; x++) setBlock(level, x, 1, stone()); // high ground
    Entity p = Entity.player(new PrecisePos(2, 1F)); // feet on the ground top
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 90; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert Math.abs(feet - 2F) < 0.1F : "player should stand on the high ground, feet " + feet;
    assert p.position().xf() > 5F : "player should have crossed the slope, x=" + p.position().xf();
    System.out.println("testStepUpSlope OK");
  }

  /** A one-block solid wall is not stepped onto automatically (Terraria:
   * solid walls need a jump; only slopes and platforms are steps). */
  private static void testNoAutoStepWall() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    setBlock(level, 6, 1, stone()); // 1-block wall in front
    Entity p = Entity.player(new PrecisePos(2, 1F)); // feet on the ground top
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    float right = p.bounds().maxX();
    assert right <= 6.01F : "wall should stop the player, right=" + right;
    System.out.println("testNoAutoStepWall OK");
  }

  /** A slope under a low ceiling cannot push the body into it. */
  private static void testHeadRoom() {
    Level level = newLevel();
    for (int x = 3; x <= 8; x++) setBlock(level, x, 0, stone());
    setBlock(level, 6, 1, slopeRight());
    for (int x = 5; x <= 7; x++) setBlock(level, x, 4, stone()); // low ceiling
    Entity p = Entity.player(new PrecisePos(2, 1F)); // feet on the ground top
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    // the body top starts at 3.65, the ceiling bottom is 4: the slope
    // would raise the feet to 2 (top 4.65), but the ceiling must stop it
    assert p.bounds().maxY() <= 4.01F
        : "player was pushed into the ceiling, top=" + (p.bounds().maxY());
    System.out.println("testHeadRoom OK");
  }

  /** A platform holds the body from above but not from below. */
  private static void testPlatform() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    // fall from above (feet 4 > platform top 3): lands on the platform
    Entity p = Entity.player(new PrecisePos(5, 4F));
    p.enterChunk(level);
    for (int i = 0; i < 120; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert Math.abs(feet - 3F) < 0.05F : "should rest on the platform, feet " + feet;
    // drop through the platform, then jump up through it from below: the
    // platform must not block the jump
    p.ignorePlatformTemporarily();
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    assert p.position().yf() < 2.9F : "should have fallen below the platform";
    p.setVelocity(0F, 20F);
    for (int i = 0; i < 15; i++) p.tick(1F / 60F, level);
    assert p.position().yf() > 3.5F : "jump should pass through the platform";
    System.out.println("testPlatform OK");
  }

  /** Holding down drops the body through a platform. */
  private static void testFallThrough() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    Entity p = Entity.player(new PrecisePos(5, 4F));
    p.enterChunk(level);
    for (int i = 0; i < 120; i++) p.tick(1F / 60F, level);
    p.ignorePlatformTemporarily();
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert feet < 2.5F : "should have dropped through the platform, feet " + feet;
    System.out.println("testFallThrough OK");
  }

  // -- helpers -------------------------------------------------------------

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() {
    return Registries.STONE.defaultState();
  }

  private static BlockState slopeRight() {
    return Registries.SLOPE_RIGHT.defaultState();
  }

  private static BlockState platform() {
    return Registries.PLATFORM.defaultState();
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }
}
