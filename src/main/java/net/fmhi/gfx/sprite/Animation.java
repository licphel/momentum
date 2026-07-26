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

package net.fmhi.gfx.sprite;

import net.fmhi.gfx.texture.TexturePart;

import java.util.List;

/**
 * A named sequence of animation frames.
 *
 * <p>Each frame references a {@link TexturePart} and has a display duration
 * in seconds.
 *
 * @param name   the animation identifier
 * @param frames the ordered list of frames
 * @param loop   whether the animation restarts after the last frame
 */
public record Animation(String name, List<Frame> frames, boolean loop) {
  /**
   * Creates a looping animation.
   *
   * @param name   the animation identifier
   * @param frames the ordered list of frames
   */
  public Animation(String name, List<Frame> frames) {
    this(name, frames, true);
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
