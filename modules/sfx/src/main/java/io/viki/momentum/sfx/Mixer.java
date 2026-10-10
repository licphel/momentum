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

package io.viki.momentum.sfx;

import io.viki.momentum.math.Quaternion;
import io.viki.momentum.sfx.ext.SfxEffect;

/**
 * Represents an audio output device.
 *
 * <p>A mixer owns the connection to the underlying audio backend and is the
 * factory for creating {@link Clip} instances. Mixers are long-lived resources; close them only when shutting down the
 * audio subsystem.
 *
 * <p>Implementations define their own thread-safety guarantees. Calls that
 * affect playback or listener state should be made from the backend thread
 * unless an implementation documents a broader contract.
 *
 * @see Clip
 */
public interface Mixer extends AutoCloseable {
  /**
   * Returns the distance law used by clips that enable rolloff.
   *
   * @return current shared distance law
   */
  default RolloffMode getRolloffMode() {
    return RolloffMode.DEFAULT;
  }

  /**
   * Replaces the distance law used by all rolloff-enabled clips.
   *
   * @param mode shared distance law
   */
  default void setRolloffMode(RolloffMode mode) {
  }

  /**
   * Computes the current attenuation for a world-space source.
   *
   * <p>The default mixer has no listener implementation and therefore leaves
   * the source audible. Positional backends override this method.
   *
   * @param x source X coordinate
   * @param y source Y coordinate
   * @param z source Z coordinate
   * @return gain in the inclusive range {@code [0, 1]}
   */
  default float getRolloffGain(float x, float y, float z) {
    return 1F;
  }

  /**
   * Returns whether positional clips currently use stereo spatialization.
   *
   * <p>The setting applies to future voices and, when supported by the backend,
   * already-playing positional voices. Local clips such as music are unaffected.
   *
   * @return whether spatial panning is enabled
   */
  default boolean isSpatialAudioEnabled() {
    return true;
  }

  /**
   * Enables or disables stereo spatialization for positional clips.
   * The listener position and orientation are not changed.
   *
   * @param enabled whether positional clips should be panned by the backend
   */
  default void setSpatialAudioEnabled(boolean enabled) {
  }

  /**
   * Returns the gain applied to a spatial sample before the backend pans it.
   *
   * <p>A centred mono sample is sent to both output channels by conventional
   * stereo mixers. The equal-power compensation keeps its perceived level
   * stable. The result may change while a clip is playing when spatialization
   * is toggled. Backends with a different panning law may override this method.
   *
   * @param format source sample format
   * @return spatial pre-gain multiplier
   */
  default float getSpatialGain(AudioFormat format) {
    return isSpatialAudioEnabled() && format.channels() == 1 ? 0.70710677F : 1F;
  }

  /**
   * Sets the listener position in backend world coordinates.
   *
   * @param x listener position on the X axis
   * @param y listener position on the Y axis
   * @param z listener position on the Z axis
   */
  default void setListenerPosition(float x, float y, float z) {
  }

  /**
   * Sets the listener orientation as a unit quaternion.
   *
   * @param orientation listener orientation
   */
  default void setListenerOrientation(Quaternion orientation) {
  }

  /**
   * Sets a planar listener direction without flipping the Y axis.
   *
   * <p>The default adapts the 2D direction to the quaternion listener API;
   * backends only need to implement {@link #setListenerOrientation(Quaternion)}.
   *
   * @param x listener direction on the X axis
   * @param y listener direction on the Y axis
   */
  default void setListener2D(float x, float y) {
    setListener2D(x, y, false);
  }

  /**
   * Sets a planar listener direction, optionally flipping its Y component.
   *
   * @param x listener direction on the X axis
   * @param y listener direction on the Y axis
   * @param yFlip whether to invert the supplied Y component
   */
  default void setListener2D(float x, float y, boolean yFlip) {
    float orientedY = yFlip ? -y : y;
    setListenerOrientation(Quaternion.createFromEuler(0F, 0F, (float) Math.atan2(orientedY, x)));
  }

  /**
   * Replaces one mixer-owned environmental effect send.
   *
   * <p>The backend may ignore this when environmental effects are unsupported
   * or when the requested send is unavailable. Mixer-owned sends are applied
   * before effects explicitly attached to an individual clip.
   *
   * @param slot zero-based mixer effect slot
   * @param effect effect to apply, normally {@link SfxEffect#NONE} to clear
   */
  default void setGlobalEffect(int slot, SfxEffect effect) {
  }

  /**
   * Processes pending mixer events.
   *
   * <p>Must be called regularly to update playback state and trigger
   * loop callbacks.
   */
  void pollEvents();

  /**
   * Creates a new, unopened {@link Clip} backed by this mixer.
   *
   * @return a new clip in unopened state
   */
  Clip getClip();

  /**
   * Uploads reusable PCM samples once. The caller owns the returned buffer.
   *
   * @param format sample layout
   * @param data   complete PCM frames; captured before this method returns
   * @return shared storage belonging to this mixer
   */
  AudioBuffer createBuffer(AudioFormat format, byte[] data);

  /**
   * Creates a new, unopened streaming clip backed by this mixer.
   *
   * @return a new streaming clip in unopened state
   */
  StreamingClip getStreamingClip();

  /**
   * Enqueues a block of work to run on the audio thread.
   *
   * @param work the commands to execute
   */
  void submit(Runnable work);

  @Override
  void close();
}
