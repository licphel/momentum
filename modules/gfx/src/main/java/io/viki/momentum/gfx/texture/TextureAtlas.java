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

package io.viki.momentum.gfx.texture;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Cube;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stitches images into a single GPU texture using rectangle packing.
 *
 * <p>The atlas starts at a fixed initial size and expands when no free
 * rectangle fits an incoming image. Each call to {@link #accept(ImageInfo)}
 * returns a {@link TexturePart} backed by a {@link FragileTexture} that
 * resolves to the current backing texture, remaining valid even after
 * expansion.
 *
 * <p>Must be closed via {@link #close()} to release GPU resources.
 */
public final class TextureAtlas implements AutoCloseable {
  private static final int INITIAL_SIZE = 64;

  private final Device device;
  /** Empty border kept around every inserted region (anti-bleed for linear filtering). */
  private final int padding;
  private final AtlasRef ref = new AtlasRef();
  private final List<Rect> freeRects = new ArrayList<>();
  private Texture texture;
  private int size;
  private boolean disposed;

  /**
   * Creates an empty atlas with an initial backing texture and no padding.
   *
   * @param device the graphics device used to allocate the backing texture
   */
  public TextureAtlas(Device device) {
    this(device, 0);
  }

  /**
   * Creates an empty atlas with the given padding.
   *
   * @param device  the graphics device used to allocate the backing texture
   * @param padding empty border kept around every inserted region, in pixels
   */
  public TextureAtlas(Device device, int padding) {
    this.device = device;
    this.padding = Math.max(0, padding);
    size = INITIAL_SIZE;
    freeRects.add(new Rect(0, 0, size, size));
    texture = createTexture(size);
    ref.set(texture);
  }

  /**
   * Inserts an image into the atlas and returns its texture region.
   *
   * <p>The atlas expands automatically if no free rectangle fits the image.
   *
   * @param image the decoded image to insert
   * @return a texture part spanning the inserted region
   * @throws IllegalStateException if the atlas has been closed
   */
  public TexturePart accept(ImageInfo image) {
    return accept(image.pixels(), image.width(), image.height());
  }

  /**
   * Inserts raw RGBA pixels into the atlas and returns its texture region.
   *
   * <p>The atlas expands automatically if no free rectangle fits the image.
   *
   * @param pixels RGBA8 pixels, first row = top
   * @param width  the image width in pixels
   * @param height the image height in pixels
   * @return a texture part spanning the inserted region
   * @throws IllegalStateException if the atlas has been closed
   */
  public TexturePart accept(byte[] pixels, int width, int height) {
    if (disposed) {
      throw new IllegalStateException("Atlas is disposed");
    }

    Rect dst = new Rect();
    while (!find(width, height, dst)) {
      expand();
    }

    texture.submit(pixels, Cube.create(dst.x, dst.y, 0, dst.w, dst.h, 1));
    return new TexturePart(ref, Rectangle.create(dst.x, dst.y, dst.w, dst.h));
  }

  /**
   * Returns the current backing texture.
   *
   * @return the backing texture
   */
  public Texture texture() {
    return texture;
  }

  /**
   * Returns the current edge size of the atlas in pixels.
   *
   * @return the edge size in pixels
   */
  public int size() {
    return size;
  }

  @Override
  public void close() {
    if (disposed) {
      return;
    }
    disposed = true;
    texture.close();
  }

  /**
   * Searches for the best-fit free rectangle for the given dimensions.
   *
   * @param width  the requested width in pixels
   * @param height the requested height in pixels
   * @param result receives the allocated rectangle on success
   * @return {@code true} if a rectangle was found
   */
  private boolean find(int width, int height, Rect result) {
    int best = -1;
    int bestScore = Integer.MAX_VALUE;

    for (int i = 0; i < freeRects.size(); i++) {
      Rect fr = freeRects.get(i);
      if (fr.w < width + padding || fr.h < height + padding) {
        continue;
      }

      int score = Math.min(fr.w - (width + padding), fr.h - (height + padding));
      if (score < bestScore) {
        bestScore = score;
        best = i;
      }
    }

    if (best == -1) {
      return false;
    }

    Rect used = freeRects.remove(best);
    int dx = used.x;
    int dy = used.y;
    int remainW = used.w - (width + padding);
    int remainH = used.h - (height + padding);

    Rect right1 = new Rect(used.x + width + padding, used.y, remainW, height + padding);
    Rect top1 = new Rect(used.x, used.y + height + padding, used.w, remainH);
    Rect top2 = new Rect(used.x, used.y + height + padding, width + padding, remainH);
    Rect right2 = new Rect(used.x + width + padding, used.y, remainW, used.h);

    if (remainW > 0 && remainH > 0) {
      int waste1 = Math.abs(right1.w * right1.h - top1.w * top1.h);
      int waste2 = Math.abs(right2.w * right2.h - top2.w * top2.h);
      if (waste1 <= waste2) {
        append(right1);
        append(top1);
      } else {
        append(right2);
        append(top2);
      }
    } else if (remainW > 0) {
      append(new Rect(used.x + width + padding, used.y, remainW, height + padding));
    } else if (remainH > 0) {
      append(new Rect(used.x, used.y + height + padding, width + padding, remainH));
    }

    merge();
    result.x = dx;
    result.y = dy;
    result.w = width;
    result.h = height;
    return true;
  }

  private void append(Rect rect) {
    if (rect.w > 0 && rect.h > 0) {
      freeRects.add(rect);
    }
  }

  /** Merges adjacent free rectangles to reduce fragmentation. */
  @SuppressWarnings("all")
  private void merge() {
    boolean merged;
    do {
      merged = false;
      outer:
      for (int i = 0; i < freeRects.size(); i++) {
        Rect a = freeRects.get(i);
        if (a.w == 0 || a.h == 0) {
          continue;
        }
        for (int j = i + 1; j < freeRects.size(); j++) {
          Rect b = freeRects.get(j);
          if (b.w == 0 || b.h == 0) {
            continue;
          }

          // Vertical adjacency — same x and width
          if (a.x == b.x && a.w == b.w) {
            if (a.y + a.h == b.y) {
              a.h += b.h;
              freeRects.remove(j);
              merged = true;
              break outer;
            }
            if (b.y + b.h == a.y) {
              a.y = b.y;
              a.h += b.h;
              freeRects.remove(j);
              merged = true;
              break outer;
            }
          }
          // Horizontal adjacency — same y and height
          if (a.y == b.y && a.h == b.h) {
            if (a.x + a.w == b.x) {
              a.w += b.w;
              freeRects.remove(j);
              merged = true;
              break outer;
            }
            if (b.x + b.w == a.x) {
              a.x = b.x;
              a.w += b.w;
              freeRects.remove(j);
              merged = true;
              break outer;
            }
          }
        }
      }
    } while (merged);
  }

  /** Doubles the atlas size, copies old content, and adds new free space. */
  private void expand() {
    int oldSize = size;
    size *= 2;

    Texture newTex = createTexture(size);
    texture.blit(newTex, 0, 0, oldSize, oldSize, 0, 0, oldSize, oldSize);
    ref.set(newTex);
    texture.close();
    texture = newTex;

    freeRects.add(new Rect(oldSize, 0, oldSize, oldSize));
    freeRects.add(new Rect(0, oldSize, oldSize, oldSize));
    freeRects.add(new Rect(oldSize, oldSize, oldSize, oldSize));
  }

  private Texture createTexture(int s) {
    return device.getTexture(new TextureDesc.Builder()
        .width(s).height(s)
        .format(TextureFormat.RGBA8)
        .type(TextureType.TEXTURE_2D)
        .build());
  }

  /** Mutable integer rectangle used during packing. */
  private static class Rect {
    int x;
    int y;
    int w;
    int h;

    Rect() {
    }

    Rect(int x, int y, int w, int h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }
  }

  /** Mutable {@link FragileTexture} whose backing texture is updated when the atlas grows. */
  private static final class AtlasRef implements FragileTexture {
    private @Nullable Texture tex;

    void set(Texture t) {
      tex = t;
    }

    @Override
    public @Nullable Texture pin() {
      return tex;
    }
  }
}
