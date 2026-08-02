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
 * Move-then-clip physics. After moving, all overlapping blocks push
 * the body out. If the body cannot be pushed to a non-overlapping
 * position, the movement is fully reverted.
 */
@NullMarked
public abstract class PhysicsObject {

  private static final float GRAVITY = 40F;
  private static final float MAX_STEP = 0.4F;
  private static final float SLIDE_LIMIT = 0.2F;
  private static final float SLIDE_ANGLE = (float) (Math.PI / 3);
  private static final float STEP_DOWN = 0.6F;
  private static final int MAX_LOOPS = 4;
  private static final float TOLERANCE = 0.001F;
  private static final Vector2 UP = new Vector2(0F, -1F);

  protected PrecisePos position = PrecisePos.ZERO;
  protected Vector2 velocity = Vector2.ZERO;
  protected boolean onGround;
  private boolean wasOnGround;

  protected float bounce = 0F;
  protected float friction = 0F;
  protected float drag = 0F;

  public abstract Polygon collisionPolygon();

  public PrecisePos position() { return position; }
  public void setPosition(PrecisePos pos) { this.position = pos; }
  public Vector2 velocity() { return velocity; }
  public void setVelocity(Vector2 v) { this.velocity = v; }
  public void setVelocity(float vx, float vy) { this.velocity = new Vector2(vx, vy); }
  public boolean onGround() { return onGround; }
  public void setPhysics(float bounce, float friction, float drag) {
    this.bounce = bounce; this.friction = friction; this.drag = drag;
  }

  public void tick(double dt, Level level) {
    float d = (float) dt;
    wasOnGround = onGround;
    float origX = position.xf();
    float origY = position.yf();

    velocity = new Vector2(velocity.x(), velocity.y() + GRAVITY * d);
    if (drag > 0F) {
      float keep = 1F - drag * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y() * keep);
    }

    float mx = velocity.x() * d;
    float my = velocity.y() * d;
    float dist = (float) Math.sqrt(mx * mx + my * my);
    int steps = Math.max(1, (int) Math.ceil(dist / MAX_STEP));
    float sx = mx / steps;
    float sy = my / steps;
    onGround = false;

    float curX = position.xf();
    float curY = position.yf();

    for (int step = 0; step < steps; step++) {
      float movedX = curX + sx;
      float movedY = curY + sy;
      var collisions = collectCollisionsSwept(collisionPolygon(), curX, curY, movedX, movedY, level);
      Polygon body = collisionPolygon().translate(movedX, movedY);

      // Starbound-style: Phase 1 (up-only slope cheat) → validate → else Phase 2
      var r = separate(collisions, body, true);
      if (r.solutionFound) {
        Vector2 res = new Vector2(sx + r.correction.x(), sy + r.correction.y());
        float mag = res.length();
        float angle = (float) Math.acos(
            Math.clamp(Math.abs(res.x()) / Math.max(mag, 0.0001F), -1F, 1F));
        if (mag >= SLIDE_LIMIT && angle >= SLIDE_ANGLE)
          r = new SepResult(r.correction, false, 0F, 0F);
        if (r.correction.length() > MAX_STEP * 3)
          r = new SepResult(r.correction, false, 0F, 0F);
      }

      if (!r.solutionFound) {
        // Phase 2: full SAT from moved position
        r = separateFull(collisions, body);
      }

      if (r.solutionFound) {
        float cx = r.correction.x();
        float cy = r.correction.y();
        // if correction opposes movement but doesn't stop it, reject
        // (prevents squeezing through tight gaps over multiple substeps)
        if ((sx > TOLERANCE && cx < -TOLERANCE && sx + cx > TOLERANCE)
            || (sx < -TOLERANCE && cx > TOLERANCE && sx + cx < -TOLERANCE)
            || (sy > TOLERANCE && cy < -TOLERANCE && sy + cy > TOLERANCE)
            || (sy < -TOLERANCE && cy > TOLERANCE && sy + cy < -TOLERANCE)) {
          r = new SepResult(r.correction, false, 0F, 0F);
        }
      }

      if (r.solutionFound) {
        curX = movedX + r.correction.x();
        curY = movedY + r.correction.y();
        if (r.correction.y() < -TOLERANCE) onGround = true;

        float cx = r.correction.x();
        float cy = r.correction.y();
        float effB = Math.max(bounce, r.maxBounce);
        float vx = velocity.x();
        float vy = velocity.y();
        if (Math.abs(cx) > TOLERANCE) {
          if (vx < 0F && cx > 0F)       vx = Math.abs(vx) * effB;
          else if (vx > 0F && cx < 0F)   vx = -Math.abs(vx) * effB;
          else if (Math.abs(vx) < 0.01F) vx = 0F;
        }
        if (Math.abs(cy) > TOLERANCE) {
          if (vy < 0F && cy > 0F)       vy = Math.abs(vy) * effB;
          else if (vy > 0F && cy < 0F)   vy = -Math.abs(vy) * effB;
          else if (Math.abs(vy) < 0.01F) vy = 0F;
        }
        velocity = new Vector2(vx, vy);
      } else {
        // stuck — revert to original, separate from there
        body = collisionPolygon().translate(origX, origY);
        collisions = collectCollisions(collisionPolygon(), origX, origY, level);
        r = separateFull(collisions, body);
        curX = origX + r.correction.x();
        curY = origY + r.correction.y();
        velocity = Vector2.ZERO;
        onGround = wasOnGround;
        break;
      }
    }

