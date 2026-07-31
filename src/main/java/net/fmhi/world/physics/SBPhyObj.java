/*
 * MIT License — direct port of Starbound MovementController.
 */

package net.fmhi.world.physics;

import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Starbound-accurate physics: move-then-separate with up-first
 * slope correction. No workarounds — just the original algorithm.
 */
@NullMarked
public abstract class SBPhyObj {

  private static final float MAX_STEP = 0.4F;
  private static final float MAX_CORRECTION = 1.5F;
  private static final float SLIDE_CORRECTION_LIMIT = 0.2F;
  private static final float SLIDE_ANGLE = (float) (Math.PI / 3);
  private static final int MAX_SEPARATION_LOOPS = 3;
  private static final float SEPARATION_TOLERANCE = 0.001F;
  private static final Vector2 UP = new Vector2(0F, -1F);

  // -- state -------------------------------------------------------------

  protected PrecisePos position = PrecisePos.ZERO;
  protected Vector2 velocity = Vector2.ZERO;
  protected boolean onGround;
  private boolean wasOnGround;

  // -- parameters (MovementParameters equivalents) ------------------------

  protected float gravityMultiplier = 1F;
  protected float bounceFactor = 0F;
  protected boolean stopOnFirstBounce = false;
  protected boolean enableSurfaceSlopeCorrection = true;
  protected float maxMovementPerStep = MAX_STEP;
  protected float maximumCorrection = MAX_CORRECTION;
  protected boolean collisionEnabled = true;
  protected float maximumPlatformCorrection = 0.5F;
  protected float groundFriction = 0F;
  protected float airFriction = 0F;
  protected float liquidFriction = 0F;
  protected float mass = 1F;
  protected float slopeSlidingFactor = 5F;

  /**
   * If true, gravity is decomposed along the slope surface and the
   * entity slides down. If false (default), surface-slope-correction
   * keeps the entity on the slope.
   */
  protected boolean shouldSlideOnSlope = false;

  // ActorMovementController: "down" to drop through platforms
  private int fallThroughSustain;
  private static final int FALL_THROUGH_FRAMES = 10;

  // -- abstract ----------------------------------------------------------

  public abstract Polygon collisionPolygon();
  protected abstract float gravity();

  // -- accessors ---------------------------------------------------------

  public PrecisePos position() {
    return position;
  }

  public PrecisePos center() {
    Box2D bb = collisionPolygon().boundBox().translate(position.toVector2());
    return new PrecisePos(bb.centralX(), bb.centralY());
  }

  public void setPosition(PrecisePos pos) {
    this.position = pos;
  }
  public Vector2 velocity() { return velocity; }
  public void setVelocity(Vector2 v) { this.velocity = v; }
  public void setVelocity(float vx, float vy) { this.velocity = new Vector2(vx, vy); }
  public boolean onGround() { return onGround; }

  /** Call when the "down" key is held. */
  public void ignorePlatformTemporarily() {
    fallThroughSustain = FALL_THROUGH_FRAMES;
  }

  // -- tick --------------------------------------------------------------

  /** Surface slope from the last collision step, for post-loop slope sliding. */
  private Vector2 lastGroundSlope = new Vector2(1F, 0F);

