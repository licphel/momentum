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
import io.netty.buffer.ByteBuf;
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
 * <p>Each connecting client transmits its identity during the handshake. The server exposes this via
 * {@link Session#remoteId()}.
 *
 * <h3>Heartbeat and timeouts</h3>
 * <p>The server broadcasts heartbeat packets at the interval defined by {@link NetConfig#HEARTBEAT_INTERVAL_MS}.
 * Sessions that remain inactive for longer than {@link NetConfig#SESSION_TIMEOUT_MS} are automatically disconnected.
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
        // identity receiver: reads the first 16 bytes as the client's UUID
        p.addLast("identityReceiver", new IdentityReceiver());
        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(NetConfig.FRAME_MAX_SIZE, 0, 4, 0, 4));
        p.addLast("packetDecoder", decoder);
        p.addLast("sessionHandler", new ServerSessionHandler());
        p.addLast("packetEncoder", encoder);
        p.addLast("frameEncoder", new LengthFieldPrepender(4));
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
   * evicts stale sessions.
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
    if (now - lastHeartbeatSent >= NetConfig.HEARTBEAT_INTERVAL_MS) {
      send(new HeartbeatPacket());
      lastHeartbeatSent = now;
    }

    Iterator<NettySession> iter = sessions.values().iterator();
    while (iter.hasNext()) {
      NettySession session = iter.next();
      if (!session.isActive()) {
        iter.remove();
        channels.remove(session.channel);
        disconnectEvents.add(session);
      } else if (now - session.lastActivityTime() > NetConfig.SESSION_TIMEOUT_MS) {
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
   * <p>The callback fires during {@link #process()} on the calling thread.
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
   * Finds a session by the client's self-reported identity.
   *
   * @param clientId the client's self-reported identity
   * @return the session if found, otherwise an empty {@link Optional}
   */
  public Optional<Session> sessionByClientId(UUID clientId) {
    for (NettySession s : sessions.values()) {
      UUID rid = s.remoteId();
      if (clientId.equals(rid)) {
        return Optional.of(s);
      }
    }
    return Optional.empty();
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

  private static final class IdentityReceiver extends ChannelInboundHandlerAdapter {
    private boolean received;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (received) {
        ctx.fireChannelRead(msg);
        return;
      }
      if (msg instanceof ByteBuf buf && buf.readableBytes() >= 16) {
        long msb = buf.readLong();
        long lsb = buf.readLong();
        ctx.channel().attr(NetConfig.CLIENT_ID_KEY).set(new UUID(msb, lsb));
        received = true;
        if (buf.isReadable()) {
          ctx.fireChannelRead(buf);
        } else {
          buf.release();
        }
      } else {
        ctx.fireChannelRead(msg);
      }
    }
  }

  private record PacketWithSession(Packet packet, Session session) {
  }

  private static final class NettySession implements Session {
    private final UUID id;
    private final Channel channel;
    private volatile long lastActivity;

    NettySession(UUID id, Channel channel) {
      this.id = id;
      this.channel = channel;
      this.lastActivity = System.currentTimeMillis();
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
      return channel.isActive();
    }

    @Override
    public SessionState state() {
      if (channel.isActive()) {
        return SessionState.CONNECTED;
      }
      if (channel.isOpen()) {
        return SessionState.DISCONNECTING;
      }
      return SessionState.DISCONNECTED;
    }

    @Override
    public UUID remoteId() {
      return channel.attr(NetConfig.CLIENT_ID_KEY).get();
    }

    @Override
    public long lastActivityTime() {
      return lastActivity;
    }

    void touch() {
      lastActivity = System.currentTimeMillis();
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
      UUID sid = UUID.randomUUID();
      NettySession session = new NettySession(sid, ch);
      sessions.put(sid, session);

      // Send the assigned session UUID back to the client so it can use it as a remote identity.
      ByteBuf idBuf = ctx.alloc().buffer(16);
      idBuf.writeLong(sid.getMostSignificantBits());
      idBuf.writeLong(sid.getLeastSignificantBits());
      ctx.writeAndFlush(idBuf);

      connectEvents.add(session);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      // Session cleanup is deferred to process().
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof Packet packet) {
        UUID sid = null;
        for (Map.Entry<UUID, NettySession> entry : sessions.entrySet()) {
          if (entry.getValue().channel == ctx.channel()) {
            sid = entry.getKey();
            entry.getValue().touch();
            break;
          }
        }
        NettySession session = sid != null ? sessions.get(sid) : null;
        if (session != null) {
          inbound.add(new PacketWithSession(packet, session));
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }
  }
}
