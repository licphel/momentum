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
 * FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fmhi.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import net.fmhi.network.codec.PacketDecoder;
import net.fmhi.network.codec.PacketEncoder;
import net.fmhi.network.packet.HeartbeatPacket;
import net.fmhi.network.packet.Packet;
import net.fmhi.network.packet.SessionAckPacket;
import net.fmhi.network.packet.SessionOpenPacket;
import org.jspecify.annotations.Nullable;

import java.net.ConnectException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A client-side network endpoint that manages a single connection to a remote server.
 *
 * <p>The connection identifier is assigned by the server during the handshake: the server sends a
 * {@link SessionOpenPacket}, the client adopts the carried UUID as its own session id and answers with a session ack
 * echoing it back. Until that exchange completes, the connection is not established — {@link #state()} reports
 * {@link SessionState#CONNECTING}, {@link #session()} is empty, and no lifecycle callback has fired.
 *
 * <p>The typical lifecycle is: register lifecycle callbacks, call {@link #connect(String, int)}, then invoke
 * {@link #process()} each frame to drain inbound packets and fire lifecycle events.
 *
 * <p>Outbound packets may be sent from any thread via {@link #send(Packet)} or through the session object. Inbound
 * packet handling and lifecycle callbacks always execute on the thread that calls {@link #process()}.
 *
 * <p>This class is thread-safe for sending. {@link #process()} should be called from a single dedicated thread.
 *
 * @see ServerConnection
 * @see Session
 */
public final class Connection implements AutoCloseable {
  private final EventLoopGroup group;
  private final ConcurrentLinkedQueue<Packet> inbound;
  private final ConcurrentLinkedQueue<Session> connectEvents;
  private final ConcurrentLinkedQueue<Session> disconnectEvents;

  private volatile @Nullable Channel channel;
  private volatile @Nullable NettySession session;
  private volatile SessionState state = SessionState.DISCONNECTED;
  private volatile long lastHeartbeatSent;

  private @Nullable Consumer<Session> onConnected;
  private @Nullable Consumer<Session> onDisconnected;

  /**
   * Creates a network client, unconnected.
   */
  public Connection() {
    this.group = new NioEventLoopGroup(1);
    this.inbound = new ConcurrentLinkedQueue<>();
    this.connectEvents = new ConcurrentLinkedQueue<>();
    this.disconnectEvents = new ConcurrentLinkedQueue<>();
  }

  /**
   * Initiates an asynchronous connection to a server.
   *
   * <p>The returned future completes only when the handshake finishes — that is, when the server-assigned connection
   * identifier has been received, adopted, and acknowledged — not when the TCP channel alone is open.
   *
   * @param host the server hostname or IP address
   * @param port the server port
   * @return a future that completes when the connection is fully established
   * @throws NetworkException if the client is already connecting or connected
   */
  @SuppressWarnings("all")
  public CompletableFuture<Void> connect(String host, int port) {
    if (state != SessionState.DISCONNECTED) {
      throw new NetworkException("Client is already connecting or connected");
    }
    state = SessionState.CONNECTING;

    PacketDecoder decoder = new PacketDecoder();
    PacketEncoder encoder = new PacketEncoder();

    CompletableFuture<Void> future = new CompletableFuture<>();

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group).channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000).handler(new ChannelInitializer<>() {
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
        p.addLast("sessionHandler", new ClientSessionHandler(future));
      }
    });

    bootstrap.connect(host, port).addListener((ChannelFutureListener) f -> {
      if (!f.isSuccess()) {
        state = SessionState.DISCONNECTED;
        Throwable cause = f.cause();
        future.completeExceptionally(cause != null ? cause : new ConnectException("Connection refused"));
      }
      // On success the handshake continues: the server sends SessionOpenPacket,
      // ClientSessionHandler completes the future when the exchange finishes.
    });

    return future;
  }

  /**
   * Sends a packet to the server.
   *
   * <p>The call returns immediately; the packet is enqueued for asynchronous transmission. If the client is not
   * connected, the packet is silently dropped.
   *
   * @param packet the packet to send
   */
  public void send(Packet packet) {
    Channel ch = this.channel;
    if (ch != null && ch.isActive()) {
      ch.writeAndFlush(packet);
    }
  }

  /**
   * Processes a single tick: drains the inbound packet queue, fires pending lifecycle events, and transmits
   * heartbeats.
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

    Packet packet;
    while ((packet = inbound.poll()) != null) {
      Session sess = this.session;
      if (sess != null) {
        packet.handle(sess);
      }
    }

    Session sess = this.session;
    if (sess != null && sess.isActive()) {
      long now = System.currentTimeMillis();
      if (now - lastHeartbeatSent >= NetSharedConstants.HEARTBEAT_INTERVAL_MS) {
        send(new HeartbeatPacket());
        lastHeartbeatSent = now;
      }
    }
  }

  /**
   * Registers a callback invoked when the connection to the server is established.
   *
   * <p>The callback fires during {@link #process()} on the calling thread, after the handshake completed and both
   * endpoints agreed on the connection identifier.
   *
   * @param callback the callback to invoke on connection
   */
  public void onConnected(Consumer<Session> callback) {
    this.onConnected = callback;
  }

  /**
   * Registers a callback invoked when the connection to the server is lost.
   *
   * <p>The callback fires during {@link #process()} on the calling thread.
   *
   * @param callback the callback to invoke on disconnection
   */
  public void onDisconnected(Consumer<Session> callback) {
    this.onDisconnected = callback;
  }

  /**
   * Returns the current session if the handshake has completed.
   *
   * @return the session if active, otherwise an empty {@link Optional}
   */
  public Optional<Session> session() {
    Session s = this.session;
    return s != null && s.isActive() ? Optional.of(s) : Optional.empty();
  }

  /**
   * Returns the current lifecycle state of this client.
   *
   * <p>The state is {@link SessionState#CONNECTING} from the moment {@link #connect(String, int)} is called until the
   * handshake finishes, so a session identifier is only ever observable in {@link SessionState#CONNECTED}.
   *
   * @return the client state
   */
  public SessionState state() {
    return state;
  }

  @Override
  public void close() {
    Channel ch = this.channel;
    if (ch != null) {
      ch.close().awaitUninterruptibly(2000);
    }
    group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    state = SessionState.DISCONNECTED;
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
    public long lastActivityTime() {
      return lastActivity;
    }

    void touch() {
      lastActivity = System.currentTimeMillis();
    }
  }

  private final class ClientSessionHandler extends ChannelInboundHandlerAdapter {
    private final CompletableFuture<Void> connectFuture;

    ClientSessionHandler(CompletableFuture<Void> connectFuture) {
      this.connectFuture = connectFuture;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      channel = ctx.channel();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      boolean wasConnected = session != null;
      state = SessionState.DISCONNECTED;
      NettySession s = session;
      if (s != null) {
        disconnectEvents.add(s);
      }
      session = null;
      channel = null;
      if (!wasConnected) {
        // The channel died mid-handshake: agreement was never reached, so the
        // connect future must fail rather than hang until its own timeout.
        connectFuture.completeExceptionally(new ConnectException("Connection closed during handshake"));
      }
    }

    @SuppressWarnings("all")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof SessionOpenPacket open) {
        // The server assigned the shared connection identifier. Adopt it, then
        // acknowledge so the server can consider the connection established.
        UUID connId = open.connId();
        NettySession sess = new NettySession(connId, ctx.channel());
        session = sess;
        state = SessionState.CONNECTED;
        lastHeartbeatSent = System.currentTimeMillis();
        connectEvents.add(sess);
        connectFuture.complete(null);
        ctx.writeAndFlush(new SessionAckPacket(connId));
        return;
      }
      if (msg instanceof Packet packet) {
        NettySession s = session;
        if (s != null) {
          s.touch();
          inbound.add(packet);
        } else {
          System.err.println("[fmhi-net/client] dropped pre-handshake packet: " + packet);
        }
      }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }
  }
}
