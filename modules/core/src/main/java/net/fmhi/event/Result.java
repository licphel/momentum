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
 * Three-state result carried by an {@link EventContext}, capturing semantic intent beyond the binary
 * {@link EventContext#isCanceled() canceled} flag.
 *
 * <ul>
 *   <li>{@link #DENY} — explicitly disallow the action</li>
 *   <li>{@link #DEFAULT} — no opinion; let subsequent handlers or default logic decide</li>
 *   <li>{@link #ALLOW} — explicitly allow the action</li>
 * </ul>
 *
 * <p>Handlers should only upgrade the result — for example, from {@code DEFAULT} to
 * {@code DENY} — so that the most restrictive handler prevails.
 *
 * @see EventContext
 */
public enum Result {
  /** Disallow the action. */
  DENY,
  /** No opinion — use the default behavior. */
  DEFAULT,
  /** Explicitly allow the action. */
  ALLOW
}
