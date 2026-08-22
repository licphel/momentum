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
import net.fmhi.network.Connection;
import net.fmhi.network.NetSharedConstants;
import net.fmhi.network.Session;

import java.util.UUID;

/**
 * Handshake packet sent by the server immediately after the TCP channel becomes active, assigning the connection's
 * shared identifier.
 *
 * <p>The UUID carried here is the single authority for the connection: both endpoints adopt it as their session id
 * once the handshake completes. The client answers with a {@link SessionAckPacket} echoing the same UUID.
 *
 * <p>This packet is registered by {@link PacketRegistry} at startup and must never be sent by application code.
 *
 * @see Connection
 * @see SessionAckPacket
 */
public final class SessionOpenPacket extends Packet {
  private UUID connId = NetSharedConstants.UNASSIGNED_ID;

  /** Creates an empty packet for decode; fields are populated by {@link #read(CursorBuffer)}. */
  public SessionOpenPacket() {
  }

  /**
   * Creates an open packet carrying the server-assigned connection identifier.
   *
   * @param connId the connection identifier assigned by the server
   */
  public SessionOpenPacket(UUID connId) {
    this.connId = connId;
  }

  /**
   * Returns the connection identifier assigned by the server.
   *
   * @return the connection identifier
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
    // Handled internally by Connection's handshake logic before reaching the
    // inbound queue; application code never sees this packet.
  }
}
