/*
 * MIT License — direct port of Terraria 1.3 player movement physics
 * (Collision.TileCollision / SlopeCollision / WalkDownSlope / StepUp /
 * StepDown and their call order in Player.Update), adapted to a Y-up
 * world: the position is the feet, y grows upward, and the tile unit is
 * 1.0 (Terraria's 16 px). All pixel constants are divided by 16.
 *
 * Terraria treats velocity as "movement per frame"; here velocity is per
 * second and the collision routines operate on the per-frame displacement
 * (velocity × dt), so the public API stays velocity-per-second.
 */

package net.fmhi.world.physics;

import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Terraria-style physics: every frame the movement is clipped per axis
 * against the solid tiles ({@link #tileCollision}), the body walks up or
 * down one-block ledges ({@link #stepUp}, {@link #stepDown}), slides
 * along slopes ({@link #slopeCollision}) and drops through platforms
 * while holding down. Position is the feet; the body spans
 * {@code [x, x+width] × [y, y+height]} upward.
 */
@NullMarked
public abstract class TRPhyObj {

  /** 1 Terraria pixel in tile units (a tile is 1.0 = 16 px). */
  private static final float PX = 1F / 16F;
  /** Collision room kept under a ceiling (TileCollision's +0.01 px). */
  private static final float CEILING_ROOM = 0.01F * PX;
  /** Minimum downward speed kept when pressed against a ceiling slope
   * (SlopeCollision's 0.0101 px/frame). */
  private static final float SLOPE_FALL_MIN = 0.0101F * PX;
  /** A fall faster than this lands on a platform even while dropping
   * through (TileCollision: {@code Velocity.Y > 1.0}). */
  private static final float FALL_THROUGH_SPEED = 1F * PX;
  /** Slope-surface snap tolerance (SlopeCollision's ±1 px). */
  private static final float SLOPE_SNAP = 1F * PX;
  /** Maximum step height (StepUp's 16.1 px). */
  private static final float STEP_UP_MAX = 16.1F * PX;
  /** Ledge drop range walked by StepDown: 7..17 px. */
  private static final float STEP_DOWN_MIN = 7F * PX;
  private static final float STEP_DOWN_MAX = 17F * PX;
  /** Half-brick height (8 px). */
  private static final float HALF_BRICK = 8F * PX;

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
  /** Terraria Collision.stair / stairFall / sloping. */
  private boolean stair;
  private boolean stairFall;
  private boolean sloping;

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

    // -- Terraria movement (Player.Update order) --------------------------
    boolean fallThrough = fallThroughSustain > 0;

    // SlopeDownMovement → WalkDownSlope: only at the exact gravity step
    // (Terraria: velocity.Y == gravity) a downward slope pulls the body
    // down with the horizontal speed
    walkDownSlope(level, d);

    // StepDown / StepUp also only fire at the gravity step, and StepUp
    // never while holding down (Terraria: velocity.Y >= gravity &&
    // !controlDown)
    if (Math.abs(velocity.y() + gravity * d) < TOLERANCE) {
      stepDown(level, d);
    }
    if (velocity.y() <= -gravity * d + TOLERANCE && !fallThrough) {
      stepUp(level, d);
    }

    // DryCollision → TileCollision: clip the per-frame displacement, then
    // apply it (Terraria: position += velocity)
    var tc = tileCollision(velocity.multiply(d), level);
    Vector2 disp = tc.disp();
    // sample the contact state before SlopeCollision re-runs TileCollision
    // and resets it (Terraria: UpdateTouchingTiles runs before
    // SlopingCollision)
    boolean groundHit = collidedDown;
    boolean headHit = collidedUp;
    position = new PrecisePos(position.xf() + disp.x() + tc.pushOut().x(),
        position.yf() + disp.y() + tc.pushOut().y());

    // SlopingCollision → SlopeCollision (fall = stairFall, forced while
    // dropping through platforms); it corrects the already-moved position
    SlopeResult slope = slopeCollision(disp, level, stairFall || fallThrough);
    position = slope.position;
    disp = slope.displacement;
    stairFall = this.stairFall || fallThrough;

    // velocity reflects the clipped displacement
    velocity = new Vector2(disp.x() / d, disp.y() / d);

    onGround = groundHit && velocity.y() <= 0F;

    // bounce on blocked axes
    if (headHit && velocity.y() > 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);
    if (groundHit && velocity.y() < 0F) velocity = new Vector2(velocity.x(), velocity.y() * -bounceFactor);

    // ground friction
    if (onGround && groundFriction > 0F) {
      float keep = 1F - groundFriction * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y());
    }
  }

  // -- Terraria TileCollision ---------------------------------------------

  private record TileColResult(Vector2 disp, Vector2 pushOut) {
  }

  /**
   * Clips the displacement against the solid tiles the moved body
   * overlaps, per axis (Collision.TileCollision, lines 1534-1650).
   * Sets {@link #collidedUp}/{@link #collidedDown}. Platforms only clip
   * while landing from above, and are skipped while dropping through
   * unless the fall exceeds 1 px/frame.
   *
   * <p>The returned {@link TileColResult#pushOut()} is a positional
   * correction (pushing a body out of an overlap it cannot normally be
   * in); it must not feed back into the velocity.
   */
  private TileColResult tileCollision(Vector2 disp, Level level) {
    collidedUp = false;
    collidedDown = false;
    Vector2 result = disp;   // clipped displacement (vector2_1)
    Vector2 orig = disp;     // requested displacement (vector2_2)
    Vector2 pushOut = Vector2.ZERO;

    float px = position.xf();      // body left
    float feet = position.yf();    // body bottom (the feet, Y-up)
    float pxr = px + width();      // body right
    float top = feet + height();   // body top
    float mvx = px + disp.x();     // moved body (Terraria checks the
    float mvy = feet + disp.y();   // destination against the tiles)

    int minBX = (int) Math.floor(px) - 1;
    int maxBX = (int) Math.floor(pxr) + 2;
    int minBY = (int) Math.floor(feet) - 1;
    int maxBY = (int) Math.floor(top) + 2;
    // Terraria num5/6 = X-collision tile, num7/8 = landing tile (-1 none)
    int num5 = -1;
    int num6 = -1;
    int num7 = -1;
    int num8 = -1;
    // Terraria num13 sentinel starts far below the body (Y-down); here
    // far above, so the first real ground always updates it
    float num13 = minBY - 2F;

    for (int bx = minBX; bx < maxBX; bx++) {
      for (int by = minBY; by < maxBY; by++) {
        boolean solid = tileSolid(bx, by, level);
        boolean platform = !solid && tilePlatform(bx, by, level);
        if (!solid && !platform) continue;

        // tile box [bx, bx+1] × [by, by+1]; a half brick only fills the
        // bottom half (Terraria shifts the box top down 8 px)
        float boxMinY = by;
        float boxMaxY = by + 1F;
        if (halfBrick(bx, by, level)) boxMaxY = by + HALF_BRICK;

        // the moved body intersects the tile box
        if (!(mvx + width() > bx && mvx < bx + 1F
            && mvy + height() > boxMinY && mvy < boxMaxY)) continue;

        // slopes: a body near the surface is not clipped (Terraria flag2)
        boolean flag1 = false;
        boolean flag2 = false;
        int slope = tileSlope(bx, by, level);
        if (slope > 2) {
          // ceiling slopes 3/4: the head within |dx| px of the tile bottom
          if (slope == 3 && top - Math.abs(disp.x()) <= by && px >= bx) flag2 = true;
          if (slope == 4 && top - Math.abs(disp.x()) <= by && pxr <= bx + 1F) flag2 = true;
        } else if (slope > 0) {
          // floor slopes 1/2: the feet within |dx| px of the tile top
          flag1 = true;
          if (slope == 1 && feet + Math.abs(disp.x()) >= boxMaxY && px >= bx) flag2 = true;
          if (slope == 2 && feet + Math.abs(disp.x()) >= boxMaxY && pxr <= bx + 1F) flag2 = true;
        }
        if (flag2) continue;

        // landing: the feet rest on the tile top (Y-up: feet >= top)
        if (feet >= boxMaxY) {
          collidedDown = true;
          if ((!platform || fallThroughSustain <= 0 || -disp.y() > FALL_THROUGH_SPEED)
              && num13 < boxMaxY) {
            num7 = bx;
            num8 = by;
            if (halfBrick(bx, by, level)) num8++;
            if (num7 != num5 && !flag1) {
              // Terraria assigns the landing clip directly (the body lands
              // exactly on the tile top, however far the fall reached)
              result = new Vector2(result.x(), boxMaxY - feet);
              num13 = boxMaxY;
            }
          }
        } else if (pxr <= bx && !platform) {
          // body left of the tile, moving right; a slope in the tile to
          // the left lets the body pass (Terraria checks tile[x-1])
          if (tileSlope(bx - 1, by, level) != 2 && tileSlope(bx - 1, by, level) != 4) {
            num5 = bx;
            num6 = by;
            if (num6 != num8)
              result = new Vector2(Math.min(result.x(), bx - pxr), result.y());
            if (num7 == num5)
              result = new Vector2(result.x(), orig.y());
          }
        } else if (px >= bx + 1F && !platform) {
          // body right of the tile, moving left
          if (tileSlope(bx + 1, by, level) != 1 && tileSlope(bx + 1, by, level) != 3) {
            num5 = bx;
            num6 = by;
            if (num6 != num8)
              result = new Vector2(Math.max(result.x(), bx + 1F - px), result.y());
            if (num7 == num5)
              result = new Vector2(result.x(), orig.y());
          }
        } else if (top <= by && !platform) {
          // the top crosses the tile bottom from below: rising into it
          collidedUp = true;
          num7 = bx;
          num8 = by;
          result = new Vector2(result.x(), by - top - CEILING_ROOM);
          if (num8 == num6)
            result = new Vector2(orig.x(), result.y());
        }

        // overlap guard: if the moved body still overlaps this solid tile
        // (riding a wall edge, a block placed into the body), push it out
        // along the smallest penetration axis so it cannot tunnel through
        // the tile and out of the ground
        if (!platform) {
          float rx = px + result.x();
          float rfeet = feet + result.y();
          if (rx + width() > bx && rx < bx + 1F
              && rfeet + height() > boxMinY && rfeet < boxMaxY) {
            float up = boxMaxY - rfeet;
            float down = rfeet + height() - boxMinY;
            float left = rx + width() - bx;
            float right = bx + 1F - rx;
            float best = Math.min(Math.min(up, down), Math.min(left, right));
            if (best > TOLERANCE) {
              if (best == up) pushOut = new Vector2(pushOut.x(), Math.max(pushOut.y(), up));
              else if (best == down) pushOut = new Vector2(pushOut.x(), Math.min(pushOut.y(), -down));
              else if (best == left) pushOut = new Vector2(Math.min(pushOut.x(), -left), pushOut.y());
              else pushOut = new Vector2(Math.max(pushOut.x(), right), pushOut.y());
            }
          }
        }
      }
    }
    return new TileColResult(result, pushOut);
  }

  // -- Terraria WalkDownSlope ---------------------------------------------

  /**
   * Pulls the body down a slope it is standing on: at the exact gravity
   * step, a downward slope adds the horizontal speed to the fall
   * (Collision.WalkDownSlope, lines 957-1040).
   */
  private void walkDownSlope(Level level, float d) {
    if (Math.abs(velocity.y() + gravity * d) > TOLERANCE) return;
    float px = position.xf();
    float feet = position.yf();
    float top = feet + height();
    int minBX = (int) Math.floor(px);
    int maxBX = (int) Math.floor(px + width());
    int row = (int) Math.floor(feet - 4F * PX);   // the row 4 px below the feet
    // sentinel: a row far above the body (Y-up; Terraria uses far below)
    float num7 = row + 4F;
    int index1 = -1;
    int index2 = -1;
    int slopeDir = velocity.x() < 0F ? 2 : 1;     // Terraria num8
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = row - 1; by <= row; by++) {
        boolean solid = tileSolid(bx, by, level) || tilePlatform(bx, by, level);
        if (!solid) continue;
        float tileTop = by + 1F;
        if (halfBrick(bx, by, level)) tileTop = by + HALF_BRICK;
        // the tile top is within 1..17 px below the feet (Terraria tests a
        // rect just above the tile, Y-down)
        if (!(px + width() > bx && px < bx + 1F
            && top > by + 1F + PX && feet < by + 1F + (1F + 16F) * PX)) continue;
        if (tileTop >= num7) {
          if (num7 == tileTop) {
            // a tie with a previous slope only picks a slope of the same
            // downward direction
            if (tileSlope(bx, by, level) != 0) {
              if (index1 != -1 && index2 != -1 && tileSlope(index1, index2, level) != 0) {
                if (tileSlope(bx, by, level) == slopeDir) {
                  num7 = tileTop;
                  index1 = bx;
                  index2 = by;
                }
              } else {
                num7 = tileTop;
                index1 = bx;
                index2 = by;
              }
            }
          } else {
            num7 = tileTop;
            index1 = bx;
            index2 = by;
          }
        }
      }
    }
    if (index1 != -1 && index2 != -1 && tileSlope(index1, index2, level) > 0) {
      int slope = tileSlope(index1, index2, level);
      float num10;
      if (slope == 2) {                         // ↗: walking left is downhill
        num10 = (index1 + 1F) - (px + width());
        if (feet <= index2 + 1F - num10 && velocity.x() < 0F) {
          velocity = new Vector2(velocity.x(), velocity.y() - Math.abs(velocity.x()));
        }
      } else if (slope == 1) {                  // ↖: walking right is downhill
        num10 = px - index1;
        if (feet <= index2 + 1F - num10 && velocity.x() > 0F) {
          velocity = new Vector2(velocity.x(), velocity.y() - Math.abs(velocity.x()));
        }
      }
    }
  }

  // -- Terraria StepUp / StepDown -----------------------------------------

  /**
   * Walks the body down a ledge of 7..17 px when falling at the gravity
   * step (Collision.StepDown, lines 2188-2238). The body drops onto the
   * highest tile top below it; slopes in the scanned row cancel the step.
   */
  private void stepDown(Level level, float d) {
    float px = position.xf() + velocity.x() * d;
    float feet = position.yf();
    int minBX = (int) Math.floor(px);
    int maxBX = (int) Math.floor(px + width());
    int row = (int) Math.floor(feet - 4F * PX);   // the row 4 px below the feet
    int num4 = (int) Math.ceil(height());          // body height in tiles
    boolean flag = false;
    // sentinel: a row far above the body (Y-up, so any ground below the
    // feet updates it)
    float num5 = row + num4 + 2F;
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = row - 1; by <= row; by++) {
        if (tileSlope(bx, by, level) != 0) flag = true;   // any slope cancels
        boolean solid = tileSolid(bx, by, level) || tilePlatform(bx, by, level);
        if (!solid) continue;
        float tileTop = by + 1F;
        if (halfBrick(bx, by, level)) tileTop = by + HALF_BRICK;
        // the tile top is within 1..17 px below the feet (Terraria tests a
        // rect just above the tile, Y-down)
        if (!(px + width() > bx && px < bx + 1F
            && feet + height() > by + 1F + PX && feet < by + 1F + (1F + 16F) * PX)) continue;
        if (tileTop > num5) num5 = tileTop;      // highest ground wins
      }
    }
    // Y-up: a ground below the feet gives a positive drop
    float drop = feet - num5;
    if (drop <= STEP_DOWN_MIN || drop >= STEP_DOWN_MAX || flag) return;
    position = new PrecisePos(position.xf(), num5);
  }

  /**
   * Steps the body up a ledge no higher than one block
   * (Collision.StepUp, lines 2240-2342): the tile just above the feet
   * must be climbable (platform, slope in the body's low half, half
   * brick with a clear tile above), with head room and a clear
   * back-tile at the head row.
   */
  private void stepUp(Level level, float d) {
    int dir = velocity.x() < 0F ? -1 : velocity.x() > 0F ? 1 : 0;
    float px = position.xf() + velocity.x() * d;
    float feet = position.yf();
    float centerX = position.xf() + width() / 2F;
    int col = (int) Math.floor(px + width() / 2F + (width() / 2F + 1F) * dir);
    int footRow = (int) Math.floor(feet - PX);     // the row containing the feet
    int num2 = (int) Math.ceil(height());          // body height in tiles

    if (chunkAt(col, footRow, level) == null) return;
    for (int index2 = 1; index2 < num2 + 2; index2++) {
      if (chunkAt(col, footRow + index2, level) == null) return;
    }
    if (chunkAt(col - dir, footRow + num2, level) == null) return;

    // head room: tiles 2..num2 above the feet must not be solid
    boolean flag1 = true;
    for (int index2 = 2; index2 < num2 + 1; index2++) {
      flag1 = flag1 && !tileSolid(col, footRow + index2, level);
    }
    // the back tile at the head row must not be solid
    boolean flag3 = !tileSolid(col - dir, footRow + num2, level);

    // flag7: the tile just above the feet is climbable — a platform, or a
    // slope under the body's low half, or a half brick with a clear tile
    // above
    int s1 = tileSlope(col, footRow + 1, level);
    boolean flag7 = !tileSolid(col, footRow + 1, level)
        || ((s1 == 1 && centerX > col) || (s1 == 2 && centerX < col + 1F))
        || (halfBrick(col, footRow + 1, level) && !tileSolid(col, footRow + num2 + 1, level));

    // flag8 (Terraria's ternary): when the foot tile is solid the step is
    // allowed; when it is passable (air, platform, slope with the body on
    // its low side) the tile just above the feet must be an active half
    // brick — so a body falling through air never steps up
    int s4 = tileSlope(col, footRow, level);
    boolean x = !tileSolid(col, footRow, level)
        || (s4 != 0 && (s4 != 1 || centerX >= col) && (s4 != 2 || centerX <= col + 1F))
        || (s4 != 0 && feet >= footRow + 1F)
        || !tileSolid(col, footRow, level);
    boolean flag8 = (x
            ? (tileActive(col, footRow + 1, level) && halfBrick(col, footRow + 1, level))
            : true)
        && !(tilePlatform(col, footRow, level) && tilePlatform(col, footRow + 1, level));

    if (col >= px + width() || col + 1F <= px) return;
    if (!flag8 || !flag7 || !flag1 || !flag3) return;

    float num3 = footRow + 1F;                     // the step top
    if (halfBrick(col, footRow + 1, level)) num3 -= HALF_BRICK;
    else if (halfBrick(col, footRow, level)) num3 += HALF_BRICK;
    if (num3 <= feet) return;
    float rise = num3 - feet;
    if (rise > STEP_UP_MAX) return;
    position = new PrecisePos(position.xf(), num3);
  }

  // -- Terraria SlopeCollision ---------------------------------------------

  private record SlopeResult(PrecisePos position, Vector2 displacement) {
  }

  /**
   * Slides the body along slopes: the feet are pulled onto floor-slope
   * surfaces and the head under ceiling slopes, the vertical velocity is
   * zeroed, and the moved position is re-clipped so a blocked lift slides
   * the body sideways (Collision.SlopeCollision, lines 1254-1444).
   */
  private SlopeResult slopeCollision(Vector2 disp, Level level, boolean fall) {
    stair = false;
    stairFall = false;
    boolean[] flagArray = new boolean[5];
    // Terraria num1/num2: the best floor/ceiling surface so far, measured
    // at the body top (Y-down smaller = higher; here larger = higher)
    float px = position.xf();
    float feet = position.yf();
    float pxr = px + width();
    float top = feet + height();
    float num1 = top;
    float num2 = top;
    sloping = false;

    float newX = px;        // Terraria vector2_2 (new position)
    float newY = feet;
    Vector2 newDisp = disp; // Terraria vector2_3 (velocity; per-frame here)

    int minBX = (int) Math.floor(px) - 1;
    int maxBX = (int) Math.floor(pxr) + 2;
    int minBY = (int) Math.floor(feet) - 1;
    int maxBY = (int) Math.floor(top) + 2;

    for (int bx = minBX; bx < maxBX; bx++) {
      for (int by = minBY; by < maxBY; by++) {
        boolean solid = tileSolid(bx, by, level);
        boolean platform = !solid && tilePlatform(bx, by, level);
        if (!solid && !platform) continue;

        float boxMinY = by;
        float boxMaxY = by + 1F;
        if (halfBrick(bx, by, level)) boxMaxY = by + HALF_BRICK;

        // the body intersects the tile box (the already-moved position,
        // as Terraria calls SlopeCollision after applying the velocity)
        if (!(pxr > bx && px < bx + 1F && top > boxMinY && feet < boxMaxY)) continue;

        boolean flag1 = true;
        int slope = tileSlope(bx, by, level);
        if (slope > 0) {
          if (slope > 2) {
            // ceiling slopes 3/4: the head within |dx|+1 px of the tile
            // bottom
            if (slope == 3 && top - (Math.abs(disp.x()) + SLOPE_SNAP) <= by && px >= bx) flag1 = true;
            if (slope == 4 && top - (Math.abs(disp.x()) + SLOPE_SNAP) <= by && pxr <= bx + 1F) flag1 = true;
          } else {
            // floor slopes 1/2: the feet within |dx|+1 px of the tile top
            if (slope == 1 && feet + Math.abs(disp.x()) + SLOPE_SNAP >= boxMaxY && px >= bx) flag1 = true;
            if (slope == 2 && feet + Math.abs(disp.x()) + SLOPE_SNAP >= boxMaxY && pxr <= bx + 1F) flag1 = true;
          }
        }
        if (platform) {
          // platforms never block upward movement and only matter while
          // the feet are within 1+|dx| px of the tile
          if (disp.y() > 0F) flag1 = false;
          if (feet > boxMaxY || feet + (SLOPE_SNAP + Math.abs(disp.x())) < by) flag1 = false;
        }
        if (!flag1) continue;

        boolean flag2 = fall && platform;
        int index3 = slope;
        // full-tile box (Terraria resets the half-brick offset here)
        if (!(pxr > bx && px < bx + 1F && top > by && feet < by + 1F)) continue;

        if (index3 == 3 || index3 == 4) {
          // ceiling slopes: press the body down under the surface
          float num12 = index3 == 3 ? px - bx : (bx + 1F) - pxr;
          if (num12 >= 0F) {
            if (top <= by + 1F - num12) {
              float num13 = (by + 1F) - top - num12;
              if (top + num13 > num2) {
                newY = feet + num13;
                num2 = newY;
                if (newDisp.y() > -SLOPE_FALL_MIN)
                  newDisp = new Vector2(newDisp.x(), -SLOPE_FALL_MIN);
                flagArray[index3] = true;
              }
            }
          } else if (top > by) {
            float num13 = by + 1F;
            if (newY < num13) {
              newY = num13;
              if (newDisp.y() > -SLOPE_FALL_MIN)
                newDisp = new Vector2(newDisp.x(), -SLOPE_FALL_MIN);
            }
          }
        }
        if (index3 == 1 || index3 == 2) {
          // floor slopes: pull the feet up onto the surface. The surface
          // is measured from the tile top downward in Terraria (Y-down);
          // Y-up it is by+1-num12 (from the tile bottom upward)
          float num12 = index3 == 1 ? px - bx : (bx + 1F) - pxr;
          if (num12 >= 0F) {
            if (feet <= by + 1F - num12) {
              float num13 = by + 1F - num12 - feet;
              if (top + num13 > num1) {
                if (flag2) {
                  stairFall = true;
                } else {
                  stair = platform;
                  newY = feet + num13;
                  num1 = newY;
                  if (newDisp.y() > 0F)
                    newDisp = new Vector2(newDisp.x(), 0F);
                  flagArray[index3] = true;
                }
              }
            }
          } else if (platform && feet - (4F * PX + Math.abs(disp.x())) > by + 1F) {
            // the feet are more than 4+|dx| px above the platform
            if (flag2) stairFall = true;
          } else {
            float num13 = by + 1F;
            if (newY < num13) {
              if (flag2) {
                stairFall = true;
              } else {
                stair = platform;
                newY = num13;
                if (newDisp.y() > 0F)
                  newDisp = new Vector2(newDisp.x(), 0F);
              }
            }
          }
        }
      }
    }

    // verify: re-clip the moved displacement; a blocked lift (or push)
    // slides the body sideways, away from the slope
    Vector2 disp1 = new Vector2(disp.x(), newY - feet);
    Vector2 clip2 = tileCollision(disp1, level).disp();
    if (clip2.y() < disp1.y()) {
      // the slope lift was blocked from above (head hit)
      float num11 = disp1.y() - clip2.y();
      newY = feet + clip2.y();
      if (flagArray[1]) newX = px - num11;
      if (flagArray[2]) newX = px + num11;
      newDisp = Vector2.ZERO;
      collidedUp = false;
    } else if (clip2.y() > disp1.y()) {
      // the ceiling push was blocked from below (feet hit)
      float num11 = clip2.y() - disp1.y();
      newY = feet + clip2.y();
      if (flagArray[3]) newX = px - num11;
      if (flagArray[4]) newX = px + num11;
      newDisp = Vector2.ZERO;
    }
    return new SlopeResult(new PrecisePos(newX, newY), newDisp);
  }

  // -- tile queries --------------------------------------------------------

  private @Nullable Chunk chunkAt(int wx, int wy, Level level) {
    return level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy));
  }

  /** Whether the tile holds a block (Terraria {@code tile.active()}). */
  private boolean tileActive(int wx, int wy, Level level) {
    Chunk c = chunkAt(wx, wy, level);
    if (c == null) return false;
    return c.getBlock(wx, wy).shape() != Shape.VACUUM;
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

  /** Terraria Tile.halfBrick: the tile only fills its bottom half. */
  private boolean halfBrick(int wx, int wy, Level level) {
    Chunk c = chunkAt(wx, wy, level);
    if (c == null) return false;
    return c.getBlock(wx, wy).halfBrick();
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
