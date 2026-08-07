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

import java.lang.annotation.*;

/**
 * Marks a method as an event handler for automatic discovery by {@link EventBus}.
 *
 * <p>Annotated methods must accept an {@link EventContext} as the first parameter and
 * an {@link Event} subtype as the second. The event type is determined from the second parameter.
 *
 * @see Event
 * @see EventContext
 * @see Priority
 * @see EventBus
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
  /**
   * The priority at which this handler runs during dispatch.
   *
   * <p>Higher-priority handlers are invoked first.
   *
   * @return the handler priority; defaults to {@link Priority#NORMAL}
   */
  Priority priority() default Priority.NORMAL;

  /**
   * The phase during which this handler receives events.
   *
   * <p>When set to a value other than {@link Phase#NONE}, this handler is only called for
   * events posted with that specific phase.
   *
   * @return the phase filter; defaults to {@link Phase#NONE} (all phases)
   */
  Phase phase() default Phase.NONE;
}
