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

package net.fmhi.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.fmhi.codec.streaming.CursorBuffer;
import net.fmhi.network.NetworkException;
import net.fmhi.network.packet.Packet;
import net.fmhi.network.packet.PacketRegistry;

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
 * @see PacketEncoder
 */
public final class PacketDecoder extends ByteToMessageDecoder {
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

  private void decodeRaw(ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 4) {
      return;
    }
    CursorBuffer buf = new NettyCursorBuffer(in);
    int packetId = buf.readInt();
    Packet packet = PacketRegistry.create(packetId);
    if (packet == null) {
      throw new NetworkException("Unknown packet ID: " + packetId);
    }
    packet.read(buf);
    // sync Netty cursors
    in.readerIndex(buf.readerIndex());
    out.add(packet);
  }

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
      CursorBuffer buf = new NettyCursorBuffer(in);
      int packetId = buf.readInt();
      Packet packet = PacketRegistry.create(packetId);
      if (packet == null) {
        throw new NetworkException("Unknown packet ID: " + packetId);
      }
      packet.read(buf);
      in.readerIndex(buf.readerIndex());
      out.add(packet);
    } else {
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
        inflater.end();
        if (resultLen != uncompressedLen) {
          throw new NetworkException("Decompressed size mismatch: expected " + uncompressedLen + ", got " + resultLen);
        }
      } catch (DataFormatException e) {
        inflater.end();
        throw new NetworkException("Failed to decompress packet", e);
      }

      CursorBuffer buf = CursorBuffer.wrap(decompressed);
      int packetId = buf.readInt();
      Packet packet = PacketRegistry.create(packetId);
      if (packet == null) {
        throw new NetworkException("Unknown packet ID: " + packetId);
      }
      packet.read(buf);
      out.add(packet);
    }
  }
}
