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

package net.fmhi.render;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.io.PngInputStream;
import net.fmhi.gfx.texture.TextureAtlas;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.util.ResourceProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The process-wide texture atlas holding every shared tile sheet.
 *
 * <p>All tileset images are stitched into a single GPU texture on
 * {@link #init(Device)} and addressed by name via {@link #get(String)};
 * the parts stay valid even when the atlas grows. Initialization is
 * idempotent, so any renderer may call it first.
 */
@NullMarked
public final class GlobalAtlas {
  /** Empty border kept around every inserted image (anti-bleed). */
  private static final int PADDING = 1;

  private static @Nullable TextureAtlas atlas;
  private static final Map<String, TexturePart> parts = new HashMap<>();

  private GlobalAtlas() {
  }

  /**
   * Loads the built-in tile sheets into the shared atlas.
   *
   * @param device the device used to allocate the backing texture
   * @throws RuntimeException if a sheet fails to load
   */
  public static void init(Device device) {
    if (atlas != null) {
      return;
    }
    TextureAtlas a = new TextureAtlas(device, PADDING);
    try {
      ResourceProvider rp = ResourceProvider.classpath(GlobalAtlas.class);
      parts.put("dirt", a.accept(new PngInputStream(rp.openStream("/dirt.png")).info()));
      parts.put("stone", a.accept(new PngInputStream(rp.openStream("/stone.png")).info()));
    } catch (Exception e) {
      a.close();
      throw new RuntimeException("Failed to load tile sheets into the global atlas", e);
    }
    atlas = a;
  }

  /**
   * Returns the texture part registered under the given name.
   *
   * @param name the sheet name, e.g. {@code "dirt"}
   * @return the registered part, or {@code null} if not loaded
   */
  public static @Nullable TexturePart get(String name) {
    return parts.get(name);
  }

  /** Releases the atlas texture and forgets all parts (idempotent). */
  public static void dispose() {
    if (atlas != null) {
      atlas.close();
    }
    atlas = null;
    parts.clear();
  }
}
