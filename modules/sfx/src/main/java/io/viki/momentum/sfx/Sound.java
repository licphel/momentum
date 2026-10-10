package io.viki.momentum.sfx;

import io.viki.momentum.util.FloatSupplier;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Shared samples and independently sampled pitch/gain multipliers for each play request.
 *
 * <p>This immutable descriptor does not own or close its buffer. It is safe to share
 * across threads while its owning mixer remains alive. Equal range endpoints disable
 * randomization. Playback volume controls multiply the sampled gain.
 *
 * @param buffer reusable uploaded samples
 * @param pitch  playback-speed range, relative to the clip's configured pitch
 * @param volume gain range, relative to the clip's configured volume
 */
public record Sound(AudioBuffer buffer, Range pitch, Range volume) {
  /**
   * Creates a sound without pitch or volume variation.
   *
   * @param buffer reusable samples, owned by the caller
   */
  public Sound(AudioBuffer buffer) {
    this(buffer, Range.UNITY, Range.UNITY);
  }

  /**
   * Creates and starts one clip with independently sampled playback parameters.
   *
   * <p>The caller owns the returned clip and may transfer it to a {@link ClipManager}.
   * Closing it does not close this sound's buffer. A saturated voice pool skips
   * playback and reports completion through {@link Clip#shouldClose()}.
   *
   * @param mixer mixer owning this sound's buffer
   * @return caller-owned playback clip
   */
  public Clip play(Mixer mixer) {
    return play(mixer, () -> 1F);
  }

  /**
   * Starts one clip, multiplying its sampled gain by a dynamic volume control.
   *
   * @param mixer      mixer owning this sound's buffer
   * @param baseVolume thread-safe dynamic volume source
   * @return caller-owned playback clip
   */
  public Clip play(Mixer mixer, FloatSupplier baseVolume) {
    Clip clip = mixer.getClip();
    try {
      float gain = volume.sample();
      clip.open(buffer);
      clip.setPitch(FloatSupplier.constant(pitch.sample()));
      clip.setVolume(() -> gain * baseVolume.getAsFloat());
      clip.play();
      return clip;
    } catch (RuntimeException exception) {
      clip.close();
      throw exception;
    }
  }

  /**
   * Starts one clip at a world-space position for spatial playback.
   * Mono samples receive the mixer's equal-power spatial pre-gain; the value
   * remains dynamic when the mixer spatialization switch is toggled. The clip
   * also enables the mixer's shared distance rolloff law.
   *
   * @param mixer      mixer owning this sound's buffer
   * @param baseVolume thread-safe dynamic volume source
   * @param x          source position on the X axis
   * @param y          source position on the Y axis
   * @param z          source position on the Z axis
   * @return caller-owned playback clip
   */
  public Clip play(Mixer mixer, FloatSupplier baseVolume, FloatSupplier x, FloatSupplier y, FloatSupplier z) {
    Clip clip = mixer.getClip();
    try {
      float gain = volume.sample();
      clip.open(buffer);
      clip.setPitch(FloatSupplier.constant(pitch.sample()));
      clip.setRolloffEnabled(true);
      clip.setVolume(() -> gain * baseVolume.getAsFloat());
      clip.setSpatialPosition(x, y, z);
      clip.play();
      return clip;
    } catch (RuntimeException exception) {
      clip.close();
      throw exception;
    }
  }

  /**
   * Uniform multiplier range. Instances are immutable and thread-safe.
   *
   * @param min inclusive minimum multiplier
   * @param max upper multiplier; equal endpoints produce a constant
   */
  public record Range(float min, float max) {
    /** No variation or scaling. */
    public static final Range UNITY = new Range(1, 1);

    /**
     * Validates the multiplier interval.
     *
     * @param min inclusive minimum multiplier
     * @param max upper multiplier; equal endpoints produce a constant
     * @throws IllegalArgumentException if the minimum is negative or exceeds the maximum
     */
    public Range {
      if (min < 0 || max < min) {
        throw new IllegalArgumentException("Invalid sound multiplier range: " + min + ".." + max);
      }
    }

    /**
     * Samples using the current thread's random generator.
     *
     * @return sampled multiplier
     */
    public float sample() {
      return sample(ThreadLocalRandom.current());
    }

    /**
     * Samples using the supplied random generator.
     *
     * @param random caller-owned random source
     * @return sampled multiplier
     */
    public float sample(RandomGenerator random) {
      return min == max ? min : min + random.nextFloat() * (max - min);
    }
  }
}
