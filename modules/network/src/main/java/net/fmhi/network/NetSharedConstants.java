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

package net.fmhi.network;

import java.util.UUID;

/**
 * Shared constants for the network module.
 *
 * <p>All values are advisory defaults and may be overridden through configuration on
 * {@link Connection} or {@link ServerConnection}.
 */
public final class NetSharedConstants {
  /** Maximum wire-frame size in bytes (8 MiB). Frames exceeding this limit are rejected. */
  public static final int FRAME_MAX_SIZE = 8 * 1024 * 1024;
  /** Interval in milliseconds between automatic heartbeat transmissions. */
  public static final long HEARTBEAT_INTERVAL_MS = 5000;
  /**
   * Maximum permitted session inactivity in milliseconds. Sessions that have not sent or received data within this
   * window are forcibly disconnected.
   */
  public static final long SESSION_TIMEOUT_MS = 30_000;
  /**
   * Handshake timeout in milliseconds. A connection that has not completed the session-open/session-ack exchange within
   * this window of the TCP channel becoming active is aborted and — because agreement was never reached — never
   * surfaces as a connected session.
   */
  public static final long HANDSHAKE_TIMEOUT_MS = 10_000;
  /**
   * Sentinel connection identifier carried by handshake packets before a real value is decoded. A UUID whose bits are
   * all zero cannot be assigned by the server, so it unambiguously marks "not yet negotiated".
   */
  public static final UUID UNASSIGNED_ID = new UUID(0, 0);

  private NetSharedConstants() {
  }
}
