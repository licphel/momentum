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

package net.fmhi.util.logging;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * One formatted, immutable log record handed to every {@link Output}.
 *
 * <p>Formatting to a final line happens once inside the logger (not per output), so all outputs of a record see the
 * exact same text and the cost is paid once.
 *
 * @param level   severity of the record
 * @param time    capture time; the year field participates in formatting only
 * @param caller  caller description, e.g. {@code MyGame.main(MyGame.java:12)}
 * @param message the message text; never {@code null}
 * @param error   optional throwable associated with the record, or {@code null}
 * @param line    the pre-formatted {@code [LEVEL/TIME] CALLER: message} line, newline-terminated
 */
public record LogRecord(Level level, LocalDateTime time, String caller, String message,
                        @Nullable Throwable error, String line) {
}
