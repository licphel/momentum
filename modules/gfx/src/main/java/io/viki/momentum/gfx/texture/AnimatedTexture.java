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

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.DrawFlags;
import io.viki.momentum.gfx.util.VertexBuilder2D;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plays an {@link Animation} and reports its frame anchors in world coordinates. */
public final class AnimatedTexture implements Drawable2D {
  private final Animation animation;
  private final Map<Color, AnchorCallback> colorCallbacks = new LinkedHashMap<>();
  private float time;
  private int frameIndex;
  private boolean finished;
  private @Nullable AnchorCallback allAnchorsCallback;

  /**
   * Creates an animated texture starting at the first frame.
   *
   * @param animation animation to play
   */
  public AnimatedTexture(Animation animation) {
    this.animation = animation;
  }

  /**
   * Advances playback by the elapsed time.
   *
   * <p>Looping animations wrap to their first frame. Non-looping animations stop at their last
   * frame and remain finished until {@link #reset()} is called.
   *
   * @param deltaTime elapsed time in seconds, ignored when playback has finished
   */
  public void update(float deltaTime) {
    if (finished) {
      return;
    }
    List<Animation.Frame> frames = animation.frames();
    time += deltaTime;
    while (time >= frames.get(frameIndex).duration()) {
      time -= frames.get(frameIndex).duration();
      frameIndex++;
      if (frameIndex >= frames.size()) {
        if (animation.loop()) {
          frameIndex = 0;
        } else {
          frameIndex = frames.size() - 1;
          finished = true;
          break;
        }
      }
    }
  }

  /**
   * Registers a callback for every anchor in the current frame.
   *
   * <p>The callback receives the anchor pixel's world-space position. The conversion respects the
   * active vertical UV convention, including the camera's {@code flipY} setting, and the current
   * horizontal draw flag. Callbacks run immediately after the frame's texture is recorded.
   *
   * @param callback callback receiving each anchor position
   * @return this animated texture
   */
  public AnimatedTexture anchorCallback(AnchorCallback callback) {
    allAnchorsCallback = callback;
    return this;
  }

  /**
   * Registers a callback for one source anchor color.
   *
   * <p>The color is the marker color found in the source image, not the replacement color written
   * into the GPU texture.
   *
   * @param color source anchor color
   * @param callback callback receiving the selected anchor position
   * @return this animated texture
   */
  public AnimatedTexture anchorCallback(Color color, AnchorCallback callback) {
    colorCallbacks.put(color, callback);
    return this;
  }

  /**
   * Removes all registered anchor callbacks.
   *
   * @return this animated texture
   */
  public AnimatedTexture clearAnchorCallbacks() {
    colorCallbacks.clear();
    allAnchorsCallback = null;
    return this;
  }

  /**
   * Returns the current frame's texture region.
   *
   * @return current texture region
   */
  public TexturePart currentFrame() {
    return animation.frames().get(frameIndex).region();
  }

  /**
   * Returns the current frame's immutable anchor map.
   *
   * @return source anchor colors and pixel positions relative to the current frame
   */
  public Map<Color, Vector2> currentAnchors() {
    return animation.frames().get(frameIndex).anchors();
  }

  /**
   * Returns the current zero-based frame index.
   *
   * @return current frame index
   */
  public int currentFrameIndex() {
    return frameIndex;
  }

  /**
   * Returns whether a non-looping animation has completed playback.
   *
   * @return {@code true} when playback has completed
   */
  public boolean isFinished() {
    return finished;
  }

  /**
   * Returns the animation definition used by this texture.
   *
   * @return underlying animation
   */
  public Animation animation() {
    return animation;
  }

  /**
   * Resets playback to the first frame and clears the finished state.
   */
  public void reset() {
    time = 0.0F;
    frameIndex = 0;
    finished = false;
  }

  /**
   * Draws the current frame and reports its anchors.
   *
   * @param g vertex builder receiving the draw command
   * @param x lower-left world X coordinate
   * @param y lower-left world Y coordinate
   * @param w destination width in world units
   * @param h destination height in world units
   */
  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h) {
    TexturePart frame = currentFrame();
    g.drawTexture(frame, x, y, w, h);
    notifyAnchors(g, frame, x, y, w, h);
  }

  /**
   * Draws the current frame through a relative source region and reports its anchors.
   *
   * @param g vertex builder receiving the draw command
   * @param x lower-left world X coordinate
   * @param y lower-left world Y coordinate
   * @param w destination width in world units
   * @param h destination height in world units
   * @param u source X offset relative to the current frame
   * @param v source Y offset relative to the current frame
   * @param uw source width in pixels
   * @param vh source height in pixels
   */
  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h,
                   float u, float v, float uw, float vh) {
    TexturePart frame = currentFrame();
    g.drawTexture(frame, x, y, w, h, u, v, uw, vh);
    notifyAnchors(g, frame, x, y, w, h);
  }

  private void notifyAnchors(VertexBuilder2D g, TexturePart frame,
                             float x, float y, float w, float h) {
    if (allAnchorsCallback == null && colorCallbacks.isEmpty()) {
      return;
    }
    float scaleX = w / frame.width();
    float scaleY = h / frame.height();
    boolean flipX = (g.flags() & DrawFlags.FLIP_X) != 0;
    boolean flipY = g.isUvYFlipped() ^ ((g.flags() & DrawFlags.FLIP_Y) != 0);
    for (Map.Entry<Color, Vector2> entry : currentAnchors().entrySet()) {
      Vector2 anchor = entry.getValue();
      float worldX = flipX ? x + w - (anchor.x() + 1.0F) * scaleX : x + anchor.x() * scaleX;
      float worldY = flipY ? y + anchor.y() * scaleY : y + h - anchor.y() * scaleY;
      AnchorCallback selectedCallback = colorCallbacks.get(entry.getKey());
      if (selectedCallback != null) {
        selectedCallback.accept(worldX, worldY);
      }
      if (allAnchorsCallback != null) {
        allAnchorsCallback.accept(worldX, worldY);
      }
    }
  }

  /**
   * Receives an anchor's world-space position after draw-state conversion.
   */
  @FunctionalInterface
  public interface AnchorCallback {
    /**
     * Consumes an anchor position.
     *
     * @param x world-space X coordinate
     * @param y world-space Y coordinate
     */
    void accept(float x, float y);
  }
}