    position = new PrecisePos(curX, curY);

    // ground friction
    if (onGround && friction > 0F) {
      float keep = 1F - friction * d;
      velocity = new Vector2(velocity.x() * keep, velocity.y());
    }

    // step down
    if (wasOnGround && velocity.y() >= 0F) {
      float testY = position.yf() + STEP_DOWN;
      var collisions = collectCollisions(collisionPolygon(), position.xf(), testY, level);
      Polygon body = collisionPolygon().translate(position.xf(), testY);
      var result = separate(collisions, body, true);
      if (result.solutionFound && result.correction.y() < -TOLERANCE) {
        position = new PrecisePos(position.x(), testY + result.correction.y());
        onGround = true;
        velocity = new Vector2(velocity.x(), 0F);
      }
    }
  }

  // -- collision ----------------------------------------------------------

  private record ColPoly(Polygon poly, float sortDist,
                          float blockBounce, float blockFriction,
                          BlockPos blockPos) {}
  private record SepResult(Vector2 correction, boolean solutionFound,
                           float maxBounce, float maxFriction) {}

  private static ArrayList<ColPoly> collectCollisions(Polygon shape, float cx, float cy, Level level) {
    return collectCollisionsSwept(shape, cx, cy, cx, cy, level);
  }

  /** Collects blocks overlapping the swept AABB from (ox,oy) to (nx,ny). */
  private static ArrayList<ColPoly> collectCollisionsSwept(Polygon shape,
      float ox, float oy, float nx, float ny, Level level) {
    var oldBody = shape.translate(ox, oy);
    var newBody = shape.translate(nx, ny);
    var bb = Box2D.getUnion(oldBody.boundBox(), newBody.boundBox());
    int minBX = (int) Math.floor(bb.minX() - 1);
    int maxBX = (int) Math.floor(bb.maxX() + 1);
    int minBY = (int) Math.floor(bb.minY() - 1);
    int maxBY = (int) Math.floor(bb.maxY() + 1);
    float centerX = newBody.boundBox().centralX();
    float centerY = newBody.boundBox().centralY();
    var list = new ArrayList<ColPoly>();
    for (int bx = minBX; bx <= maxBX; bx++) {
      for (int by = minBY; by <= maxBY; by++) {
        var state = level.getBlock(new BlockPos(bx, by));
        if (state == null) continue;
        var poly = level.getBlockPolygon(new BlockPos(bx, by));
        if (poly == null || !poly.boundBox().intersects(bb.inflate(0.02F, 0.02F))) continue;
        float dx = poly.boundBox().centralX() - centerX;
        float dy = poly.boundBox().centralY() - centerY;
        list.add(new ColPoly(poly, dx * dx + dy * dy,
            state.bounce(), state.friction(),
            new BlockPos(bx, by)));
      }
    }
    list.sort(Comparator.comparingDouble(ColPoly::sortDist));
    return list;
  }

  /**
   * Starbound-style collisionSeparate: pushes the body out of all
   * overlapping blocks in one pass. If {@code upward}, only vertical
   * (up) separation is allowed. Verifies no remaining overlaps at the end.
   */
  private static SepResult separate(ArrayList<ColPoly> collisions, Polygon body, boolean upward) {
    var cur = body;
    float cx = 0F;
    float cy = 0F;
    float maxB = 0F;
    float maxF = 0F;

    for (var cp : collisions) {
      if (!cur.boundBox().intersects(cp.poly.boundBox())) continue;
      var r = upward ? cur.directionalSatIntersection(cp.poly, UP, false)
                     : cur.satIntersection(cp.poly);
      if (r.intersects()) {
        cur = cur.translate(r.overlap().x(), r.overlap().y());
        cx += r.overlap().x(); cy += r.overlap().y();
        maxB = Math.max(maxB, cp.blockBounce);
        maxF = Math.max(maxF, cp.blockFriction);
      }
    }

    // verify: no remaining overlaps
    var bb = cur.boundBox();
    for (var cp : collisions) {
      if (!bb.intersects(cp.poly.boundBox())) continue;
      var r = cur.satIntersection(cp.poly);
      if (r.intersects() && r.overlap().lengthSquared() > TOLERANCE * TOLERANCE)
        return new SepResult(new Vector2(cx, cy), false, maxB, maxF);
    }
    return new SepResult(new Vector2(cx, cy), true, maxB, maxF);
  }

  /** Iterative full-SAT separation, up to MAX_LOOPS passes. */
  private static SepResult separateFull(ArrayList<ColPoly> collisions, Polygon body) {
    float tx = 0F;
    float ty = 0F;
    float maxB = 0F;
    float maxF = 0F;
    var cur = body;
    for (int loop = 0; loop < MAX_LOOPS; loop++) {
      var r = separate(collisions, cur, false);
      tx += r.correction.x(); ty += r.correction.y();
      maxB = Math.max(maxB, r.maxBounce); maxF = Math.max(maxF, r.maxFriction);
      if (r.solutionFound) return new SepResult(new Vector2(tx, ty), true, maxB, maxF);
      cur = cur.translate(r.correction.x(), r.correction.y());
    }
    return new SepResult(new Vector2(tx, ty), false, maxB, maxF);
  }
}
