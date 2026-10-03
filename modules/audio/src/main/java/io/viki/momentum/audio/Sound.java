package io.viki.momentum.audio;

/**
 * Decoded PCM samples without any playback or resource ownership.
 *
 * <p>Sample storage is copied on construction and retrieval. Instances are immutable and
 * safe to share between threads; opening a sound never transfers a clip's ownership.
 *
 * @param format the PCM sample layout
 * @param data   complete PCM frames matching the format
 */
public record Sound(AudioFormat format, byte[] data) {
  /**
   * Captures a reusable snapshot of decoded samples.
   *
   * @param format the PCM sample layout
   * @param data   complete PCM frames; an empty array represents silence
   * @throws IllegalArgumentException if the data ends with an incomplete frame
   */
  public Sound {
    if (data.length % format.frameSize() != 0) {
      throw new IllegalArgumentException("Sound data must contain complete PCM frames: " + data.length);
    }
    data = data.clone();
  }
}
