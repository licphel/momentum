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
import net.fmhi.world.level.Level;
import net.fmhi.world.light.Beam;
import net.fmhi.world.light.Channel;
import net.fmhi.world.physics.SBPhyObj;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@NullMarked
public class Entity extends SBPhyObj {

  private final float w;
  private final float h;
  public ChunkPos chunkPos = new ChunkPos(0, 0);
  public long lastTick;

  protected Entity(float width, float height) {
    this.w = width;
    this.h = height;
    // lighter than water, so entities float in liquids
    density = 0.85F;
  }

  @Override
  public Box2D collisionBox() {
    // local box, bottom-left origin (the position is the feet, Y-up)
    return Box2D.create(0F, 0F, w, h);
  }

  @Override
  protected float gravity() {
    return 65F;
  }

  public void tick(double dt, Level level) {
    super.tick(dt, level);
  }

  /** Call once after spawning to place in the correct chunk. */
  public void enterChunk(Level level) {
    chunkPos = new ChunkPos(
        Math.floorDiv((int) Math.floor(position.xf()), ChunkPos.SIZE),
        Math.floorDiv((int) Math.floor(position.yf()), ChunkPos.SIZE));
    level.getOrLoadChunk(chunkPos).addEntity(this);
  }

  /** The ambient light emitted by this entity on one channel, or {@code 0}. */
  public float emitAmbient(byte channel) {
    return 0F;
  }

  /** The directional beams emitted by this entity; the caller draws and
   * recycles them. */
  public Collection<Beam> emitBeams() {
    return Collections.emptySet();
  }

  public static Entity player(PrecisePos pos) {
    var e = new Entity(1.4F, 2.65F) {
      @Override
      public float emitAmbient(byte channel) {
        return switch (channel) {
          case Channel.RED -> 1F;
          case Channel.GREEN -> 0.9F;
          default -> 0.8F;
        };
      }
    };
    e.setPosition(pos);
    return e;
  }
}
