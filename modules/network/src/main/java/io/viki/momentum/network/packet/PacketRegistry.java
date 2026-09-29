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

import io.viki.momentum.network.NetworkException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Global registry that maps packet types to wire-format integer IDs and provides factory-based instantiation for
 * received packets.
 *
 * <p>The registry is not thread-safe.
 *
 * @see Packet
 */
public final class PacketRegistry {
  private static final List<Supplier<? extends Packet>> ID_TO_FACTORY = new ArrayList<>();
  private static final Map<Class<? extends Packet>, Integer> TYPE_TO_ID = new HashMap<>();

  static {
    register(ConnectionOpenPacket.class, ConnectionOpenPacket::new);
    register(ConnectionAckPacket.class, ConnectionAckPacket::new);
    register(HeartbeatPacket.class, HeartbeatPacket::new);
    register(EchoPacket.class, EchoPacket::new);
  }

  private PacketRegistry() {
  }

  /**
   * Registers a packet type and its factory, associating the given class with an auto-assigned integer ID for wire
   * encoding and decoding.
   *
   * @param type    the packet class to register
   * @param factory a supplier that produces new instances of the packet, typically a constructor reference
   * @return the assigned integer ID
   * @throws NetworkException if the type has already been registered
   */
  public static int register(Class<? extends Packet> type, Supplier<? extends Packet> factory) {
      if (TYPE_TO_ID.containsKey(type)) {
        throw new NetworkException("Packet type already registered: " + type.getName());
      }
      int id = ID_TO_FACTORY.size();
      ID_TO_FACTORY.add(factory);
      TYPE_TO_ID.put(type, id);
      return id;
  }

  /**
   * Creates a new packet instance from its wire-format ID by calling the registered factory.
   *
   * @param id the packet ID received on the wire
   * @return a new packet instance, or {@code null} if the ID is not registered
   */
  public static @Nullable Packet create(int id) {
      if (id < 0 || id >= ID_TO_FACTORY.size()) {
        return null;
      }
      Packet packet = ID_TO_FACTORY.get(id).get();
      TYPE_TO_ID.putIfAbsent(packet.getClass(), id);
      return packet;
  }

  /**
   * Looks up the registered ID for a packet class.
   *
   * @param type the packet class
   * @return the registered integer ID, or {@code -1} if the type has not been registered
   */
  public static int id(Class<? extends Packet> type) {
    return TYPE_TO_ID.getOrDefault(type, -1);
  }
}
