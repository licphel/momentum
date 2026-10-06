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
import io.viki.momentum.math.Cube;
import io.viki.momentum.math.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs images into fixed-size GPU texture pages.
 *
 * <p>A new page is allocated when existing pages cannot fit an incoming image.
 * Each returned {@link TexturePart} retains its own page and texel coordinates;
 * adding pages never relocates previously accepted images.
 *
 * <p>Not thread-safe. Calls must be confined to the resource-owning thread.
 *
 * <p>Must be closed via {@link #close()} to release GPU resources.
 */
public final class TextureAtlas implements AutoCloseable {
  /** Default square page edge in pixels. */
  public static final int DEFAULT_PAGE_SIZE = 2048;

  private final Device device;
  /** Empty border kept around every inserted region (anti-bleed for linear filtering). */
  private final int padding;
  private final int pageSize;
  private final List<Page> pages = new ArrayList<>();
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
    this(device, padding, DEFAULT_PAGE_SIZE);
  }

  /**
   * Creates an empty atlas with the given padding and page size.
   *
   * @param device   the graphics device used to allocate the backing texture
   * @param padding  empty border kept around every inserted region, in pixels
   * @param pageSize the square page size
   */
  public TextureAtlas(Device device, int padding, int pageSize) {
    if (padding < 0 || pageSize <= padding) {
      throw new IllegalArgumentException("Invalid atlas page size/padding: " + pageSize + "/" + padding);
    }
    this.device = device;
    this.padding = padding;
    this.pageSize = pageSize;
    pages.add(new Page());
  }

  /**
   * Inserts an image into the atlas and returns its texture region.
   *
   * <p>A new page is allocated if no existing page fits the image.
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
   * <p>A new page is allocated if no existing page fits the image.
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

    if (width <= 0 || height <= 0 || width > pageSize - padding || height > pageSize - padding) {
      throw new IllegalArgumentException("Image " + width + "x" + height + " does not fit atlas page " + pageSize);
    }
    Rect dst = new Rect();
    for (Page page : pages) {
      if (page.find(width, height, dst)) {
        return upload(page, pixels, dst);
      }
    }
    Page page = new Page();
    pages.add(page);
    page.find(width, height, dst);
    return upload(page, pixels, dst);
  }

  private TexturePart upload(Page page, byte[] pixels, Rect dst) {
    page.texture.submit(pixels, Cube.of(dst.x, dst.y, 0, dst.w, dst.h, 1));
    return new TexturePart(page.texture, Rectangle.of(dst.x, dst.y, dst.w, dst.h));
  }

  /**
   * Returns the texture of page N.
   *
   * @param page the page N
   * @return the page texture of the given slot
   */
  public Texture texture(int page) {
    return pages.get(page).texture;
  }

  /**
   * Gets the page count.
   *
   * @return the page count
   */
  public int pageCount() {
    return pages.size();
  }

  /**
   * Returns the page size.
   *
   * @return the page size
   */
  public int pageSize() {
    return pageSize;
  }

  @Override
  public void close() {
    if (disposed) {
      return;
    }
    disposed = true;
    for (Page page : pages) {
      page.texture.close();
    }
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

  private final class Page {
    private final Texture texture = device.getTexture(
        new TextureDesc.Builder()
            .width(pageSize)
            .height(pageSize)
            .format(TextureFormat.RGBA8)
            .type(TextureType.TEXTURE_2D)
            .build());
    private final List<Rect> freeRects = new ArrayList<>();

    private Page() {
      freeRects.add(new Rect(0, 0, pageSize, pageSize));
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
  }
}
