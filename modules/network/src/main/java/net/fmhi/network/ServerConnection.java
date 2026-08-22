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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.fmhi.network.codec.PacketDecoder;
import net.fmhi.network.codec.PacketEncoder;
import net.fmhi.network.packet.HeartbeatPacket;
import net.fmhi.network.packet.Packet;
import net.fmhi.network.packet.SessionAckPacket;
import net.fmhi.network.packet.SessionOpenPacket;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A server-side network endpoint that binds to a port, accepts client connections, and manages sessions.
 *
 * <p>Each accepted TCP channel begins a handshake: the server assigns the connection's shared identifier and sends it
 * in a {@link SessionOpenPacket}; the connection only becomes established — and {@code onConnected} only fires — when
 * the client answers with a {@link SessionAckPacket} echoing the same UUID. Until then the session reports
 * {@link SessionState#CONNECTING}, is not {@link Session#isActive() active}, its packets are dropped, and it is
 * evicted
 * silently after {@link NetSharedConstants#HANDSHAKE_TIMEOUT_MS}.
 *
 * <h3>Heartbeat and timeouts</h3>
 * <p>The server broadcasts heartbeat packets at the interval defined by
 * {@link NetSharedConstants#HEARTBEAT_INTERVAL_MS}.
 * Sessions that remain inactive for longer than {@link NetSharedConstants#SESSION_TIMEOUT_MS} are automatically
 * disconnected.
 *
 * <p>This class is thread-safe. {@link #send(Packet)} and {@link #send(UUID, Packet)} may be called from any thread,
 * while {@link #process()} should be called from a single dedicated thread.
 *
 * @see Connection
 * @see Session
 */
public final class ServerConnection implements AutoCloseable {
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final ChannelGroup channels;
  private final ConcurrentLinkedQueue<PacketWithSession> inbound;
  private final ConcurrentHashMap<UUID, NettySession> sessions;
  private final ConcurrentLinkedQueue<Session> connectEvents;
  private final ConcurrentLinkedQueue<Session> disconnectEvents;

  private @Nullable Channel serverChannel;
  private volatile boolean running;
  private volatile long lastHeartbeatSent;

  private @Nullable Consumer<Session> onConnected;
  private @Nullable Consumer<Session> onDisconnected;

  private ServerConnection() {
    this.bossGroup = new NioEventLoopGroup(1);
    this.workerGroup = new NioEventLoopGroup();
    this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    this.inbound = new ConcurrentLinkedQueue<>();
    this.sessions = new ConcurrentHashMap<>();
    this.connectEvents = new ConcurrentLinkedQueue<>();
    this.disconnectEvents = new ConcurrentLinkedQueue<>();
  }

  /**
   * Creates a new, unbound server.
   *
   * @return a new server instance
   */
  public static ServerConnection open() {
    return new ServerConnection();
  }

  /**
   * Binds to a port and begins accepting client connections asynchronously.
   *
   * @param port the TCP port to bind to
   * @return a future that completes when the server is bound and listening
   * @throws NetworkException if the server is already running
   */
  @SuppressWarnings("all")
  public CompletableFuture<Void> bind(int port) {
    if (running) {
      throw new NetworkException("Server is already running");
    }

    PacketDecoder decoder = new PacketDecoder();
    PacketEncoder encoder = new PacketEncoder();

    CompletableFuture<Void> future = new CompletableFuture<>();

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.TCP_NODELAY, true).childHandler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        // Outbound traversal is tail→head, so the encoders must sit head-side of
        // the session handler: writes from sessionHandler pass packetEncoder
        // (Packet→bytes) then frameEncoder (prepend length) before the socket.
        // inbound
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(NetSharedConstants.FRAME_MAX_SIZE, 0, 4, 0, 4));
        p.addLast("packetDecoder", decoder);
        // outbound
        p.addLast("frameEncoder", new LengthFieldPrepender(4));
        p.addLast("packetEncoder", encoder);
        // business
        p.addLast("sessionHandler", new ServerSessionHandler());
      }
    });

    bootstrap.bind(port).addListener((ChannelFutureListener) f -> {
      if (f.isSuccess()) {
        serverChannel = f.channel();
        running = true;
        lastHeartbeatSent = System.currentTimeMillis();
        future.complete(null);
      } else {
        Throwable cause = f.cause();
        future.completeExceptionally(cause != null ? cause : new NetworkException("Failed to bind to port " + port));
      }
    });

    return future;
  }

  /**
   * Broadcasts a packet to every connected session.
   *
   * @param packet the packet to broadcast
   */
  public void send(Packet packet) {
    for (NettySession session : sessions.values()) {
      session.send(packet);
    }
  }

  /**
   * Sends a packet to a single session identified by its identifier.
   *
   * @param sessionId the target session identifier
   * @param packet    the packet to send
   */
  public void send(UUID sessionId, Packet packet) {
    NettySession session = sessions.get(sessionId);
    if (session != null) {
      session.send(packet);
    }
  }

  /**
   * Processes a single tick: drains the inbound packet queue, fires pending lifecycle events, transmits heartbeats, and
   * evicts stale or never-established sessions.
   *
   * <p>This method should be called once per frame from the main thread. Inbound packets receive their
   * {@link Packet#handle(Session)} call on the calling thread.
   */
  public void process() {
    Session s;
    while ((s = connectEvents.poll()) != null) {
      Consumer<Session> cb = onConnected;
      if (cb != null) {
        cb.accept(s);
      }
    }
    while ((s = disconnectEvents.poll()) != null) {
      Consumer<Session> cb = onDisconnected;
      if (cb != null) {
        cb.accept(s);
      }
    }

    PacketWithSession entry;
    while ((entry = inbound.poll()) != null) {
      entry.packet.handle(entry.session);
    }

    long now = System.currentTimeMillis();
    if (now - lastHeartbeatSent >= NetSharedConstants.HEARTBEAT_INTERVAL_MS) {
      send(new HeartbeatPacket());
      lastHeartbeatSent = now;
    }

    Iterator<NettySession> iter = sessions.values().iterator();
    while (iter.hasNext()) {
      NettySession session = iter.next();
      // Channel liveness (not Session.isActive(): a handshaking session is not
      // yet active but its channel is very much alive) decides eviction.
      if (!session.channel.isActive()) {
        iter.remove();
        channels.remove(session.channel);
        if (session.established) {
          disconnectEvents.add(session);
        }
      } else if (!session.established && now - session.createdAt > NetSharedConstants.HANDSHAKE_TIMEOUT_MS) {
        // Handshake never finished: agreement was never reached, so evict
        // silently — no onConnected/onDisconnected pair ever fires.
        session.close();
        iter.remove();
        channels.remove(session.channel);
      } else if (session.established && now - session.lastActivityTime() > NetSharedConstants.SESSION_TIMEOUT_MS) {
        session.close();
        iter.remove();
        channels.remove(session.channel);
        disconnectEvents.add(session);
      }
    }
  }

  /**
   * Registers a callback invoked when a new session is established.
   *
   * <p>The callback fires during {@link #process()} on the calling thread, after the handshake completed and the client
   * acknowledged the shared connection identifier.
   *
   * @param callback the callback to invoke for each new connection
   */
  public void onConnected(Consumer<Session> callback) {
    this.onConnected = callback;
  }

  /**
   * Registers a callback invoked when a session is disconnected.
   *
   * <p>The callback fires during {@link #process()} on the calling thread.
   *
   * @param callback the callback to invoke for each disconnection
   */
  public void onDisconnected(Consumer<Session> callback) {
    this.onDisconnected = callback;
  }

  /**
   * Returns a snapshot of all currently connected sessions.
   *
   * @return an unmodifiable collection of sessions
   */
  public Collection<Session> sessions() {
    return Collections.unmodifiableCollection(new ArrayList<>(sessions.values()));
  }

  /**
   * Forcefully disconnects the session with the given identifier.
   *
   * @param sessionId the session to disconnect
   */
  public void kick(UUID sessionId) {
    NettySession session = sessions.get(sessionId);
    if (session != null) {
      session.close();
    }
  }

  /**
   * Returns whether the server is currently bound and accepting connections.
   *
   * @return {@code true} if the server is running
   */
  public boolean isRunning() {
    return running;
  }

  @Override
  public void close() {
    running = false;
    channels.close().awaitUninterruptibly(2000);
    if (serverChannel != null) {
      serverChannel.close().awaitUninterruptibly(2000);
    }
    bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    sessions.clear();
  }

  private record PacketWithSession(Packet packet, Session session) {
  }

  private static final class NettySession implements Session {
    private final UUID id;
    private final Channel channel;
    private final long createdAt;
    private volatile long lastActivity;
    /** Whether the client acknowledged the handshake; set once on the I/O thread, read from any thread. */
    private volatile boolean established;

    NettySession(UUID id, Channel channel) {
      this.id = id;
      this.channel = channel;
      this.createdAt = System.currentTimeMillis();
      this.lastActivity = createdAt;
    }

    void touch() {
      lastActivity = System.currentTimeMillis();
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public void send(Packet packet) {
      if (channel.isActive()) {
        channel.writeAndFlush(packet);
        touch();
      }
    }

    @Override
    public void close() {
      channel.close();
    }

    @Override
    public boolean isActive() {
      // A session is only usable once both endpoints agreed on the identifier.
      return established && channel.isActive();
    }

    @Override
    public SessionState state() {
      if (!established) {
        return SessionState.CONNECTING;
      }
      if (channel.isActive()) {
        return SessionState.CONNECTED;
      }
      if (channel.isOpen()) {
        return SessionState.DISCONNECTING;
      }
      return SessionState.DISCONNECTED;
    }

    @Override
    public long lastActivityTime() {
      return lastActivity;
    }

    @Override
    public String toString() {
      return "Session[" + id + " " + state() + "]";
    }
  }

  private final class ServerSessionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      Channel ch = ctx.channel();
      channels.add(ch);
      UUID connId = UUID.randomUUID();
      NettySession session = new NettySession(connId, ch);
      sessions.put(connId, session);

      // Assign the shared connection identifier; the session stays CONNECTING
      // until the client echoes it back in a SessionAckPacket. Write failures
      // surface on the promise, not via exceptionCaught — log them or they
      // vanish silently.
      ctx.writeAndFlush(new SessionOpenPacket(connId)).addListener((ChannelFutureListener) f -> {
        if (!f.isSuccess()) {
          System.err.println("[fmhi-net/server] session-open write failed: " + f.cause());
        }
      });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof SessionAckPacket ack) {
        NettySession session = sessionFor(ctx.channel());
        if (session == null) {
          return;
        }
        if (!session.established && ack.connId().equals(session.id)) {
          session.established = true;
          connectEvents.add(session);
        } else if (!ack.connId().equals(session.id)) {
          // Echo mismatch: the protocol is broken, abort the connection.
          ctx.close();
        }
        return;
      }
      if (msg instanceof Packet packet) {
        NettySession session = sessionFor(ctx.channel());
        if (session != null && session.established) {
          // Packets from not-yet-established sessions are dropped.
          session.touch();
          inbound.add(new PacketWithSession(packet, session));
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }

    private @Nullable NettySession sessionFor(Channel ch) {
      for (NettySession s : sessions.values()) {
        if (s.channel == ch) {
          return s;
        }
      }
      return null;
    }
  }
}
