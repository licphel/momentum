package io.viki.momentum.sfx;

/**
 * Immutable PCM storage uploaded once to its owning mixer and shared by clips.
 *
 * <p>The creator owns this resource. Clips borrow it; closing a clip never closes
 * a shared buffer. Native deletion may be deferred until borrowers release it.
 * Mixer shutdown also releases outstanding buffers. Backend commands are serialized
 * by the mixer; creation and closure may be requested from any thread.
 */
public interface AudioBuffer extends AutoCloseable {
  /**
   * Returns the immutable PCM layout.
   *
   * @return sample format
   */
  AudioFormat format();

  /**
   * Returns the immutable sample duration, independent of pitch or repetition.
   *
   * @return duration in seconds, including zero for an empty buffer
   */
  float getDuration();

  /** Releases the owner's interest in these samples; repeated closure is harmless. */
  @Override
  void close();
}
