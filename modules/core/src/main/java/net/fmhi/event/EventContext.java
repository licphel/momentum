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

package net.fmhi.event;

/**
 * Mutable context carried alongside an {@link Event} during dispatch.
 *
 * <p>While events are immutable data carriers, the context holds transient dispatch state:
 * whether the event has been {@link #isCanceled() canceled}, the {@link #result() semantic result}, and the current
 * {@link #phase() phase}.
 *
 * @param <T> the event type
 * @see Event
 * @see EventListener
 * @see EventBus
 */
public final class EventContext<T extends Event> {
  private final T event;
  private final Phase phase;
  private boolean canceled;
  private Result result;

  EventContext(T event, Phase phase) {
    this.event = event;
    this.phase = phase;
    this.canceled = false;
    this.result = Result.DEFAULT;
  }

  /**
   * Returns the event being dispatched.
   *
   * @return the event
   */
  public T event() {
    return event;
  }

  /**
   * Returns the dispatch phase in which this event was posted.
   *
   * @return the phase; {@link Phase#NONE} if the event is phase-agnostic
   */
  public Phase phase() {
    return phase;
  }

  /**
   * Returns whether the event has been canceled by a handler.
   *
   * @return {@code true} if canceled
   */
  public boolean isCanceled() {
    return canceled;
  }

  /**
   * Sets or clears the canceled flag on this event.
   *
   * <p>Canceling an event does not stop dispatch — all remaining handlers still receive the
   * event. Handlers that wish to respect cancellation should check {@link #isCanceled()} and return early.
   *
   * @param canceled {@code true} to mark the event as canceled, {@code false} to clear cancellation
   */
  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }

  /**
   * Returns the semantic result of this event.
   *
   * <p>The default value is {@link Result#DEFAULT}, indicating no opinion.
   *
   * @return the current result
   */
  public Result result() {
    return result;
  }

  /**
   * Sets the semantic result of this event.
   *
   * <p>Handlers should only upgrade the result — for example, from {@link Result#DEFAULT}
   * to {@link Result#ALLOW} or {@link Result#DENY} — so that the most restrictive handler prevails.
   *
   * @param result the result to set
   */
  public void setResult(Result result) {
    this.result = result;
  }
}
