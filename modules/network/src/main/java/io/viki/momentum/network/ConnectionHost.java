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

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns one network endpoint and the logical connections reachable through it.
 *
 * <p>Implementations may receive packets asynchronously, but lifecycle callbacks and packet processing are invoked by
 * the thread that calls {@link #process(Supplier)}.
 */
public interface ConnectionHost extends AutoCloseable {
  /**
   * Creates a host that listens on all local interfaces.
   *
   * @param port the local TCP port, from {@code 0} through {@code 65535}
   * @return an unstarted server host
   * @throws IllegalArgumentException if the port is outside the valid range
   */
  static ConnectionHost createLAN(int port) {
    return ServerConnection.configured("0.0.0.0", port);
  }

  /**
   * Creates a dedicated server host that listens on all local interfaces.
   *
   * @param port the local TCP port, from {@code 0} through {@code 65535}
   * @return an unstarted server host
   * @throws IllegalArgumentException if the port is outside the valid range
   */
  static ConnectionHost createDedicated(int port) {
    return ServerConnection.configured("0.0.0.0", port);
  }

  /**
   * Creates a client host configured for the supplied remote address.
   *
   * @param host the remote server hostname or IP address
   * @param port the remote server port, from {@code 0} through {@code 65535}
   * @return an unstarted client host
   * @throws IllegalArgumentException if the host is blank or the port is outside the valid range
   */
  static ConnectionHost createRemote(String host, int port) {
    return ClientConnection.configured(host, port);
  }

  /**
   * Starts this host and begins its asynchronous network operation.
   *
   * @return a future that completes when the host has started
   */
  CompletableFuture<Void> start();

  /**
   * Finds an active connection by identifier.
   *
   * @param netUuid the connection identifier to find
   * @return the matching active connection, or {@code null} when no such connection exists
   */
  @Nullable Connection get(UUID netUuid);

  /**
   * Returns the active connections currently managed by this host.
   *
   * @return a snapshot of the active connections
   */
  Collection<Connection> connections();

  /**
   * Processes queued packets and lifecycle events on the calling thread.
   *
   * <p>The supplier is evaluated on this thread once for each inbound packet, immediately before its handler runs.
   *
   * @param contextSup the packet context supplier
   */
  void process(Supplier<Object> contextSup);

  /**
   * Registers the callback invoked when a connection becomes active.
   *
   * @param callback the callback to invoke for newly active connections
   */
  void onConnected(Consumer<Connection> callback);

  /**
   * Registers the callback invoked when a connection becomes inactive.
   *
   * @param callback the callback to invoke for disconnected connections
   */
  void onDisconnected(Consumer<Connection> callback);

  /**
   * Reports whether this host has been started and has not been closed.
   *
   * @return {@code true} if the host is running
   */
  boolean isRunning();

  @Override
  void close();
}
