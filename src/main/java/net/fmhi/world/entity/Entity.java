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

package net.fmhi.world.entity;

import net.fmhi.math.Box2D;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.Beam;
import net.fmhi.world.light.LightBuffer;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.physics.SBPhyObj;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class Entity extends SBPhyObj {

  private final float w;
  private final float h;
  private ChunkPos chunkPos = new ChunkPos(0, 0);

  protected Entity(float width, float height) {
    this.w = width;
    this.h = height;
    // lighter than water, so entities float in liquids
    density = 0.85F;
  }

  public Box2D bounds() {
    return Box2D.create(position.xf(), position.yf(), w, h);
  }

  public float width() { return w; }
  public float height() { return h; }

  @Override public Polygon collisionPolygon() {
    return Polygon.fromBox(Box2D.create(0F, 0F, w, h));
  }

  @Override protected float gravity() { return 65; }

  public void tick(double dt, Level level) {
    super.tick(dt, level);
    ChunkPos newCp = new ChunkPos(
        Math.floorDiv((int) Math.floor(position.xf()), ChunkPos.SIZE),
        Math.floorDiv((int) Math.floor(position.yf()), ChunkPos.SIZE));
    if (!newCp.equals(chunkPos)) {
      Chunk old = level.getChunk(chunkPos);
      if (old != null) old.removeEntity(this);
      level.getOrLoadChunk(newCp).addEntity(this);
      chunkPos = newCp;
    }
  }

  /** Call once after spawning to place in the correct chunk. */
  public void enterChunk(Level level) {
    chunkPos = new ChunkPos(
        Math.floorDiv((int) Math.floor(position.xf()), ChunkPos.SIZE),
        Math.floorDiv((int) Math.floor(position.yf()), ChunkPos.SIZE));
    level.getOrLoadChunk(chunkPos).addEntity(this);
  }

  public boolean getLight(LightBuffer buf) { return false; }

  public @Nullable Beam getBeam(LightBuffer buf) { return null; }

  public static Entity player(PrecisePos pos) {
    var e = new Entity(1.4F, 2.65F) {
      @Override public Beam getBeam(LightBuffer buf) {
        buf.r(1.0F); buf.g(0.85F); buf.b(0.86F);
        return null;
      }

      @Override
      public boolean getLight(LightBuffer buf) {
        getBeam(buf);
        return true;
      }
    };
    e.setPosition(pos);
    return e;
  }
}
