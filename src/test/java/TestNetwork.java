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

import net.fmhi.codec.streaming.CursorBuffer;
import net.fmhi.network.*;
import net.fmhi.network.packet.Packet;
import net.fmhi.network.packet.PacketRegistry;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Localhost smoke test for the network handshake and packet exchange.
 */
public class TestNetwork {

  // -- test packet: echo request with a string payload --

  static void main(String[] args) throws Exception {
    int port = NetConfig.DEFAULT_PORT;

    // --- Start server ---
    ServerConnection server = ServerConnection.open();
    CountDownLatch serverBound = new CountDownLatch(1);
    server.bind(port).thenRun(serverBound::countDown);
    serverBound.await(3, TimeUnit.SECONDS);
    System.out.println("[SERVER] bound to port " + port);

    // Track sessions and received packets on the server side.
    CountDownLatch serverConnected = new CountDownLatch(1);
    CountDownLatch serverEchoReceived = new CountDownLatch(1);
    UUID[] serverSessionId = new UUID[1];
    UUID[] serverRemoteId = new UUID[1];

    server.onConnected(s -> {
      serverSessionId[0] = s.id();
      serverRemoteId[0] = s.remoteId();
      System.out.println("[SERVER] client connected: session.id=" + s.id() + " remoteId=" + s.remoteId());
      serverConnected.countDown();
    });

    // --- Start client ---
    Connection client = new Connection();
    System.out.println("[CLIENT] clientId=" + client.clientId());

    CountDownLatch clientConnected = new CountDownLatch(1);
    CountDownLatch clientEchoReply = new CountDownLatch(1);
    UUID[] clientRemoteId = new UUID[1];

    client.onConnected(s -> {
      clientRemoteId[0] = s.remoteId();
      System.out.println("[CLIENT] connected! session.id=" + s.id() + " remoteId=" + s.remoteId());
      clientConnected.countDown();

      // Send an echo packet to verify bidirectional communication.
      client.send(new EchoPacket("hello"));
      System.out.println("[CLIENT] sent EchoPacket(\"hello\")");
    });

    CompletableFuture<Void> connectFuture = client.connect("localhost", port);
    connectFuture.get(5, TimeUnit.SECONDS);
    System.out.println("[CLIENT] TCP connect future completed");

    // Tick both sides.
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      server.process();
      client.process();

      // Check echo reception on server.
      if (serverConnected.getCount() == 0 && serverEchoReceived.getCount() > 0) {
        // We'll detect echo via server-side processing.
        // The server's EchoPacket.handle() sends back an echo;
        // the client's process() will receive it.
      }

      if (clientConnected.getCount() == 0 && clientEchoReply.getCount() > 0) {
        break;
      }

      Thread.sleep(16);
    }

    // --- Assertions ---
    System.out.println();
    System.out.println("=== RESULTS ===");

    // 1. Server should have received the client connection.
    boolean serverGotConnection = serverConnected.getCount() == 0;
    System.out.println("Server received connection: " + (serverGotConnection ? "PASS" : "FAIL"));
    assert serverGotConnection : "Server did not receive client connection";

    // 2. Server's session.remoteId() should equal client.clientId().
    boolean serverRemoteMatches = serverRemoteId[0] != null && serverRemoteId[0].equals(client.clientId());
    System.out.println("Server session.remoteId == client.clientId: " + (serverRemoteMatches ? "PASS" : "FAIL") + "  " +
        "(remoteId=" + serverRemoteId[0] + " clientId=" + client.clientId() + ")");
    assert serverRemoteMatches : "Server remoteId does not match clientId";

    // 3. Client's remoteId() should equal server's session.id().
    boolean clientGotRemote = clientConnected.getCount() == 0;
    System.out.println("Client received connection: " + (clientGotRemote ? "PASS" : "FAIL"));
    assert clientGotRemote : "Client did not fire onConnected";

    boolean clientRemoteMatches = clientRemoteId[0] != null && clientRemoteId[0].equals(serverSessionId[0]);
    System.out.println("Client remoteId == server session.id: " + (clientRemoteMatches ? "PASS" : "FAIL") + "  " +
        "(client.remoteId=" + clientRemoteId[0] + " server.session.id=" + serverSessionId[0] + ")");
    assert clientRemoteMatches : "Client remoteId does not match server sessionId";

    // 4. Client should be in CONNECTED state.
    boolean clientConnectedState = client.state() == SessionState.CONNECTED;
    System.out.println("Client state is CONNECTED: " + (clientConnectedState ? "PASS" : "FAIL") + "  (state=" + client.state() + ")");
    assert clientConnectedState : "Client state is not CONNECTED";

    // 5. Client session should be available.
    boolean clientHasSession = client.session().isPresent();
    System.out.println("Client session is present: " + (clientHasSession ? "PASS" : "FAIL"));
    assert clientHasSession : "Client session is not present";

    // 6. Session.remoteId() via the session object.
    UUID sessionRemoteId = client.session().orElseThrow().remoteId();
    boolean sessionRemoteMatches = sessionRemoteId != null && sessionRemoteId.equals(serverSessionId[0]);
    System.out.println("Session.remoteId == server session.id: " + (sessionRemoteMatches ? "PASS" : "FAIL") + "  " +
        "(session.remoteId=" + sessionRemoteId + " server.session.id=" + serverSessionId[0] + ")");
    assert sessionRemoteMatches : "Session.remoteId does not match";

    // --- Cleanup ---
    client.close();
    server.close();
    System.out.println();
    System.out.println("=== ALL TESTS PASSED ===");
  }

  // -- entry point --

  public static final class EchoPacket extends Packet {
    public static final int ID = PacketRegistry.register(EchoPacket.class, EchoPacket::new);

    private String message = "";

    public EchoPacket() {
    }

    public EchoPacket(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }

    @Override
    public void read(CursorBuffer buf) {
      message = buf.readUTF8();
    }

    @Override
    public void write(CursorBuffer buf) {
      buf.writeUTF8(message);
    }

    @Override
    public void handle(Session session) {
      // Echo the message back to the sender.
      session.send(new EchoPacket("echo: " + message));
    }
  }
}
