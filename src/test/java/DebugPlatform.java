import net.fmhi.Registries;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.PrecisePos;

/**
 * Debug: does a body dropped onto a platform actually rest on it, and does
 * a body jumping and falling back onto a platform rest on it (Terraria:
 * yes, unless holding down)?
 */
public class DebugPlatform {

  public static void main(String[] args) {
    Registries.bootstrap();
    testDropOntoPlatform();
    testJumpAndLandBack();
    testFastFallThroughPlatform();
    System.out.println("==== DEBUG DONE ====");
  }

  private static void testDropOntoPlatform() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    Entity p = Entity.player(new PrecisePos(5, 4F));
    p.enterChunk(level);
    for (int i = 0; i < 120; i++) p.tick(1F / 60F, level);
    System.out.println("dropOntoPlatform: feet=" + p.position().yf() + " onGround=" + p.onGround()
        + " (expect ~3.0)");
  }

  /** Jump up from the platform, fall back: must land on the platform again. */
  private static void testJumpAndLandBack() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    Entity p = Entity.player(new PrecisePos(5, 3F));
    p.enterChunk(level);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level); // settle on the platform
    p.setVelocity(0F, 8F); // jump
    for (int i = 0; i < 20; i++) p.tick(1F / 60F, level); // rise + fall back
    System.out.println("jumpAndLandBack: feet=" + p.position().yf() + " (expect ~3.0, "
        + (p.position().yf() < 2.5F ? "BROKEN: fell through!" : "ok") + ")");
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    System.out.println("jumpAndLandBack after settle: feet=" + p.position().yf() + " onGround=" + p.onGround());
  }

  /** Falling onto a platform while NOT holding down: Terraria clips (lands). */
  private static void testFastFallThroughPlatform() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    Entity p = Entity.player(new PrecisePos(5, 5F));
    p.enterChunk(level);
    p.setVelocity(0F, -10F); // fast fall (no down key)
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    System.out.println("fastFall no-down: feet=" + p.position().yf() + " (expect ~3.0, Terraria lands)");
  }

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() {
    return Registries.STONE.defaultState();
  }

  private static BlockState platform() {
    return Registries.PLATFORM.defaultState();
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }
}
