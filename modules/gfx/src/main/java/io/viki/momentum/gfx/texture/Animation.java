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

import io.viki.momentum.math.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * A named sequence of animation frames.
 *
 * <p>Each frame references a {@link TexturePart} and has a display duration
 * in seconds.
 *
 * @param frames the ordered list of frames
 * @param loop   whether the animation restarts after the last frame
 */
public record Animation(List<Frame> frames, boolean loop) {
  /**
   * Creates a looping animation.
   *
   * @param frames the ordered list of frames
   */
  public Animation(List<Frame> frames) {
    this(frames, true);
  }

  /**
   * Creates an animation from a sprite sheet by slicing it into a grid
   * and taking every other row (even rows, 0-indexed).
   *
   * @param sheet       the sprite sheet texture
   * @param frameWidth  the width of each frame in pixels
   * @param frameHeight the height of each frame in pixels
   * @param duration    the duration of each frame in seconds
   * @param loop        whether the animation loops
   * @return a new Animation with frames from even rows
   */
  public static Animation fromEvenRows(
      Texture sheet,
      int frameWidth,
      int frameHeight,
      float duration,
      boolean loop) {
    return fromEvenRows(sheet, frameWidth, frameHeight, duration, loop, 0, 0);
  }

  /**
   * Creates an animation from a sprite sheet by slicing it into a grid
   * and taking every other row (even rows, 0-indexed), with optional offset.
   *
   * @param sheet       the sprite sheet texture
   * @param frameWidth  the width of each frame in pixels
   * @param frameHeight the height of each frame in pixels
   * @param duration    the duration of each frame in seconds
   * @param loop        whether the animation loops
   * @param offsetX     horizontal offset in pixels from the top-left of the sheet
   * @param offsetY     vertical offset in pixels from the top-left of the sheet
   * @return a new Animation with frames from even rows
   */
  public static Animation fromEvenRows(
      Texture sheet,
      int frameWidth,
      int frameHeight,
      float duration,
      boolean loop,
      int offsetX,
      int offsetY) {
    List<Frame> frames = new ArrayList<>();
    int cols = (sheet.width() - offsetX) / frameWidth;
    int rows = (sheet.height() - offsetY) / frameHeight;

    for (int row = 0; row < rows; row += 2) {
      for (int col = 0; col < cols; col++) {
        int x = offsetX + col * frameWidth;
        int y = offsetY + row * frameHeight;
        TexturePart region = new TexturePart(sheet, Rectangle.of(x, y, frameWidth, frameHeight));
        frames.add(new Frame(region, duration));
      }
    }

    return new Animation(frames, loop);
  }

  /**
   * Creates an animation from a sprite sheet by slicing it into a grid
   * and taking every other column (even columns, 0-indexed).
   *
   * @param sheet       the sprite sheet texture
   * @param frameWidth  the width of each frame in pixels
   * @param frameHeight the height of each frame in pixels
   * @param duration    the duration of each frame in seconds
   * @param loop        whether the animation loops
   * @return a new Animation with frames from even columns
   */
  public static Animation fromEvenCols(
      Texture sheet,
      int frameWidth,
      int frameHeight,
      float duration,
      boolean loop) {
    return fromEvenCols(sheet, frameWidth, frameHeight, duration, loop, 0, 0);
  }

  /**
   * Creates an animation from a sprite sheet by slicing it into a grid
   * and taking every other column (even columns, 0-indexed), with optional offset.
   *
   * @param sheet       the sprite sheet texture
   * @param frameWidth  the width of each frame in pixels
   * @param frameHeight the height of each frame in pixels
   * @param duration    the duration of each frame in seconds
   * @param loop        whether the animation loops
   * @param offsetX     horizontal offset in pixels from the top-left of the sheet
   * @param offsetY     vertical offset in pixels from the top-left of the sheet
   * @return a new Animation with frames from even columns
   */
  public static Animation fromEvenCols(
      Texture sheet,
      int frameWidth,
      int frameHeight,
      float duration,
      boolean loop,
      int offsetX,
      int offsetY) {
    List<Frame> frames = new ArrayList<>();
    int cols = (sheet.width() - offsetX) / frameWidth;
    int rows = (sheet.height() - offsetY) / frameHeight;

    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col += 2) {
        int x = offsetX + col * frameWidth;
        int y = offsetY + row * frameHeight;
        TexturePart region = new TexturePart(sheet, Rectangle.of(x, y, frameWidth, frameHeight));
        frames.add(new Frame(region, duration));
      }
    }

    return new Animation(frames, loop);
  }

  /**
   * A single frame within an animation.
   *
   * @param region   the texture region to display
   * @param duration the frame duration in seconds
   */
  public record Frame(TexturePart region, float duration) {
  }
}