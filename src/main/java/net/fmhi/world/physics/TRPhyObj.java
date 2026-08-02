/*
 * MIT License — direct port of Terraria 1.3 player movement physics
 * (Collision.TileCollision / StepUp / StepDown / SlopeCollision),
 * adapted to a Y-up world: the position is the feet, y grows upward.
 * The tile unit is 1.0 (Terraria's 16 px).
 *
 * Terraria treats velocity as "movement per frame"; here the velocity is
 * per second and the per-frame displacement is {@code velocity × dt}, so
 * the public API stays velocity-per-second.
 */

package net.fmhi.world.physics;

import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;

/**
 * Terraria-style physics: every frame the movement is clipped per axis
 * against the solid tiles ({@link #tileCollision}), the body steps up or
 * down one-block ledges ({@link #stepUp}, {@link #stepDown}) and slides
 * along slopes ({@link #slopeCollision}). Position is the feet; the body
 * spans {@code [x, x+width] × [y, y+height]} upward.
 */
@NullMarked
public abstract class TRPhyObj {

  private static final float TOLERANCE = 0.001F;
  private static final float MAX_SPEED = 64F;

  // -- state -------------------------------------------------------------

  protected PrecisePos position = PrecisePos.ZERO;
  protected Vector2 velocity = Vector2.ZERO;
  private boolean onGround;
  /** Whether the body is currently in liquid (used for swimming). */
  private boolean isFloating;
  /** Terraria Collision.up / Collision.down from the last tile collision. */
  private boolean collidedUp;
  private boolean collidedDown;

  // -- parameters ---------------------------------------------------------

  protected float gravity = 65F;
  protected float groundFriction = 0F;
  protected float bounceFactor = 0F;
  /** Density of the entity, used for liquid buoyancy. */
  protected float density = 1F;
  /** Upward speed limit while swimming (liquid jump profile). */
  protected float swimSpeed = 5F;
  /** Upward burst added on a fresh jump press in liquid. */
  protected float swimJumpSpeed = 10F;
  /** How fast the swim velocity approaches {@link #swimSpeed} while held. */
  protected float swimForce = 50F;

  // ActorMovementController: "down" to drop through platforms
  private int fallThroughSustain;
  private static final int FALL_THROUGH_FRAMES = 10;

  // -- abstract ----------------------------------------------------------

  public abstract float width();
  public abstract float height();

  // -- accessors ---------------------------------------------------------

  public PrecisePos position() { return position; }

  public PrecisePos center() {
    return new PrecisePos(position.xf() + width() / 2F, position.yf() + height() / 2F);
  }

  public Box2D bounds() {
    return Box2D.create(position.xf(), position.yf(), width(), height());
  }

  public void setPosition(PrecisePos pos) { this.position = pos; }
  public Vector2 velocity() { return velocity; }
  public void setVelocity(Vector2 v) { this.velocity = v; }
  public void setVelocity(float vx, float vy) { this.velocity = new Vector2(vx, vy); }
  public boolean onGround() { return onGround; }
  public boolean isFloating() { return isFloating; }

  private boolean lastControlJump;

  /** Starbound-style swimming: a fresh press adds an upward burst, holding
   * approaches the swim speed. */
  public boolean liquidJump(boolean controlJump, float dt) {
    boolean newPress = controlJump && !lastControlJump;
    lastControlJump = controlJump;
    if (!isFloating) return false;
    if (newPress) {
      velocity = new Vector2(velocity.x(), velocity.y() + swimJumpSpeed);
    } else if (controlJump) {
      float step = Math.clamp(swimSpeed - velocity.y(), -swimForce * dt, swimForce * dt);
      velocity = new Vector2(velocity.x(), velocity.y() + step);
    }
    return true;
  }

  /** Call when the "down" key is held. */
  public void ignorePlatformTemporarily() {
    fallThroughSustain = FALL_THROUGH_FRAMES;
  }

  // -- tick --------------------------------------------------------------

