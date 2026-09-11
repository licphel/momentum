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

import io.viki.momentum.gfx.util.VertexBuilder2D;

import java.util.List;

/**
 * A drawable texture driven by an {@link Animation}.
 *
 * <p>Tracks elapsed time, the current frame index, and whether a
 * non-looping animation has finished. Call {@link #update(float)} each
 * frame to advance the animation, then draw via
 * {@link Drawable2D#draw(VertexBuilder2D, float, float, float, float)}.
 *
 * @see Animation
 */
public final class AnimatedTexture implements Drawable2D {
  private final Animation animation;
  private float time;
  private int frameIndex;
  private boolean finished;

  /**
   * Creates a new animated texture starting at the first frame.
   *
   * @param animation the animation to play
   */
  public AnimatedTexture(Animation animation) {
    this.animation = animation;
  }

  /**
   * Advances the animation by the given time delta.
   *
   * <p>For looping animations, the frame index wraps around. For non-looping
   * animations, playback stops at the last frame and subsequent calls have
   * no effect.
   *
   * @param deltaTime the elapsed time in seconds since the last frame
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
   * Returns the texture region for the current frame.
   *
   * @return the texture region to draw
   */
  public TexturePart currentFrame() {
    return animation.frames().get(frameIndex).region();
  }

  /**
   * Returns the current frame index.
   *
   * @return the zero-based frame index
   */
  public int currentFrameIndex() {
    return frameIndex;
  }

  /**
   * Returns whether a non-looping animation has finished playback.
   *
   * @return {@code true} if playback has completed
   */
  public boolean isFinished() {
    return finished;
  }

  /**
   * Returns the underlying animation.
   *
   * @return the animation
   */
  public Animation animation() {
    return animation;
  }

  /**
   * Resets playback to the first frame.
   */
  public void reset() {
    time = 0;
    frameIndex = 0;
    finished = false;
  }

  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h) {
    g.drawTexture(currentFrame(), x, y, w, h);
  }

  @Override
  public void draw(VertexBuilder2D g, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    g.drawTexture(currentFrame(), x, y, w, h, u, v, uw, vh);
  }
}
