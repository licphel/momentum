package io.viki.momentum.network;

import io.netty.channel.nio.NioEventLoopGroup;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.util.multithread.Parallel;

import java.util.concurrent.TimeUnit;

/** Process-wide selector thread. Connections own channels, never the shared event loop. */
@InternalApi
final class NetworkThreads {
  static final NioEventLoopGroup GROUP = new NioEventLoopGroup(1, Parallel.threadFactory("Parallel-Network-"));

  static {
    Runtime.getRuntime().addShutdownHook(Parallel.threadFactory("Parallel-Network-close-").newThread(
        () -> GROUP.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly()));
  }

  private NetworkThreads() {
  }
}
