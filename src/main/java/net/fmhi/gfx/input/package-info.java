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

/**
 * Platform-agnostic input event system.
 *
 * <p>This package provides types for representing user input independent of
 * any windowing backend, plus pollable per-frame input state. Input events extend {@link net.fmhi.event.Event} and are
 * dispatched through the core {@link net.fmhi.event.EventBus}.
 *
 * <h3>Key types</h3>
 * <ul>
 *   <li>{@link net.fmhi.gfx.input.event} — immutable input event records
 *       (key, mouse, scroll, resize, etc.)</li>
 *   <li>{@link net.fmhi.gfx.input.Snapshot} — per-frame pollable input
 *       state for game-fmt queries</li>
 *   <li>{@link net.fmhi.gfx.input.KeyCode} — platform-agnostic key codes
 *       based on USB HID Usage Tables</li>
 *   <li>{@link net.fmhi.gfx.input.KeyAction} — press/release/repeat enum</li>
 *   <li>{@link net.fmhi.gfx.input.MouseButton} — platform-agnostic mouse buttons</li>
 * </ul>
 */
package net.fmhi.gfx.input;
