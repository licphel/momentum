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

package io.viki.momentum.network;

import io.viki.momentum.network.packet.Packet;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * In-process client/server transport used by single-player without bypassing packets.
 *
 * <p>Packets may be queued from another thread, but each endpoint's {@link ConnectionHost#process(Supplier)} method and
 * lifecycle callbacks should be driven from a single thread.
 */
public final class IntegratedConnection implements AutoCloseable {
  private final Endpoint server;
  private final Endpoint client;

  private IntegratedConnection() {
    UUID id = UUID.randomUUID();
    server = new Endpoint(id);
    client = new Endpoint(id);
    server.peer = client.connection;
    client.peer = server.connection;
  }

  /**
   * Creates an in-process transport with paired client and server endpoints.
   *
   * @return a new integrated transport
   */
  public static IntegratedConnection create() {
    return new IntegratedConnection();
  }

  /**
   * Returns the server endpoint of this transport.
   *
   * @return the server-side connection host
   */
  public ConnectionHost server() {
    return server;
  }

  /**
   * Returns the client endpoint of this transport.
   *
   * @return the client-side connection host
   */
  public ConnectionHost client() {
    return client;
  }

  @Override
  public void close() {
    client.close();
    server.close();
  }

  private static final class Endpoint implements ConnectionHost {
    private final LocalConnection connection;
    private final ConcurrentLinkedQueue<Packet> inbound = new ConcurrentLinkedQueue<>();
    private @Nullable LocalConnection peer;
    private @Nullable Consumer<Connection> onConnected;
    private @Nullable Consumer<Connection> onDisconnected;
    private boolean running;

    private Endpoint(UUID id) {
      connection = new LocalConnection(id, this);
    }

    @Override
    public CompletableFuture<Void> start() {
      running = true;
      Consumer<Connection> callback = onConnected;
      if (callback != null) {
        callback.accept(connection);
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public @Nullable Connection get(UUID netUuid) {
      return running && connection.id().equals(netUuid) ? connection : null;
    }

    @Override
    public Collection<Connection> connections() {
      return running ? List.of(connection) : List.of();
    }

    @Override
    public void process(Supplier<Object> contextSup) {
      Packet packet;
      while ((packet = inbound.poll()) != null) {
        packet.handle(connection, contextSup.get());
      }
    }

    @Override
    public void onConnected(Consumer<Connection> callback) {
      onConnected = callback;
    }

    @Override
    public void onDisconnected(Consumer<Connection> callback) {
      onDisconnected = callback;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public void close() {
      if (!running) {
        return;
      }
      running = false;
      Consumer<Connection> callback = onDisconnected;
      if (callback != null) {
        callback.accept(connection);
      }
      inbound.clear();
    }
  }

  private static final class LocalConnection implements Connection {
    private final UUID id;
    private final Endpoint owner;
    private long lastActivity = System.currentTimeMillis();

    private LocalConnection(UUID id, Endpoint owner) {
      this.id = id;
      this.owner = owner;
    }

    @Override public UUID id() { return id; }

    @Override
    public void send(Packet packet) {
      Endpoint target = owner.peer == null ? null : owner.peer.owner;
      if (target != null && target.running) {
        target.inbound.add(packet);
        lastActivity = System.currentTimeMillis();
      }
    }

    @Override public void close() { owner.close(); }
    @Override public boolean isActive() { return owner.running; }
    @Override public ConnectionState state() {
      return owner.running ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
    }
    @Override public long lastActivityTime() { return lastActivity; }
  }
}
