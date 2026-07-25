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
 * A listener for events of a specific type.
 *
 * <p>This is a functional interface whose functional method is
 * {@link #handle(EventContext, Event)}. The first parameter provides the mutable dispatch context; the second carries
 * the immutable event data.
 *
 * @param <T> the event type this listener handles
 * @see Event
 * @see EventContext
 * @see EventBus
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
  /**
   * Handles the given event.
   *
   * @param ctx   the dispatch context, providing access to cancel and result state
   * @param event the event data
   */
  void handle(EventContext<T> ctx, T event);
}