  public void tick(double dt, Level level) {
    float d = (float) dt;
    wasOnGround = onGround;

    final int MAX_SPEED = 64;
    if (Math.abs(velocity.x()) > MAX_SPEED) {
      velocity = new Vector2(Math.clamp(velocity.x(), -MAX_SPEED, MAX_SPEED), velocity.y());
    }
    if (Math.abs(velocity.y()) > MAX_SPEED) {
      velocity = new Vector2(velocity.x(), Math.clamp(velocity.y(), -MAX_SPEED, MAX_SPEED));
    }

    if (fallThroughSustain > 0) fallThroughSustain--;

    // Starbound: gravity is applied AFTER collision as an environmental
    // velocity. The working velocity (relativeVelocity) does NOT include
    // this frame's gravity. Slope sliding is also applied post-loop.
    Vector2 relativeVelocity = velocity;

    float mx = relativeVelocity.x() * d;
    float my = relativeVelocity.y() * d;
    float dist = (float) Math.sqrt(mx * mx + my * my);
    int steps = Math.max(1, (int) Math.floor(dist / maxMovementPerStep) + 1);
    float dtSteps = d / steps;

    onGround = false;
    float maxBlockFriction = 0F;
    lastGroundSlope = new Vector2(1F, 0F);

    for (int i = 0; i < steps; i++) {
      Vector2 movement = new Vector2(relativeVelocity.x() * dtSteps,
                                     relativeVelocity.y() * dtSteps);

      if (!collisionEnabled || collisionPolygon() == null) {
        position = new PrecisePos(position.x() + movement.x(), position.y() + movement.y());
        onGround = false;
        continue;
      }

      Polygon body = collisionPolygon().translate(position.xf(), position.yf());
      // Y-down: vy<0 = jumping (pass through platforms from below)
      boolean ignorePlatforms = fallThroughSustain > 0 || relativeVelocity.y() < 0F;
      float platCorrection = maximumPlatformCorrection;
      Vector2 bodyCenter = new Vector2(body.boundBox().centralX(), body.boundBox().centralY());

      var queryBounds = body.boundBox().inflate(maximumCorrection, maximumCorrection);
      queryBounds = net.fmhi.math.Box2D.getUnion(queryBounds,
          body.translate(movement.x(), movement.y()).boundBox());
      var collisions = queryCollisions(queryBounds, level);

      boolean doSlopeCorrection = enableSurfaceSlopeCorrection && !shouldSlideOnSlope;
      var result = collisionMove(collisions, body, movement, ignorePlatforms,
          doSlopeCorrection, maximumCorrection, platCorrection,
          bodyCenter, dtSteps);

      position = new PrecisePos(position.x() + result.movement.x(),
                                position.y() + result.movement.y());

      Vector2 correction = result.correction;
      onGround = !(gravity() == 0F) && result.onGround;
      lastGroundSlope = result.groundSlope;

      float effBounce = Math.max(bounceFactor, result.maxBounce);
      maxBlockFriction = Math.max(maxBlockFriction, result.maxFriction);

      // Starbound velocity response: modifies relativeVelocity
      if (correction.lengthSquared() > 0.0001F) {
        if (effBounce != 0F) {
          float corrMag = correction.length();
          Vector2 corrDir = correction.divide(corrMag);
          float vDot = corrDir.x() * relativeVelocity.x() + corrDir.y() * relativeVelocity.y();
          Vector2 adjustment = corrDir.multiply(-vDot);
          relativeVelocity = relativeVelocity.add(adjustment).add(adjustment.multiply(effBounce));
          if (stopOnFirstBounce) break;
        } else {
          float vx = relativeVelocity.x();
          float vy = relativeVelocity.y();
          float cx = correction.x();
          float cy = correction.y();
          if (vx < 0F && cx > 0F)       vx = Math.min(0F, vx + cx / dtSteps);
          else if (vx > 0F && cx < 0F)   vx = Math.max(0F, vx + cx / dtSteps);
          if (vy < 0F && cy > 0F)       vy = Math.min(0F, vy + cy / dtSteps);
          else if (vy > 0F && cy < 0F)   vy = Math.max(0F, vy + cy / dtSteps);
          relativeVelocity = new Vector2(vx, vy);
        }
      }
    }

    // -- post-collision: gravity + slope sliding (Starbound-style) ----------
    // Gravity is applied AFTER collision as an environmental velocity.
    // Y-down: gravity is positive Y.
    float envVy = gravity() * gravityMultiplier * d;

    // Slope sliding: decompose gravity along the slope surface.
    // Starbound formula (Y-up): -surfaceSlope * (sx*sy) * factor
    // Y-down equivalent:    +surfaceSlope * (sx*sy) * factor
    float envVx = 0F;
    float sx = lastGroundSlope.x();
    float sy = lastGroundSlope.y();
    if (onGround && slopeSlidingFactor != 0F && Math.abs(sy) > 0.0001F) {
      float slide = sx * sy * slopeSlidingFactor;
      envVx = sx * slide * d;
      envVy += sy * slide * d;
    }

    velocity = new Vector2(relativeVelocity.x() + envVx,
                           relativeVelocity.y() + envVy);

    // ground friction
    float effFric = Math.max(groundFriction, maxBlockFriction);
    if (onGround && effFric > 0F) {
      float keep = 1F - effFric * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y());
    }
  }

  // -- collisionMove ------------------------------------------------------

  private static final int NULL_COLLISION = 0;
  private static final int NONE = 1;
  private static final int PLATFORM = 2;
  private static final int BLOCK = 3;

  private static class ColPoly {
    Polygon poly;
    net.fmhi.math.Box2D polyBounds;
    Vector2 sortPosition;
    float sortDistance;
    float blockBounce;    // from Block.bounce()
    float blockFriction;  // from Block.friction()
    int collisionKind = BLOCK;
  }

  private static class MoveResult {
    Vector2 movement = Vector2.ZERO;
    Vector2 correction = Vector2.ZERO;
    boolean onGround;
    Vector2 groundSlope = new Vector2(1F, 0F);
    int collisionKind = NONE;
    float maxBounce;
    float maxFriction;
  }

  private static class SepResult {
    Vector2 correction = Vector2.ZERO;
    boolean solutionFound;
    int collisionKind = NONE;
    float maxBounce;
    float maxFriction;
  }

  private static MoveResult collisionMove(ArrayList<ColPoly> collisionPolys,
      Polygon body, Vector2 movement, boolean ignorePlatforms,
      boolean enableSlopeCorrection, float maximumCorrection,
      float maximumPlatformCorrection, Vector2 sortCenter, float dt) {

    if (body == null)
      return new MoveResult();

    Polygon translatedBody = body.translate(movement.x(), movement.y());
    Polygon checkBody = translatedBody;
    Vector2 totalCorrection = Vector2.ZERO;
    int maxCollided = NONE;
    float separationTolerance = SEPARATION_TOLERANCE * (dt * 60F);
    float platMax = maximumPlatformCorrection * (dt * 60F);

    SepResult separation = new SepResult();

    if (enableSlopeCorrection) {
      separation = collisionSeparate(collisionPolys, checkBody, ignorePlatforms,
          platMax, sortCenter, true, separationTolerance);
      totalCorrection = totalCorrection.add(separation.correction);
      checkBody = checkBody.translate(separation.correction.x(), separation.correction.y());
      maxCollided = Math.max(maxCollided, separation.collisionKind);
      Vector2 upwardResult = movement.add(separation.correction);
      float upMag = upwardResult.length();
      float horiz = Math.abs(upwardResult.x()) / Math.max(upMag, 0.0001F);
      float angleHoriz = (float) Math.acos(Math.clamp(horiz, -1F, 1F));

      if (separation.solutionFound)
        separation.solutionFound = upMag < SLIDE_CORRECTION_LIMIT || angleHoriz < SLIDE_ANGLE;

      if (separation.solutionFound) {
        if (totalCorrection.length() > maximumCorrection)
          separation.solutionFound = false;
      }
    }

    if (!separation.solutionFound) {
      checkBody = translatedBody;
      totalCorrection = Vector2.ZERO;
      for (int i = 0; i < MAX_SEPARATION_LOOPS; i++) {
        separation = collisionSeparate(collisionPolys, checkBody, ignorePlatforms,
            platMax, sortCenter, false, separationTolerance);
        totalCorrection = totalCorrection.add(separation.correction);
        checkBody = checkBody.translate(separation.correction.x(), separation.correction.y());
        maxCollided = Math.max(maxCollided, separation.collisionKind);

        if (totalCorrection.length() > maximumCorrection) {
          separation.solutionFound = false;
          break;
        }
        if (separation.solutionFound) break;
      }
    }

    if (!separation.solutionFound && movement.lengthSquared() > 0.0001F) {
      checkBody = body;
      totalCorrection = movement.multiply(-1F);
      for (int i = 0; i < MAX_SEPARATION_LOOPS; i++) {
        separation = collisionSeparate(collisionPolys, checkBody, true,
            platMax, sortCenter, false, separationTolerance);
        totalCorrection = totalCorrection.add(separation.correction);
        checkBody = checkBody.translate(separation.correction.x(), separation.correction.y());
        maxCollided = Math.max(maxCollided, separation.collisionKind);

        if (totalCorrection.length() > maximumCorrection) {
          separation.solutionFound = false;
          break;
        }
        if (separation.solutionFound) break;
      }
    }

    if (separation.solutionFound) {
      MoveResult r = new MoveResult();
      r.movement = movement.add(totalCorrection);
      r.correction = totalCorrection;
      r.onGround = totalCorrection.y() < -separationTolerance;
      r.collisionKind = maxCollided;
      r.maxBounce = separation.maxBounce;
      r.maxFriction = separation.maxFriction;

      // compute ground slope
      if (r.onGround) {
        float touchRad = 1; // scale with speed
        float touchRad2 = touchRad * touchRad;
        var touchingBounds = checkBody.boundBox().inflate(touchRad, touchRad);

        for (var cp : collisionPolys) {
          if (!cp.polyBounds.intersects(touchingBounds)) continue;
          for (int s = 0; s < cp.poly.sides(); s++) {
            var edge = cp.poly.sideAt(s);
            float edx = edge[1].x() - edge[0].x();
            float edy = edge[1].y() - edge[0].y();
            float len = (float) Math.sqrt(edx * edx + edy * edy);
            if (len < 0.0001F) continue;
            var cb = checkBody;
            for (int vi = 0; vi < cb.sides(); vi++) {
              var bv = cb.vertex(vi);
              float t = Math.clamp(
                  ((bv.x() - edge[0].x()) * edx + (bv.y() - edge[0].y()) * edy) / (len * len),
                  0F, 1F);
              float nx = edge[0].x() + t * edx;
              float ny = edge[0].y() + t * edy;
              float dx = bv.x() - nx;
              float dy = bv.y() - ny;
              if (dx * dx + dy * dy <= touchRad2) {
                r.groundSlope = new Vector2(edx / len, edy / len);
                // normalize x>0 so slope sliding formula works consistently
                if (r.groundSlope.x() < 0F)
                  r.groundSlope = r.groundSlope.multiply(-1F);
              }
            }
          }
        }
      }

      return r;
    } else {
      // Starbound: when separation fails completely, ZERO movement
      MoveResult r = new MoveResult();
      r.movement = Vector2.ZERO;
      r.correction = movement.multiply(-1F);
      r.onGround = false;
      r.collisionKind = maxCollided;
      r.maxBounce = separation.maxBounce;
      r.maxFriction = separation.maxFriction;
      return r;
    }
  }

  // -- collisionSeparate --------------------------------------------------

  private static SepResult collisionSeparate(ArrayList<ColPoly> collisionPolys,
      Polygon poly, boolean ignorePlatforms, float maximumPlatformCorrection,
      Vector2 sortCenter, boolean upward, float separationTolerance) {

    SepResult separation = new SepResult();
    boolean intersects = false;

    for (var cp : collisionPolys)
      cp.sortDistance = cp.sortPosition.subtract(sortCenter).lengthSquared();
    collisionPolys.sort(Comparator.comparingDouble(a -> a.sortDistance));

    Polygon correctedPoly = poly;
    var correctedBb = correctedPoly.boundBox();

    for (var cp : collisionPolys) {
      if ((ignorePlatforms && cp.collisionKind == PLATFORM)
          || !correctedBb.intersects(cp.poly.boundBox()))
        continue;

      IntersectResult ir;
      if (upward)
        ir = correctedPoly.directionalSatIntersection(cp.poly, UP, false);
      else if (cp.collisionKind == PLATFORM)
        ir = correctedPoly.directionalSatIntersection(cp.poly, UP, true);
      else
        ir = correctedPoly.satIntersection(cp.poly);

      if (cp.collisionKind == PLATFORM && ir.intersects()) {
        // Y-down: push-up (y<0) = stand on platform; push-down (y>=0) = jump-through
        if (ir.overlap().y() >= 0F || Math.abs(ir.overlap().y()) > maximumPlatformCorrection)
          ir = IntersectResult.NO_INTERSECT;
      }

      if (ir.intersects()) {
        intersects = true;
        correctedPoly = correctedPoly.translate(ir.overlap().x(), ir.overlap().y());
        correctedBb = correctedPoly.boundBox();
        separation.correction = separation.correction.add(ir.overlap());
        separation.collisionKind = Math.max(separation.collisionKind, cp.collisionKind);
        separation.maxBounce = Math.max(separation.maxBounce, cp.blockBounce);
        separation.maxFriction = Math.max(separation.maxFriction, cp.blockFriction);
      }
    }

    separation.solutionFound = true;
    float tol2 = separationTolerance * separationTolerance;
    if (intersects) {
      for (var cp : collisionPolys) {
        if (cp.collisionKind == PLATFORM
            || !correctedBb.intersects(cp.poly.boundBox()))
          continue;
        var ir = correctedPoly.satIntersection(cp.poly);
        if (ir.intersects() && ir.overlap().lengthSquared() > tol2) {
          separation.collisionKind = Math.max(separation.collisionKind, cp.collisionKind);
          separation.solutionFound = false;
          break;
        }
      }
    }

    return separation;
  }

  // -- queryCollisions ----------------------------------------------------

  private static ArrayList<ColPoly> queryCollisions(
      net.fmhi.math.Box2D region, Level level) {
    var list = new ArrayList<ColPoly>();
    int minBX = (int) Math.floor(region.minX());
    int maxBX = (int) Math.floor(region.maxX());
    int minBY = (int) Math.floor(region.minY());
    int maxBY = (int) Math.floor(region.maxY());

    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        var state = level.getBlock(new BlockPos(bx, by));
        if (state == null) continue;
        var poly = level.getBlockPolygon(new BlockPos(bx, by));
        if (poly == null || !poly.boundBox().intersects(region)) continue;

        var cp = new ColPoly();
        cp.poly = poly;
        cp.polyBounds = poly.boundBox();
        cp.sortPosition = new Vector2(bx + 0.5F, by + 0.5F);
        cp.collisionKind = state.block().collisionKind();
        cp.blockBounce = state.block().bounce();
        cp.blockFriction = state.block().friction();
        list.add(cp);
      }
    }
    return list;
  }

}
