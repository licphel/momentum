/*
 * MIT License — Enchant-style per-axis clip physics.
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
 * Enchant-style physics: movement is clipped per axis against the
 * {@link VoxelClip} shapes of the surrounding blocks. A blocked horizontal
 * move retries with the body raised by {@link #stepHeight} (step-up), so
 * slopes and stairs built from box outlines are walked automatically.
 * Liquid buoyancy, drag and swimming are applied on top.
 */
@NullMarked
public abstract class SBPhyObj {

  private static final float MAX_SPEED = 64F;
  private static final float TOLERANCE = 0.001F;

  // -- state -------------------------------------------------------------

  protected PrecisePos position = PrecisePos.ZERO;
  protected Vector2 velocity = Vector2.ZERO;
  protected boolean onGround;
  private boolean wasOnGround;
  /** Whether the body is currently in liquid (used for swimming). */
  private boolean isFloating;

  // -- parameters ---------------------------------------------------------

  protected float gravityMultiplier = 1F;
  protected float bounceFactor = 0F;
  protected boolean stopOnFirstBounce = false;
  protected boolean collisionEnabled = true;
  protected float groundFriction = 0F;
  protected float airFriction = 0F;
  /** Density of the entity, used for liquid buoyancy. */
  protected float density = 1F;
  protected float mass = 1F;
  /** Upward speed limit while swimming (liquid jump profile). */
  protected float swimSpeed = 5F;
  /** Upward burst added on a fresh jump press in liquid. */
  protected float swimJumpSpeed = 10F;
  /** How fast the swim velocity approaches {@link #swimSpeed} while held. */
  protected float swimForce = 50F;
  /** How high a blocked horizontal move may step up (slopes, stairs). */
  protected float stepHeight = 1F;

  // ActorMovementController: "down" to drop through platforms
  private int fallThroughSustain;
  private static final int FALL_THROUGH_FRAMES = 10;

  // -- abstract ----------------------------------------------------------

  /** The body's collision box in local coordinates (top-left origin,
   * Y-down). */
  public abstract Box2D collisionBox();
  protected abstract float gravity();

  // -- accessors ---------------------------------------------------------

  public PrecisePos position() {
    return position;
  }

  public PrecisePos center() {
    Box2D bb = bounds();
    return new PrecisePos(bb.centralX(), bb.centralY());
  }

  /** The body's bounds in world coordinates. */
  public Box2D bounds() {
    return collisionBox().translate(position.xf(), position.yf());
  }

  public void setPosition(PrecisePos pos) {
    this.position = pos;
  }
  public Vector2 velocity() { return velocity; }
  public void setVelocity(Vector2 v) { this.velocity = v; }
  public void setVelocity(float vx, float vy) { this.velocity = new Vector2(vx, vy); }
  public boolean onGround() { return onGround; }

  /** Whether the body is currently in liquid (partially or fully). */
  public boolean isFloating() { return isFloating; }

  private boolean lastControlJump;

  /**
   * Starbound-style swimming: a fresh press adds an upward burst on top of
   * the current velocity; holding approaches the swim speed at
   * {@link #swimForce} acceleration instead of overriding the velocity.
   * Outside liquid the action is ignored (so swimming never launches the
   * body out of the water and bounces).
   *
   * @param controlJump whether the jump control is held
   * @param dt          the frame time
   * @return whether the body is in liquid (the action was considered)
   */
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

  /** Whether the last tick ended with a downward collision (standing). */
  private boolean touchDown;

