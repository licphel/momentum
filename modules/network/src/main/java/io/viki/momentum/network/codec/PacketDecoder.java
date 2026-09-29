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

package io.viki.momentum.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.momentum.network.NetworkException;
import io.viki.momentum.network.packet.Packet;
import io.viki.momentum.network.packet.PacketRegistry;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A Netty inbound handler that decodes wire-format bytes into {@link Packet} instances.
 *
 * <p>Must be installed downstream of a length-field-based frame decoder that strips the frame-length prefix. The
 * decoder
 * reads the packet ID and payload, handling optional decompression automatically. The compression threshold must match
 * the value configured on the paired {@link PacketEncoder}.
 *
 * <p>Each instance is intended for one Netty channel pipeline and must not be used concurrently by multiple channels.
 *
 * @see PacketEncoder
 */
public final class PacketDecoder extends ByteToMessageDecoder {
  /** Maximum payload size accepted after decompression. */
  private static final int MAX_UNCOMPRESSED_SIZE = 2 * 1024 * 1024;
  private final int compressionThreshold;

  /**
   * Creates a decoder with compression disabled.
   */
  public PacketDecoder() {
    this(-1);
  }

  /**
   * Creates a decoder with the given compression threshold.
   *
   * @param compressionThreshold the threshold used by the paired encoder; a negative value disables decompression
   */
  public PacketDecoder(int compressionThreshold) {
    this.compressionThreshold = compressionThreshold;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (compressionThreshold >= 0) {
      decodeCompressed(ctx, in, out);
    } else {
      decodeRaw(in, out);
    }
  }

  /**
   * Decodes one uncompressed packet from the current frame.
   *
   * @param in the buffer containing the frame payload
   * @param out the collection receiving a decoded packet
   * @throws NetworkException if the packet ID is unknown or the payload is not consumed completely
   */
  private void decodeRaw(ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 4) {
      return;
    }
    BinaryBuffer buf = new NettyBinaryBuffer(in);
    int packetId = buf.readInt();
    Packet packet = PacketRegistry.create(packetId);
    if (packet == null) {
      throw new NetworkException("Unknown packet ID: " + packetId);
    }
    packet.read(buf);
    requireFullyRead(buf, packetId);
    // sync Netty cursors
    in.readerIndex(buf.readerIndex());
    out.add(packet);
  }

  /**
   * Decodes one packet from a frame that may contain compressed data.
   *
   * @param ctx the channel context associated with the frame
   * @param in the buffer containing the frame payload
   * @param out the collection receiving a decoded packet
   * @throws NetworkException if the compressed data or decoded packet is invalid
   */
  private void decodeCompressed(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 4) {
      return;
    }
    in.markReaderIndex();
    int uncompressedLen = in.readInt();

    if (uncompressedLen == 0) {
      if (in.readableBytes() < 4) {
        in.resetReaderIndex();
        return;
      }
      BinaryBuffer buf = new NettyBinaryBuffer(in);
      int packetId = buf.readInt();
      Packet packet = PacketRegistry.create(packetId);
      if (packet == null) {
        throw new NetworkException("Unknown packet ID: " + packetId);
      }
      packet.read(buf);
      requireFullyRead(buf, packetId);
      in.readerIndex(buf.readerIndex());
      out.add(packet);
    } else {
      if (uncompressedLen < 4 || uncompressedLen > MAX_UNCOMPRESSED_SIZE) {
        throw new NetworkException("Invalid uncompressed packet size: " + uncompressedLen);
      }
      if (in.readableBytes() < 1) {
        in.resetReaderIndex();
        return;
      }
      byte[] compressed = new byte[in.readableBytes()];
      in.readBytes(compressed);

      Inflater inflater = new Inflater();
      inflater.setInput(compressed);
      byte[] decompressed = new byte[uncompressedLen];
      try {
        int resultLen = inflater.inflate(decompressed);
        if (resultLen != uncompressedLen || !inflater.finished() || inflater.getRemaining() != 0) {
          throw new NetworkException("Decompressed size mismatch: expected " + uncompressedLen + ", got " + resultLen);
        }
      } catch (DataFormatException e) {
        throw new NetworkException("Failed to decompress packet", e);
      } finally {
        inflater.end();
      }

      BinaryBuffer buf = BinaryBuffer.wrap(decompressed);
      int packetId = buf.readInt();
      Packet packet = PacketRegistry.create(packetId);
      if (packet == null) {
        throw new NetworkException("Unknown packet ID: " + packetId);
      }
      packet.read(buf);
      requireFullyRead(buf, packetId);
      out.add(packet);
    }
  }

  /**
   * Verifies that packet decoding consumed the complete payload.
   *
   * @param buffer the decoded packet buffer
   * @param packetId the decoded packet identifier, used in the failure message
   * @throws NetworkException if unread payload bytes remain
   */
  private static void requireFullyRead(BinaryBuffer buffer, int packetId) {
    if (buffer.readableBytes() != 0) {
      throw new NetworkException("Packet " + packetId + " left "
          + buffer.readableBytes() + " unread payload bytes");
    }
  }
}
