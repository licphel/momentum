package io.viki.momentum.sfx;

import io.viki.momentum.util.FloatSupplier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Owns caller-prepared music clips with resumable interruptions and optional fades.
 *
 * <p>Callers create, open, and configure clips, including categories and repetition settings.
 * This manager preserves those settings and progress. Insertions retain interrupted clips;
 * replacement releases them. Owned clips must not be controlled externally or registered
 * with {@link ClipManager}.
 *
 * <p>Instances are confined to the application's audio-control thread. Call {@link #update(float)}
 * regularly after mixer polling. Fades adjust local gain, never volume categories. A new
 * transition request completes the previous requested transition before starting its own.
 * Each track's base gain is supplied dynamically, independently of its fade envelope.
 * Clip transfers take O(retained tracks) time to prevent duplicate ownership.
 */
public final class MusicManager implements AutoCloseable {
  private final Deque<Track> tracks = new ArrayDeque<>();
  private Transition transition = Transition.NONE;
  private @Nullable Track incoming;
  private float transitionElapsed;
  private float outgoingEnvelope;
  private boolean closed;

  /**
   * Creates an empty owner without allocating clips or an output device.
   */
  public MusicManager() {
  }

  private static void activate(Track track) {
    track.fadeElapsed = 0;
    track.envelope = track.fade.inSeconds() > 0 ? 0 : 1;
    track.applyVolume();
    track.clip.resume();
  }

  /**
   * Replaces retained music with a clip that starts without a fade-in.
   *
   * <p>The previous clip's configured fade-out still applies.
   *
   * @param clip an open, unfinished clip whose ownership is transferred
   * @param baseVol supplies the current nonnegative local gain on this control thread
   * @throws IllegalStateException    if this manager or the clip is unavailable
   * @throws IllegalArgumentException if the clip is already retained
   */
  public void switchTo(Clip clip, FloatSupplier baseVol) {
    switchTo(clip, baseVol, Fade.NONE);
  }

  /**
   * Replaces retained music using the outgoing fade-out and the incoming fade-in.
   *
   * <p>Ownership transfers immediately. The incoming clip is paused until the outgoing fade
   * completes. The supplied gain is read at every update, including during either fade.
   *
   * @param clip an open, unfinished clip to own
   * @param baseVol supplies the current nonnegative local gain on this control thread
   * @param fade independent fade settings retained with the clip
   * @throws IllegalStateException    if this manager or the clip is unavailable
   * @throws IllegalArgumentException if the clip is already retained
   */
  public void switchTo(Clip clip, FloatSupplier baseVol, Fade fade) {
    checkTransfer(clip);
    begin(Transition.REPLACE, new Track(clip, baseVol, fade));
  }

  /**
   * Inserts music without a fade-in while retaining the interrupted track's progress.
   *
   * <p>The interrupted clip's configured fade-out still applies.
   *
   * @param clip an open, unfinished clip whose ownership is transferred
   * @param baseVol supplies the current nonnegative local gain on this control thread
   * @throws IllegalStateException    if this manager or the clip is unavailable
   * @throws IllegalArgumentException if the clip is already retained
   */
  public void insert(Clip clip, FloatSupplier baseVol) {
    insert(clip, baseVol, Fade.NONE);
  }

  /**
   * Inserts music with independent fades and preserves interrupted playback progress.
   *
   * <p>The previous clip fades out before being paused. Its fade-in applies again on resumption.
   * Insertions may be nested; repetition counts and categories remain unchanged.
   *
   * @param clip an open, unfinished clip to own
   * @param baseVol supplies the current nonnegative local gain on this control thread
   * @param fade independent fade settings retained with the clip
   * @throws IllegalStateException    if this manager or the clip is unavailable
   * @throws IllegalArgumentException if the clip is already retained
   */
  public void insert(Clip clip, FloatSupplier baseVol, Fade fade) {
    checkTransfer(clip);
    begin(Transition.INSERT, new Track(clip, baseVol, fade));
  }

  /**
   * Processes completion without advancing gain envelopes.
   *
   * <p>Dynamic base gains still refresh. Use {@link #update(float)} to advance enabled fades.
   * Calling this after closure has no effect.
   */
  public void update() {
    update(0);
  }

  /**
   * Advances fades, releases completed clips, and resumes interrupted music.
   *
   * <p>Time remaining after fade-out advances the next fade-in. Pending and paused playback
   * are not completion. This method does not poll the device; calling it after closure is harmless.
   * Base-gain suppliers are also read during steady playback, so gain changes need no new transfer.
   *
   * @param elapsedSeconds nonnegative elapsed playback-control time, in seconds
   * @throws IllegalArgumentException if elapsed time is negative
   */
  public void update(float elapsedSeconds) {
    if (elapsedSeconds < 0) {
      throw new IllegalArgumentException("Music update elapsed time must be nonnegative");
    }
    if (tracks.isEmpty()) {
      return;
    }
    Track current = tracks.peek();
    if (transition != Transition.NONE && current.clip.shouldClose()) {
      finishTransition();
      if (tracks.isEmpty()) {
        return;
      }
      current = tracks.peek();
    }
    if (transition != Transition.NONE) {
      float duration = current.fade.outSeconds();
      float consumed = Math.min(elapsedSeconds, duration - transitionElapsed);
      transitionElapsed += consumed;
      current.envelope = outgoingEnvelope * (1 - transitionElapsed / duration);
      current.applyVolume();
      elapsedSeconds -= consumed;
      if (current.clip.shouldClose() || transitionElapsed >= duration) {
        finishTransition();
      } else {
        return;
      }
    }
    if (!tracks.isEmpty() && tracks.peek().clip.shouldClose()) {
      tracks.pop().clip.close();
      resumePrevious();
    }
    if (tracks.isEmpty()) {
      return;
    }
    current = tracks.peek();
    if (current.fade.inSeconds() > 0 && current.fadeElapsed < current.fade.inSeconds()) {
      current.fadeElapsed = Math.min(current.fade.inSeconds(), current.fadeElapsed + elapsedSeconds);
      current.envelope = current.fadeElapsed / current.fade.inSeconds();
    }
    current.applyVolume();
  }