  public void tick(double dt, Level level) {
    float d = (float) dt;
    wasOnGround = onGround;

    if (Math.abs(velocity.x()) > MAX_SPEED) {
      velocity = new Vector2(Math.clamp(velocity.x(), -MAX_SPEED, MAX_SPEED), velocity.y());
    }
    if (Math.abs(velocity.y()) > MAX_SPEED) {
      velocity = new Vector2(velocity.x(), Math.clamp(velocity.y(), -MAX_SPEED, MAX_SPEED));
    }

    if (fallThroughSustain > 0) fallThroughSustain--;

    // liquid contact: the net vertical force in liquid is the body's own
    // weight (scaled by its density) minus the weight of the displaced
    // liquid, so a body lighter than the liquid rises; viscosity damps
    // the resulting velocity
    float liquidContact = liquidContactFraction(level);
    isFloating = liquidContact > 0F;
    // Y-up: gravity pulls downward (negative Y)
    float envVy = -gravity() * gravityMultiplier * d;
    if (liquidContact > 0F) {
      float g = gravity() * gravityMultiplier;
      envVy = g * (dominantLiquid.density() * liquidContact - density) * d;
      envVy += dominantLiquid.temperature() / 1000F * liquidContact * g * 0.1F * d;
    }
    velocity = new Vector2(velocity.x(), velocity.y() + envVy);
    if (liquidContact > 0F) {
      float keep = Math.max(0F, 1F - dominantLiquid.viscosity() * liquidContact * d);
      velocity = velocity.multiply(keep);
    }

    // -- Enchant-style move: clip X, step-up, clip Y -----------------------
    float dx = velocity.x() * d;
    float dy = velocity.y() * d;
    float dx0 = dx;
    float dy0 = dy;
    if (Math.abs(dx) < TOLERANCE) dx = 0F;
    if (Math.abs(dy) < TOLERANCE) dy = 0F;

    Box2D origin = bounds();
    Box2D dest = origin;
    boolean stepped = false;

    if (collisionEnabled && dx != 0F) {
      dx = clipX(dx, dest, level);

      // Terraria-style step-up: when a horizontal move is blocked while
      // standing, step onto a climbable tile (slope, platform) in front,
      // raising the body exactly onto its surface — but never onto a
      // solid wall, never higher than stepHeight, and only with head room
      if (onGround && Math.abs(dx - dx0) > TOLERANCE) {
        float rise = stepRise(dest, dx0, level);
        if (rise > 0F && rise <= stepHeight) {
          Box2D raised = dest.translate(0F, rise);
          float dx1 = clipX(dx0, raised, level);
          if (Math.abs(dx1) >= TOLERANCE) {
            dest = raised.translate(dx1, 0F);
            dx = dx1;
            stepped = true;
          }
        }
      }
    }

    if (!stepped) dest = dest.translate(dx, 0F);

    if (collisionEnabled && dy != 0F) {
      dy = clipY(dy, dest, level);
    }
    dest = dest.translate(0F, dy);

    // the clip rules only prevent entering a shape; a body that ended up
    // overlapping one (e.g. pulled up by a slope into a ceiling) is pushed
    // out along the smallest penetration axis
    if (collisionEnabled) {
      for (int i = 0; i < 3; i++) {
        Box2D next = resolveOverlaps(dest, level);
        if (next.equals(dest)) break;
        dest = next;
      }
    }

    boolean xClip = Math.abs(dx - dx0) > TOLERANCE;
    boolean yClip = Math.abs(dy - dy0) > TOLERANCE;
    touchDown = dy0 < 0F && yClip; // Y-up: falling (dy < 0) was blocked
    boolean touchUp = dy0 > 0F && yClip;
    boolean touchLeft = dx0 < 0F && xClip;
    boolean touchRight = dx0 > 0F && xClip;

    position = new PrecisePos(dest.minX(), dest.minY());

    onGround = !(gravity() == 0F) && touchDown;

    // bounce: reflect the blocked velocity component
    if (touchUp && velocity.y() > 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);
    if (touchDown && velocity.y() < 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);
    if (touchLeft && velocity.x() < 0F) velocity = new Vector2(velocity.x() * -bounceFactor, velocity.y());
    if (touchRight && velocity.x() > 0F) velocity = new Vector2(velocity.x() * -bounceFactor, velocity.y());

    // ground friction
    if (onGround && groundFriction > 0F) {
      float keep = 1F - groundFriction * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y());
    }
  }

  // -- step-up ------------------------------------------------------------

  /**
   * The height to raise the body onto the tile in front, or
   * {@code 0}/{@code NaN}-based {@code <= 0} if there is no climbable step.
   * Follows Terraria's StepUp: only non-wall shapes (slope outlines,
   * platforms) qualify, the step must be no higher than
   * {@link #stepHeight}, and the column above must be clear.
   */
  private float stepRise(Box2D dest, float dx0, Level level) {
    int dir = dx0 > 0F ? 1 : -1;
    // the column being entered, measured at the moved position (Terraria
    // offsets by the velocity before picking the tile)
    int col = dir > 0
        ? (int) Math.floor(dest.maxX() + dx0)
        : (int) Math.floor(dest.minX() + dx0);
    int footRow = (int) Math.floor(dest.minY());

    // Terraria flag7: the tile just above the feet must be climbable,
    // never a solid wall
    VoxelClip step = voxelShape(col, footRow + 1, level);
    if (step == VoxelClip.EMPTY || step == VoxelClip.CUBE) return 0F;

    float x0 = dir > 0 ? dest.minX() : dest.minX() + dx0;
    float x1 = dir > 0 ? dest.maxX() + dx0 : dest.maxX();
    float top = step.topAt(x0, x1, col, footRow + 1);
    if (Float.isNaN(top)) return 0F;
    float rise = top - dest.minY();
    if (rise <= 0F || rise > stepHeight) return 0F;

    // head room: every tile the raised body would newly occupy, across all
    // its columns, must be clear — except the step tile itself
    int minCol = (int) Math.floor(dir > 0 ? dest.minX() + dx0 : dest.minX());
    int maxCol = (int) Math.floor(dir > 0 ? dest.maxX() + dx0 : dest.maxX());
    int headRow = (int) Math.floor(dest.maxY() + rise);
    for (int c = minCol; c <= maxCol; c++) {
      for (int r = footRow + 1; r <= headRow; r++) {
        if (c == col && r == footRow + 1) continue; // the step tile itself
        if (voxelShape(c, r, level) != VoxelClip.EMPTY) return 0F;
      }
    }
    return rise;
  }

  // -- clipping -----------------------------------------------------------

  /** Clips an X movement against every block shape the body sweeps over. */
  private float clipX(float dx, Box2D aabb, Level level) {
    int minBX = (int) Math.floor(Math.min(aabb.minX(), aabb.minX() + dx));
    int maxBX = (int) Math.floor(Math.max(aabb.maxX(), aabb.maxX() + dx));
    int minBY = (int) Math.floor(aabb.minY());
    int maxBY = (int) Math.floor(aabb.maxY());
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        VoxelClip clip = voxelShape(bx, by, level);
        if (clip == VoxelClip.EMPTY) continue;
        dx = clip.clipX(dx, aabb, bx, by);
      }
    }
    return dx;
  }

  /** Clips a Y movement against every block shape the body sweeps over;
   * one-way platforms are ignored while falling through. */
  private float clipY(float dy, Box2D aabb, Level level) {
    int minBX = (int) Math.floor(aabb.minX());
    int maxBX = (int) Math.floor(aabb.maxX());
    int minBY = (int) Math.floor(Math.min(aabb.minY(), aabb.minY() + dy));
    int maxBY = (int) Math.floor(Math.max(aabb.maxY(), aabb.maxY() + dy));
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        VoxelClip clip = voxelShape(bx, by, level);
        if (clip == VoxelClip.EMPTY) continue;
        // Y-up: platforms are skipped while rising (dy > 0) or falling
        // through (fallThroughSustain)
        if (clip instanceof VoxelPlatform && (fallThroughSustain > 0 || dy > 0F)) continue;
        dy = clip.clipY(dy, aabb, bx, by);
      }
    }
    return dy;
  }

  /** The collision shape of a tile, or {@link VoxelClip#EMPTY} for
   * unloaded chunks. */
  private VoxelClip voxelShape(int wx, int wy, Level level) {
    Chunk chunk = level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy));
    if (chunk == null) return VoxelClip.EMPTY;
    return chunk.getBlock(wx, wy).getVoxelShape();
  }

  /**
   * Pushes the body out of any tile it overlaps, along the smallest
   * penetration axis (vertical first). One-way platforms are skipped.
   */
  private Box2D resolveOverlaps(Box2D dest, Level level) {
    int minBX = (int) Math.floor(dest.minX());
    int maxBX = (int) Math.floor(dest.maxX());
    int minBY = (int) Math.floor(dest.minY());
    int maxBY = (int) Math.floor(dest.maxY());
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        VoxelClip clip = voxelShape(bx, by, level);
        if (clip == VoxelClip.EMPTY || clip instanceof VoxelPlatform) continue;
        if (!clip.interacts(dest, bx, by)) continue;
        if (clip instanceof VoxelSlope slope) {
          // feet below the slope surface: pull them onto it
          float sy = slope.surfaceMax(dest, bx, by);
          if (!Float.isNaN(sy) && dest.minY() < sy) {
            return dest.translate(0F, sy - dest.minY());
          }
          continue;
        }
        // box-like shape: push the body out along the shortest distance to
        // the outside (the feet to the top, the head to the bottom, the
        // sides to their edges), so ceilings push down and floors push up
        float toUp = (by + 1F) - dest.minY();    // distance feet → box top
        float toDown = dest.maxY() - by;         // distance head → box bottom
        float toLeft = dest.maxX() - bx;         // distance right edge → box left
        float toRight = (bx + 1F) - dest.minX(); // distance left edge → box right
        float best = Math.min(Math.min(toUp, toDown), Math.min(toLeft, toRight));
        if (best == toUp) return dest.translate(0F, toUp);
        if (best == toDown) return dest.translate(0F, -toDown);
        if (best == toLeft) return dest.translate(-toLeft, 0F);
        return dest.translate(toRight, 0F);
      }
    }
    return dest;
  }

  // -- liquid contact ------------------------------------------------------

  private Liquid dominantLiquid = Liquids.EMPTY;

  /** The fraction of the body inside liquid ({@code 0..1}); the liquid
   * with the largest overlap becomes {@link #dominantLiquid}. */
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
        int lv = level.getLiquidLevel(bx, by);
        if (lv <= 0) continue;
        // Y-up: liquid fills the tile from the bottom up to its surface
        // height, so the overlap must be clipped to the liquid region
        float surfaceY = by + lv / (float) FluidEngine.FULL;
        float ox = Math.min(bb.maxX(), bx + 1F) - Math.max(bb.minX(), bx);
        float oy = Math.min(bb.maxY(), surfaceY) - Math.max(bb.minY(), by);
        if (ox <= 0F || oy <= 0F) continue;
        float ov = ox * oy;
        total += ov;
        if (ov > best) {
          best = ov;
          dominantLiquid = level.getLiquidStack(bx, by).liquid();
        }
      }
    }
    float area = bb.width() * bb.height();
    return area > 0F ? Math.min(1F, total / area) : 0F;
  }
}
