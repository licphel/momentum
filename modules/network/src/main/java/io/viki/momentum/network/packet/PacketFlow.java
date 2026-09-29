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

package io.viki.momentum.network.packet;

/** Declares which remote endpoint is permitted to send a packet type. */
public enum PacketFlow {
  /** Packets that may be sent from the server to the client. */
  CLIENTBOUND,
  /** Packets that may be sent from the client to the server. */
  SERVERBOUND,
  /** Packets that may be sent in either direction. */
  BIDIRECTIONAL,
  /** Packets reserved for internal connection-management traffic. */
  INTERNAL;

  /**
   * Reports whether this flow permits delivery to the client.
   *
   * @return {@code true} if the packet may be sent to the client
   */
  public boolean acceptedByClient() {
    return this == CLIENTBOUND || this == BIDIRECTIONAL;
  }

  /**
   * Reports whether this flow permits delivery to the server.
   *
   * @return {@code true} if the packet may be sent to the server
   */
  public boolean acceptedByServer() {
    return this == SERVERBOUND || this == BIDIRECTIONAL;
  }
}