  /**
   * Returns the current track, including one fading out.
   *
   * @return the current clip, or {@code null} when no music is retained
   */
  public @Nullable Clip current() {
    return tracks.isEmpty() ? null : tracks.peek().clip;
  }

  /**
   * Returns the retained clip count including any pending incoming track.
   *
   * @return the owned clip count
   */
  public int depth() {
    return tracks.size() + (incoming == null ? 0 : 1);
  }

  /**
   * Removes the active track using its fade-out, then resumes its predecessor.
   *
   * <p>Empty playback is unchanged. Nonzero fades require subsequent updates.
   */
  public void pop() {
    begin(Transition.POP, null);
  }

  /**
   * Releases all retained music after the active clip's fade-out, without resuming predecessors.
   *
   * <p>Nonzero fades require subsequent updates. The manager may accept later transfers.
   */
  public void clear() {
    begin(Transition.CLEAR, null);
  }

  /**
   * Immediately releases owned clips and permanently rejects new transfers.
   *
   * <p>Closure bypasses fades so cleanup needs no future updates. Repeated calls have no effect;
   * output devices and categories remain untouched.
   */
  @Override
  public void close() {
    closed = true;
    if (incoming != null) {
      incoming.clip.close();
    }
    incoming = null;
    transition = Transition.NONE;
    releaseAll();
  }

  private void checkTransfer(Clip clip) {
    if (closed || !clip.isOpen() || clip.shouldClose()) {
      throw new IllegalStateException("Music manager or incoming clip is unavailable");
    }
    if (incoming != null && incoming.clip == clip) {
      throw new IllegalArgumentException("Music clip is already pending");
    }
    for (Track track : tracks) {
      if (track.clip == clip) {
        throw new IllegalArgumentException("Music clip is already managed");
      }
    }
  }

  @SuppressWarnings("all")
  private void begin(Transition requested, @Nullable Track next) {
    if (transition != Transition.NONE) {
      finishTransition();
    }
    if (next != null) {
      next.clip.pause();
    }
    incoming = next;
    transition = requested;
    transitionElapsed = 0;
    if (!tracks.isEmpty() && !tracks.peek().clip.shouldClose() && tracks.peek().fade.outSeconds() > 0) {
      outgoingEnvelope = tracks.peek().envelope;
    } else {
      finishTransition();
    }
  }

  private void finishTransition() {
    switch (transition) {
      case REPLACE, CLEAR -> releaseAll();
      case POP -> {
        if (!tracks.isEmpty()) {
          tracks.pop().clip.close();
        }
      }
      case INSERT -> {
        while (!tracks.isEmpty() && tracks.peek().clip.shouldClose()) {
          tracks.pop().clip.close();
        }
        if (!tracks.isEmpty()) {
          Track previous = tracks.peek();
          previous.clip.pause();
          previous.envelope = 1;
          previous.applyVolume();
        }
      }
      case NONE -> {
        return;
      }
    }
    Track next = incoming;
    boolean resumePrevious = transition == Transition.POP;
    incoming = null;
    transition = Transition.NONE;
    if (next != null) {
      tracks.push(next);
      activate(next);
    } else if (resumePrevious) {
      resumePrevious();
    }
  }

  private void releaseAll() {
    while (!tracks.isEmpty()) {
      tracks.pop().clip.close();
    }
  }

  private void resumePrevious() {
    while (!tracks.isEmpty() && tracks.peek().clip.shouldClose()) {
      tracks.pop().clip.close();
    }
    if (!tracks.isEmpty()) {
      activate(tracks.peek());
    }
  }

  private enum Transition {
    NONE,
    INSERT,
    REPLACE,
    POP,
    CLEAR
  }

  /**
   * Independent gain transition durations for a retained clip.
   *
   * <p>Zero disables a fade. Fade-out applies to interruption or explicit removal; natural
   * completion cannot fade samples that have already ended. Instances are thread-safe.
   *
   * @param inSeconds  fade-in duration when starting or resuming, in seconds
   * @param outSeconds fade-out duration before pausing or releasing, in seconds
   */
  public record Fade(float inSeconds, float outSeconds) {
    /** Immediate transitions without gain envelopes. */
    public static final Fade NONE = new Fade(0, 0);

    /**
     * Creates independent fade settings.
     *
     * @param inSeconds  nonnegative fade-in duration in seconds
     * @param outSeconds nonnegative fade-out duration in seconds
     * @throws IllegalArgumentException if a duration is negative
     */
    public Fade {
      if (inSeconds < 0 || outSeconds < 0) {
        throw new IllegalArgumentException("Music fade durations must be nonnegative");
      }
    }
  }

  private static final class Track {
    final Clip clip;
    final Fade fade;
    final FloatSupplier baseVol;
    float envelope = 1;
    float fadeElapsed;

    Track(Clip clip, FloatSupplier baseVol, Fade fade) {
      this.clip = clip;
      this.fade = fade;
      this.baseVol = baseVol;
    }

    void applyVolume() {
      clip.setVolume(baseVol.getAsFloat() * envelope);
    }
  }
}
