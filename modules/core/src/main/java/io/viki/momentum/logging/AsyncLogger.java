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

package io.viki.momentum.logging;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Logger} that formats on the calling thread but performs output I/O on a dedicated flusher thread.
 *
 * <p>{@code log()} captures the record (level, time, caller, formatted line — all immutable) and enqueues it; the
 * flusher thread drains the queue into the outputs. Caller detection and formatting stay on the calling thread
 * because they depend on the caller's stack; only the I/O moves.
 *
 * <p>Crash safety: {@link #close()} drains remaining records and joins the flusher. A {@link Runtime#addShutdownHook
 * shutdown hook} also drains the queue on JVM exit so a {@code fatal} right before death is not lost — a daemon
 * flusher thread would never get to write it otherwise.
 *
 * <p>This class is thread-safe: any number of threads may log concurrently.
 */
public final class AsyncLogger extends AbstractLogger {
  /** Queue capacity before producers block; large enough that games never hit it. */
  static final int QUEUE_CAPACITY = 8192;
  /** Sentinel enqueued by {@link #flush()}; never handed to outputs. */
  private static final LogRecord BARRIER = new LogRecord(Level.DEBUG, LocalDateTime.of(1970, 1, 1, 0, 0, 0),
      "flush-barrier", "", null, "");
  private final BlockingQueue<LogRecord> queue;
  private final Thread flusher;
  private volatile boolean closed;

  AsyncLogger(String name) {
    super(name);
    this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    this.flusher = new Thread(this::run, "momentum-logger-" + name());
    this.flusher.setDaemon(true);
    this.flusher.start();
    Runtime.getRuntime().addShutdownHook(new Thread(this::close, "momentum-logger-" + name() + "-shutdown"));
  }

  @Override
  protected void dispatch(LogRecord record) {
    if (closed) {
      return;
    }
    try {
      queue.put(record);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // Fall back to direct write rather than dropping the record.
      writeToOutputs(record);
    }
  }

  @Override
  public void flush() {
    // Drain at least everything queued before this call. The flusher flushes
    // outputs after an empty poll, so enqueuing a barrier record and waiting
    // for it to be written gives us the ordering guarantee we need.
    try {
      queue.put(BARRIER);
      while (true) {
        LogRecord head = queue.peek();
        if (head == BARRIER || head == null) {
          return;
        }
        TimeUnit.MILLISECONDS.sleep(1);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    flusher.interrupt();
    try {
      flusher.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    drainRemaining();
    closeOutputs();
  }

  private void run() {
    while (true) {
      try {
        LogRecord record = queue.poll(200, TimeUnit.MILLISECONDS);
        if (record == null) {
          continue;
        }
        if (record == BARRIER) {
          // Flush semantics: outputs flushed, barrier dropped.
          flushOutputs();
          continue;
        }
        writeToOutputs(record);
      } catch (InterruptedException e) {
        // closed or closing: fall through to drain whatever is left.
        break;
      }
    }
    drainRemaining();
  }

  private void drainRemaining() {
    LogRecord record;
    while ((record = queue.poll()) != null) {
      if (record != BARRIER) {
        writeToOutputs(record);
      }
    }
    flushOutputs();
  }
}
