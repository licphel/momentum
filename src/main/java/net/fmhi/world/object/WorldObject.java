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

package net.fmhi.world.object;

import net.fmhi.math.Box2D;
import net.fmhi.world.block.Shape;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.PrecisePos;
import org.jspecify.annotations.NullMarked;

/**
 * A multi-tile world object placed in the world (Starbound {@code Object}).
 *
 * <p>The object is an entity anchored to a main tile ({@link #tileX()}/{@link #tileY()});
 * its {@link ObjectConfig#spaces()} declare the tiles it occupies (they are never
 * written to the tile grid — the object floats above it) and its anchors must stay
 * solid. The object is immovable: it skips physics, and when {@code rooting} anchors
 * fail it breaks (removes itself).
 */
@NullMarked
public class WorldObject extends Entity {

  private final ObjectConfig config;
  private final int tileX;
  private final int tileY;

  /**
   * Creates an object at the given main tile. The position is the tile's bottom-left
   * corner (the feet, Y-up), matching the entity convention.
   *
   * @param config the object definition
   * @param tileX  the main tile X
   * @param tileY  the main tile Y
   */
  public WorldObject(ObjectConfig config, int tileX, int tileY) {
    super(1F, 1F);
    this.config = config;
    this.tileX = tileX;
    this.tileY = tileY;
    setPosition(new PrecisePos(tileX, tileY));
  }

  /**
   * Returns the object definition.
   *
   * @return the object config
   */
  public ObjectConfig config() {
    return config;
  }

  /**
   * Returns the main tile X.
   *
   * @return the main tile X
   */
  public int tileX() {
    return tileX;
  }

  /**
   * Returns the main tile Y.
   *
   * @return the main tile Y
   */
  public int tileY() {
    return tileY;
  }

  /**
   * Returns the world tiles the object occupies.
   *
   * @return the occupied tile positions
   */
  public Box2D spaceBounds() {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    for (BlockPos s : config.spaces()) {
      minX = Math.min(minX, s.x());
      minY = Math.min(minY, s.y());
      maxX = Math.max(maxX, s.x());
      maxY = Math.max(maxY, s.y());
    }
    return Box2D.create(tileX + minX, tileY + minY, maxX - minX + 1F, maxY - minY + 1F);
  }

  /**
   * The object is immovable: physics is skipped entirely. While {@code rooting}, an
   * anchor that is no longer solid breaks the whole object (it removes itself from
   * its chunk).
   *
   * @param dt    the frame time
   * @param level the level
   */
  @Override
  public void tick(double dt, Level level) {
    if (!config.rooting() || config.anchors().isEmpty()) {
      return;
    }
    boolean anyValid = false;
    for (BlockPos a : config.anchors()) {
      if (level.getBlock(tileX + a.x(), tileY + a.y()).shape() == Shape.SOLID) {
        anyValid = true;
      } else if (!config.anchorAny()) {
        breakObject(level);
        return;
      }
    }
    if (config.anchorAny() && !anyValid) {
      breakObject(level);
    }
  }

  /** Removes the object from its chunk (the anchor failed). */
  private void breakObject(Level level) {
    Chunk c = level.getChunk(chunkPos);
    if (c != null) {
      c.removeEntity(this);
    }
  }
}
