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

package net.fmhi.network.packet;

import net.fmhi.codec.streaming.CursorBuffer;
import net.fmhi.network.NetSharedConstants;
import net.fmhi.network.ServerConnection;
import net.fmhi.network.Session;

import java.util.UUID;

/**
 * Handshake packet sent by the client in response to {@link SessionOpenPacket}, echoing the server-assigned connection
 * identifier.
 *
 * <p>The connection is only considered established on the server once this packet arrives and the echoed UUID matches.
 * The echo makes agreement explicit: both endpoints have provably adopted the same identifier before either fires
 * {@code onConnected}.
 *
 * <p>This packet is registered by {@link PacketRegistry} at startup and must never be sent by application code.
 *
 * @see ServerConnection
 * @see SessionOpenPacket
 */
public final class SessionAckPacket extends Packet {
  private UUID connId = NetSharedConstants.UNASSIGNED_ID;

  /** Creates an empty packet for decode; fields are populated by {@link #read(CursorBuffer)}. */
  public SessionAckPacket() {
  }

  /**
   * Creates an ack packet echoing the connection identifier received from the server.
   *
   * @param connId the connection identifier to echo back
   */
  public SessionAckPacket(UUID connId) {
    this.connId = connId;
  }

  /**
   * Returns the echoed connection identifier.
   *
   * @return the connection identifier received from the server
   */
  public UUID connId() {
    return connId;
  }

  @Override
  public void read(CursorBuffer buf) {
    connId = buf.readUUID();
  }

  @Override
  public void write(CursorBuffer buf) {
    buf.writeUUID(connId);
  }

  @Override
  public void handle(Session session) {
    // Handled internally by ServerConnection's handshake logic before reaching
    // the inbound queue; application code never sees this packet.
  }
}