  public void tick(double dt, Level level) {
    float d = (float) dt;
    if (fallThroughSustain > 0) fallThroughSustain--;

    // velocity (per second): clamp, gravity, liquid forces
    if (Math.abs(velocity.x()) > MAX_SPEED) velocity = new Vector2(Math.clamp(velocity.x(), -MAX_SPEED, MAX_SPEED), velocity.y());
    if (Math.abs(velocity.y()) > MAX_SPEED) velocity = new Vector2(velocity.x(), Math.clamp(velocity.y(), -MAX_SPEED, MAX_SPEED));

    float liquidContact = liquidContactFraction(level);
    isFloating = liquidContact > 0F;
    float envVy = -gravity * d;
    if (liquidContact > 0F) {
      envVy = gravity * (dominantLiquid.density() * liquidContact - density) * d;
      envVy += dominantLiquid.temperature() / 1000F * liquidContact * gravity * 0.1F * d;
    }
    velocity = new Vector2(velocity.x(), velocity.y() + envVy);
    if (liquidContact > 0F) {
      float keep = Math.max(0F, 1F - dominantLiquid.viscosity() * liquidContact * d);
      velocity = velocity.multiply(keep);
    }

    // -- Terraria movement -------------------------------------------------
    boolean fallThrough = fallThroughSustain > 0;

    // step down a ledge while resting on the ground and falling
    if (onGround && velocity.y() < 0F) {
      stepDown(level);
    }
    // step up a ledge while resting on the ground
    if (onGround && !fallThrough) {
      stepUp(level);
    }

    // dry collision: clip the displacement per axis (Terraria
    // TileCollision), then apply the move
    Vector2 disp = tileCollision(velocity.multiply(d), level);
    if (collidedUp) {
      disp = new Vector2(disp.x(), 0.01F);
    }
    position = new PrecisePos(position.xf() + disp.x(), position.yf() + disp.y());

    // slope correction (Terraria SlopeCollision / SlopingCollision)
    SlopeResult slope = slopeCollision(disp, level, fallThrough);
    position = slope.position;
    disp = slope.displacement;

    // velocity reflects the clipped displacement
    velocity = new Vector2(disp.x() / d, disp.y() / d);

    // ground state: re-check after the slope pass
    collidedDown = false;
    collidedUp = false;
    tileCollision(disp, level);
    onGround = collidedDown && velocity.y() <= 0F;

    // bounce on blocked axes
    if (collidedUp && velocity.y() > 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);
    if (collidedDown && velocity.y() < 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);

    // ground friction
    if (onGround && groundFriction > 0F) {
      float keep = 1F - groundFriction * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y());
    }
  }

  // -- Terraria TileCollision ---------------------------------------------

  /**
   * Clips the displacement against the solid tiles the body sweeps over,
   * per axis (Terraria Collision.TileCollision). Sets
   * {@link #collidedUp}/{@link #collidedDown}.
   */
  private Vector2 tileCollision(Vector2 disp, Level level) {
    collidedUp = false;
    collidedDown = false;
    Vector2 result = disp;

    float px = position.xf();
    float feet = position.yf();
    float pxr = px + width();
    float top = feet + height();
    // moved position (Terraria checks the destination against the tiles)
    float mvx = px + disp.x();
    float mvy = feet + disp.y();

    int minBX = (int) Math.floor(mvx) - 1;
    int maxBX = (int) Math.floor(mvx + width()) + 2;
    int minBY = (int) Math.floor(mvy) - 1;
    int maxBY = (int) Math.floor(mvy + height()) + 2;

    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        int slope = tileSlope(bx, by, level);
        boolean solid = tileSolid(bx, by, level);
        boolean platform = !solid && tilePlatform(bx, by, level);
        if (!solid && !platform) continue;

        // the tile box [bx, bx+1] × [by, by+1] (Y-up); the feet resting
        // exactly on the top count as touching
        if (!(mvx + width() > bx && mvx < bx + 1F && mvy + height() > by && mvy <= by + 1F)) continue;

        // slopes: a body near the surface is not blocked (Terraria flag2)
        if (slope > 0) {
          boolean flag2 = false;
          if (slope == 2 && feet + Math.abs(disp.x()) + 1F >= by + 1F && px >= bx) flag2 = true;
          if (slope == 1 && feet + Math.abs(disp.x()) + 1F >= by + 1F && pxr <= bx + 1F) flag2 = true;
          if (flag2) continue;
        }

        // landing: the moved feet reach the tile top while the feet are
        // at or inside the tile (Y-up: feet >= bottom). Only the tile the
        // feet actually touch counts, so a wall only clips the body's X.
        if (mvy <= by + 1F && feet >= by) {
          collidedDown = true;
          boolean falling = disp.y() < 0F;
          if (!platform || !(fallThroughSustain > 0 || falling)) {
            if (falling) {
              float clip = (by + 1F) - feet;
              result = new Vector2(result.x(), Math.max(disp.y(), clip));
            } else if (!platform) {
              result = new Vector2(result.x(), Math.min(disp.y(), (by + 1F) - feet));
            }
          }
        } else if (pxr <= bx && !platform) {
          // body left of the tile, moving right
          result = new Vector2(Math.min(result.x(), bx - pxr), result.y());
        } else if (px >= bx + 1F && !platform) {
          // body right of the tile, moving left
          result = new Vector2(Math.max(result.x(), bx + 1F - px), result.y());
        } else if (top <= by && mvy + height() >= by && !platform) {
          // the top crosses the tile bottom from below: rising into it
          collidedUp = true;
          result = new Vector2(result.x(), Math.min(result.y(), by - top));
        }
      }
    }
    return result;
  }

  // -- Terraria StepUp / StepDown -----------------------------------------

  /**
   * Smoothly walks the body down a ledge up to one block high (Terraria
   * Collision.StepDown). Y-up: down means smaller y.
   */
  private void stepDown(Level level) {
    float px = position.xf() + velocity.x() * 0.0166667F;
    float feet = position.yf();
    int minBX = (int) Math.floor(px);
    int maxBX = (int) Math.floor(px + width());
    int row = (int) Math.floor(feet);
    float lowest = Float.MAX_VALUE;
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = row; by <= row + 1; by++) {
        if (!tileSolid(bx, by, level) && !tilePlatform(bx, by, level)) continue;
        float tileTop = by + 1F;
        // the body intersects this tile's footprint
        if (px + width() > bx && px < bx + 1F && feet + height() > by && feet < by + 1F) {
          lowest = Math.min(lowest, tileTop);
        }
      }
    }
    float drop = lowest - feet;
    if (drop <= 0F || drop >= 1F) return;
    if (drop > 0.44F && drop < 1.06F) return;
    position = new PrecisePos(position.xf(), lowest);
  }

  /**
   * Steps the body up a ledge no higher than one block (Terraria
   * Collision.StepUp): the tile just above the feet must be climbable
   * (slope or platform, never a solid wall), with head room above.
   */
  private void stepUp(Level level) {
    int dir = velocity.x() < 0F ? -1 : velocity.x() > 0F ? 1 : 0;
    if (dir == 0) return;
    float px = position.xf() + velocity.x() * 0.0166667F;
    float feet = position.yf();
    int col = (int) Math.floor(px + width() / 2F + (width() / 2F + 1F) * dir);
    int footRow = (int) Math.floor(feet);
    int rows = (int) Math.ceil(height());

    int slope = tileSlope(col, footRow + 1, level);
    boolean stepTile = slope > 0 || tilePlatform(col, footRow + 1, level);
    if (!stepTile) return;

    // head room: the column above the step must be clear
    for (int r = footRow + 2; r <= footRow + rows + 1; r++) {
      if (tileSolid(col, r, level)) return;
    }

    float top;
    if (slope > 0) {
      // the slope surface under the body's leading edge (Y-up: slope 2 ↗
      // rises from bottom-left to top-right, slope 1 ↖ the mirror)
      float offset = dir > 0 ? (px + width() - col) : (col + 1F - px);
      top = footRow + 1F + (slope == 2 ? offset : 1F - offset);
    } else {
      top = footRow + 2F; // platform top
    }
    float rise = top - feet;
    if (rise <= 0F || rise > 1F) return;

    // try the move with the body raised; it must not collide
    Vector2 savedVel = velocity;
    position = new PrecisePos(position.xf(), feet + rise);
    Vector2 disp = tileCollision(velocity.multiply(0.0166667F), level);
    if (Math.abs(disp.x()) < TOLERANCE) {
      // blocked: undo
      position = new PrecisePos(position.xf(), feet);
      velocity = savedVel;
    }
  }

  // -- Terraria SlopeCollision ---------------------------------------------

  private record SlopeResult(PrecisePos position, Vector2 displacement) {
  }

  /**
   * Slides the body along slopes: the feet are pulled onto the slope
   * surface and the vertical displacement is zeroed (Terraria
   * Collision.SlopeCollision).
   */
  private SlopeResult slopeCollision(Vector2 disp, Level level, boolean fall) {
    float px = position.xf();
    float feet = position.yf();
    float pxr = px + width();
    float top = feet + height();

    float newY = feet;
    Vector2 newDisp = disp;

    int minBX = (int) Math.floor(px) - 1;
    int maxBX = (int) Math.floor(pxr) + 2;
    int minBY = (int) Math.floor(feet) - 1;
    int maxBY = (int) Math.floor(top) + 2;

    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        int slope = tileSlope(bx, by, level);
        if (slope != 1 && slope != 2) continue;
        if (!tileSolid(bx, by, level)) continue;
        if (!(pxr > bx && px < bx + 1F && top > by && feet < by + 1F)) continue;

        float offset;
        if (slope == 2) offset = pxr - bx;      // ↗ high right
        else offset = bx + 1F - px;             // ↖ high left
        if (offset >= 0F) {
          // the feet at or above the slope surface rest on it (Y-up: the
          // feet are pulled down onto the surface)
          if (feet >= by + offset) {
            float target = by + offset;
            if (target < newY) {
              if (fall) continue;
              newY = target;
              if (newDisp.y() > 0F) newDisp = new Vector2(newDisp.x(), 0F);
            }
          }
        } else if (feet > by) {
          float target = by + 1F;
          if (newY < target) {
            newY = target;
            if (newDisp.y() > 0F) newDisp = new Vector2(newDisp.x(), 0F);
          }
        }
      }
    }
    return new SlopeResult(new PrecisePos(px, newY), newDisp);
  }

  // -- tile queries --------------------------------------------------------

  private @org.jspecify.annotations.Nullable Chunk chunkAt(int wx, int wy, Level level) {
    return level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy));
  }

  private boolean tileSolid(int wx, int wy, Level level) {
    Chunk c = chunkAt(wx, wy, level);
    if (c == null) return false;
    BlockState state = c.getBlock(wx, wy);
    return switch (state.shape()) {
      case VACUUM, PARTIAL, TRANSLUCENT -> false;
      case SOLID -> true;
    };
  }

  private boolean tilePlatform(int wx, int wy, Level level) {
    Chunk c = chunkAt(wx, wy, level);
    if (c == null) return false;
    return c.getBlock(wx, wy).getVoxelShape() instanceof VoxelPlatform;
  }

  private int tileSlope(int wx, int wy, Level level) {
    Chunk c = chunkAt(wx, wy, level);
    if (c == null) return 0;
    return c.getBlock(wx, wy).slope();
  }

  // -- liquid contact ------------------------------------------------------

  private Liquid dominantLiquid = Liquids.EMPTY;

  /** The fraction of the body inside liquid (0..1), clipped to the surface
   * height; the liquid with the largest overlap becomes the dominant one. */
  private float liquidContactFraction(Level level) {
    Box2D bb = bounds();
    dominantLiquid = Liquids.EMPTY;
    float total = 0F;
    float best = 0F;
    int minBX = (int) Math.floor(bb.minX());
    int maxBX = (int) Math.floor(bb.maxX());
    int minBY = (int) Math.floor(bb.minY());
    int maxBY = (int) Math.floor(bb.maxY());
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        Chunk c = chunkAt(bx, by, level);
        if (c == null) continue;
        int lv = c.getLiquidLevel(bx, by);
        if (lv <= 0) continue;
        float surfaceY = by + lv / (float) FluidEngine.FULL;
        float ox = Math.min(bb.maxX(), bx + 1F) - Math.max(bb.minX(), bx);
        float oy = Math.min(bb.maxY(), surfaceY) - Math.max(bb.minY(), by);
        if (ox <= 0F || oy <= 0F) continue;
        float ov = ox * oy;
        total += ov;
        if (ov > best) {
          best = ov;
          dominantLiquid = Liquids.byId(c.getLiquidType(bx, by));
        }
      }
    }
    float area = bb.width() * bb.height();
    return area > 0F ? Math.min(1F, total / area) : 0F;
  }
}
