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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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

import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines an ordered texture animation and the anchor metadata associated with each frame.
 *
 * <p>An animation definition is immutable after construction. The texture referenced by its
 * frames remains owned by the caller and follows the thread-safety contract of {@link Texture}.
 *
 * @param frames ordered frame list, which must contain at least one frame
 * @param loop   whether playback restarts after the last frame
 */
public record Animation(List<Frame> frames, boolean loop) {
  /**
   * Creates an animation definition.
   *
   * @param frames ordered frame list, which must contain at least one frame
   * @param loop   whether playback restarts after the last frame
   * @throws IllegalArgumentException if {@code frames} is empty
   */
  public Animation {
    frames = List.copyOf(frames);
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("Animation must contain at least one frame");
    }
  }

  /**
   * Creates a looping animation definition.
   *
   * @param frames ordered frame list, which must contain at least one frame
   * @throws IllegalArgumentException if {@code frames} is empty
   */
  public Animation(List<Frame> frames) {
    this(frames, true);
  }

  /**
   * Slices every frame in every other row of a sprite sheet.
   *
   * @param sheet       source texture
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @return animation containing the even rows
   * @throws IllegalArgumentException if the sheet contains no complete selected frame
   */
  public static Animation fromEvenRows(Texture sheet, int frameWidth, int frameHeight,
                                       float duration, boolean loop) {
    return fromEvenRows(sheet, frameWidth, frameHeight, duration, loop, 0, 0);
  }

  /**
   * Slices every frame in every other row of a sprite sheet from an offset.
   *
   * @param sheet       source texture
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @param offsetX     X coordinate of the first frame
   * @param offsetY     Y coordinate of the first frame
   * @return animation containing the even rows
   * @throws IllegalArgumentException if the sheet contains no complete selected frame
   */
  public static Animation fromEvenRows(Texture sheet, int frameWidth, int frameHeight,
                                       float duration, boolean loop, int offsetX, int offsetY) {
    List<Frame> frames = new ArrayList<>();
    int cols = (sheet.width() - offsetX) / frameWidth;
    int rows = (sheet.height() - offsetY) / frameHeight;
    for (int row = 0; row < rows; row += 2) {
      for (int col = 0; col < cols; col++) {
        frames.add(frame(sheet, offsetX + col * frameWidth, offsetY + row * frameHeight,
            frameWidth, frameHeight, duration));
      }
    }
    return new Animation(frames, loop);
  }

  /**
   * Slices every frame in every other column of a sprite sheet.
   *
   * @param sheet       source texture
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @return animation containing the even columns
   * @throws IllegalArgumentException if the sheet contains no complete selected frame
   */
  public static Animation fromEvenCols(Texture sheet, int frameWidth, int frameHeight,
                                       float duration, boolean loop) {
    return fromEvenCols(sheet, frameWidth, frameHeight, duration, loop, 0, 0);
  }

  /**
   * Slices every frame in every other column of a sprite sheet from an offset.
   *
   * @param sheet       source texture
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @param offsetX     X coordinate of the first frame
   * @param offsetY     Y coordinate of the first frame
   * @return animation containing the even columns
   * @throws IllegalArgumentException if the sheet contains no complete selected frame
   */
  public static Animation fromEvenCols(Texture sheet, int frameWidth, int frameHeight,
                                       float duration, boolean loop, int offsetX, int offsetY) {
    List<Frame> frames = new ArrayList<>();
    int cols = (sheet.width() - offsetX) / frameWidth;
    int rows = (sheet.height() - offsetY) / frameHeight;
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col += 2) {
        frames.add(frame(sheet, offsetX + col * frameWidth, offsetY + row * frameHeight,
            frameWidth, frameHeight, duration));
      }
    }
    return new Animation(frames, loop);
  }

  /**
   * Creates a horizontal animation and records pixels matching the supplied anchor color.
   *
   * @param sheet       source texture with top-left pixel coordinates
   * @param anchorColor color identifying the attachment pixel
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param frameCount  number of consecutive frames
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @return animation containing the horizontal frames
   * @throws IllegalArgumentException if {@code frameCount} is not positive
   */
  public static Animation fromHorizontalRow(Texture sheet, Color anchorColor,
                                            int frameWidth, int frameHeight, int frameCount,
                                            float duration, boolean loop) {
    return fromHorizontalRow(sheet, anchorColor, frameWidth, frameHeight, frameCount,
        duration, loop, 0, 0);
  }

  /**
   * Creates a horizontal animation at the supplied texture offset and anchor color.
   *
   * @param sheet       source texture with top-left pixel coordinates
   * @param anchorColor color identifying the attachment pixel
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param frameCount  number of consecutive frames
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @param offsetX     X coordinate of the first frame
   * @param offsetY     Y coordinate of the first frame
   * @return animation containing the horizontal frames
   * @throws IllegalArgumentException if {@code frameCount} is not positive
   */
  public static Animation fromHorizontalRow(Texture sheet, Color anchorColor,
                                            int frameWidth, int frameHeight, int frameCount,
                                            float duration, boolean loop,
                                            int offsetX, int offsetY) {
    List<Frame> frames = new ArrayList<>(frameCount);
    for (int index = 0; index < frameCount; index++) {
      int frameX = offsetX + index * frameWidth;
      Map<Color, Vector2> anchors = getAnchors(
          sheet, anchorColor, frameX, offsetY, frameWidth, frameHeight);
      TexturePart region = new TexturePart(
          sheet, Rectangle.of(frameX, offsetY, frameWidth, frameHeight));
      frames.add(new Frame(region, duration, anchors));
    }
    return new Animation(frames, loop);
  }

  /**
   * Creates a horizontal animation from an atlas-backed sheet.
   *
   * <p>The decoded image is used only for anchor discovery; the returned frames retain the
   * supplied atlas region and therefore do not create a standalone texture.
   *
   * @param sheet       atlas-backed sheet region
   * @param image       decoded source image corresponding to {@code sheet}
   * @param anchorColor color identifying the attachment pixel
   * @param frameWidth  frame width in pixels
   * @param frameHeight frame height in pixels
   * @param frameCount  number of consecutive frames
   * @param duration    duration of each frame in seconds
   * @param loop        whether playback restarts after the last frame
   * @param offsetX     X coordinate of the first frame in the source image
   * @param offsetY     Y coordinate of the first frame in the source image
   * @return animation containing atlas-backed horizontal frames
   * @throws IllegalArgumentException if {@code frameCount} is not positive
   */
  public static Animation fromHorizontalRow(TexturePart sheet, ImageInfo image,
                                            Color anchorColor, int frameWidth, int frameHeight,
                                            int frameCount, float duration, boolean loop,
                                            int offsetX, int offsetY) {
    List<Frame> frames = new ArrayList<>(frameCount);
    for (int index = 0; index < frameCount; index++) {
      int frameX = offsetX + index * frameWidth;
      Map<Color, Vector2> anchors = getAnchors(
          image, anchorColor, frameX, offsetY, frameWidth, frameHeight);
      TexturePart region = new TexturePart(
          sheet, Rectangle.of(frameX, offsetY, frameWidth, frameHeight));
      frames.add(new Frame(region, duration, anchors));
    }
    return new Animation(frames, loop);
  }

  private static Frame frame(Texture sheet, int x, int y, int width, int height, float duration) {
    return new Frame(new TexturePart(sheet, Rectangle.of(x, y, width, height)), duration);
  }

  /**
   * Finds the first occurrence of an anchor color within a texture frame.
   *
   * @param sheet the texture containing the frame
   * @param anchorColor the color that identifies the anchor
   * @param frameX the frame's left coordinate in the texture
   * @param frameY the frame's top coordinate in the texture
   * @param frameWidth the frame width in pixels
   * @param frameHeight the frame height in pixels
   * @return anchor metadata, empty when the color does not occur in the frame
   */
  private static Map<Color, Vector2> getAnchors(Texture sheet, Color anchorColor,
                                                int frameX, int frameY,
                                                int frameWidth, int frameHeight) {
    Map<Color, Vector2> anchors = new LinkedHashMap<>();
    int anchorRgba = anchorColor.packRgba8();
    for (int y = 0; y < frameHeight; y++) {
      for (int x = 0; x < frameWidth; x++) {
        int pixel = sheet.pixel(frameX + x, frameY + y, 0);
        if (pixel == anchorRgba) {
          anchors.putIfAbsent(anchorColor, new Vector2(x, y));
        }
      }
    }
    return Map.copyOf(anchors);
  }

  /**
   * Finds the first occurrence of an anchor color within a decoded image frame.
   *
   * @param sheet the image containing the frame
   * @param anchorColor the color that identifies the anchor
   * @param frameX the frame's left coordinate in the image
   * @param frameY the frame's top coordinate in the image
   * @param frameWidth the frame width in pixels
   * @param frameHeight the frame height in pixels
   * @return anchor metadata, empty when the color does not occur in the frame
   */
  private static Map<Color, Vector2> getAnchors(ImageInfo sheet, Color anchorColor,
                                                int frameX, int frameY,
                                                int frameWidth, int frameHeight) {
    Map<Color, Vector2> anchors = new LinkedHashMap<>();
    int anchorRgba = anchorColor.packRgba8();
    int channels = sheet.channels();
    byte[] pixels = sheet.pixels();
    for (int y = 0; y < frameHeight; y++) {
      for (int x = 0; x < frameWidth; x++) {
        int offset = ((frameY + y) * sheet.width() + frameX + x) * channels;
        int red = Byte.toUnsignedInt(pixels[offset]);
        int green = channels > 1 ? Byte.toUnsignedInt(pixels[offset + 1]) : red;
        int blue = channels > 2 ? Byte.toUnsignedInt(pixels[offset + 2]) : green;
        int alpha = channels > 3 ? Byte.toUnsignedInt(pixels[offset + 3]) : 255;
        if (Color.packRgba8(red, green, blue, alpha) == anchorRgba) {
          anchors.putIfAbsent(anchorColor, new Vector2(x, y));
        }
      }
    }
    return Map.copyOf(anchors);
  }

  /**
   * Describes one drawable frame and its source-image anchor locations.
   *
   * @param region   texture region to draw
   * @param duration frame duration in seconds
   * @param anchors  anchor colors and their pixel positions relative to the frame
   */
  public record Frame(TexturePart region, float duration, Map<Color, Vector2> anchors) {
    /**
     * Creates a frame and freezes its anchor metadata.
     *
     * @param region   texture region to draw
     * @param duration frame duration in seconds
     * @param anchors  anchor colors and their pixel positions relative to the frame
     */
    public Frame {
      anchors = Map.copyOf(anchors);
    }

    /**
     * Creates a frame without anchors.
     *
     * @param region   texture region to draw
     * @param duration frame duration in seconds
     */
    public Frame(TexturePart region, float duration) {
      this(region, duration, Map.of());
    }
  }
}
