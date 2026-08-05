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

import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.Color;
import net.fmhi.world.util.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Static definition of a multi-tile world object (Starbound {@code ObjectConfig}).
 *
 * <p>An object is an entity that floats above the tile grid: {@link #spaces()} list the
 * tiles it occupies (they must be empty at placement) and {@link #anchors()} the tiles
 * that support it (they must be solid; when an anchor is destroyed, the whole object
 * breaks — Starbound's {@code rooting}). Rendering uses {@link #texture()} when present,
 * otherwise the placeholder {@link #color()}.
 *
 * @see WorldObject
 */
public record ObjectConfig(String name,
                           List<BlockPos> spaces,
                           List<BlockPos> anchors,
                           boolean anchorAny,
                           boolean rooting,
                           @Nullable TexturePart texture,
                           @Nullable Color color) {

  public ObjectConfig {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("object name must not be empty");
    }
    if (spaces.isEmpty()) {
      throw new IllegalArgumentException("object must occupy at least one space");
    }
  }

  /** A config with the given placeholder color and no texture. */
  public static ObjectConfig colored(String name, List<BlockPos> spaces, List<BlockPos> anchors,
                                     boolean rooting, Color color) {
    return new ObjectConfig(name, spaces, anchors, false, rooting, null, color);
  }
}
