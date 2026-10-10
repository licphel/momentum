package io.viki.momentum.sfx.openal;

import io.viki.momentum.sfx.AudioBuffer;
import io.viki.momentum.sfx.AudioException;
import io.viki.momentum.sfx.AudioFormat;

import java.nio.ByteBuffer;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Mixer-owned native samples. Borrow counts and native operations are audio-thread
 * confined; owner closure is synchronized and may be requested from any thread.
 */
final class OpenALAudioBuffer implements AudioBuffer {
  final OpenALMixer mixer;
  private final AudioFormat format;
  int id;
  private volatile boolean closed;
  private int borrowers;

  OpenALAudioBuffer(OpenALMixer mixer, AudioFormat format, byte[] data) {
    if (data.length % format.frameSize() != 0) {
      throw new IllegalArgumentException("Audio buffer requires complete PCM frames: " + data.length);
    }
    int nativeFormat = OpenALUtils.convertFormat(format);
    this.mixer = mixer;
    this.format = format;
    byte[] samples = data.clone();
    mixer.submit(() -> {
      id = alGenBuffers();
      if (id == 0) {
        throw new AudioException("Failed to generate OpenAL audio buffer");
      }
      ByteBuffer staging = memAlloc(samples.length);
      try {
        staging.put(samples).flip();
        alBufferData(id, nativeFormat, staging, format.sampleRate());
      } finally {
        memFree(staging);
      }
      mixer.buffers.add(this);
    });
  }

  @Override
  public AudioFormat format() {
    return format;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    mixer.submit(() -> {
      if (borrowers == 0) {
        delete();
      }
    });
  }

  synchronized void borrow() {
    if (closed) {
      throw new IllegalStateException("Audio buffer is closed");
    }
    mixer.submit(() -> borrowers++);
  }

  void release() {
    borrowers--;
    if (closed && borrowers == 0) {
      delete();
    }
  }

  void delete() {
    if (id == 0) {
      return;
    }
    alDeleteBuffers(id);
    id = 0;
    closed = true;
    mixer.buffers.remove(this);
  }
}
