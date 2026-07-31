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

import io.netty.util.AttributeKey;

import java.util.UUID;

/**
 * Shared constants for the network module.
 *
 * <p>All values are advisory defaults and may be overridden through configuration on
 * {@link Connection} or {@link ServerConnection}.
 */
public final class NetConfig {
  /** Default listening port for servers and default connect port for clients. */
  public static final int DEFAULT_PORT = 25565;
  /** Maximum wire-frame size in bytes (8 MiB). Frames exceeding this limit are rejected. */
  public static final int FRAME_MAX_SIZE = 8 * 1024 * 1024;
  /**
   * Compression threshold in bytes. When a packet body reaches this size, the frame is compressed before transmission.
   * Set to a negative value to disable per-packet compression.
   */
  public static final int COMPRESSION_THRESHOLD = 256;
  /** Interval in milliseconds between automatic heartbeat transmissions. */
  public static final long HEARTBEAT_INTERVAL_MS = 5000;
  /** Socket read timeout in seconds applied to the underlying transport. */
  public static final int READ_TIMEOUT_SECONDS = 30;
  /**
   * Maximum permitted session inactivity in milliseconds. Sessions that have not sent or received data within this
   * window are forcibly disconnected.
   */
  public static final long SESSION_TIMEOUT_MS = 30_000;
  /**
   * Channel attribute key for the connecting client's identity. The server stores each client's identity under this key
   * after the initial handshake.
   */
  public static final AttributeKey<UUID> CLIENT_ID_KEY = AttributeKey.valueOf("fmhi.clientId");

  private NetConfig() {
  }
}
