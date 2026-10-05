package io.viki.momentum.international;

import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

/**
 * Per-download subscriber; all mutable transfer state is guarded by its monitor.
 */
@InternalApi
final class LanguageDownload implements HttpResponse.BodySubscriber<String> {
  /** Maximum delay before the first response body byte, in nanoseconds. */
  private static final long FIRST_BYTE_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
  private static final long WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
  private static final long MIN_BYTES_PER_SECOND = 128 * 1024;
  private final CompletableFuture<String> body = new CompletableFuture<>();
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final long started = System.nanoTime();
  private long windowStart;
  private long windowBytes;
  private boolean receivedByte;
  private Flow.@Nullable Subscription subscription;

  /**
   * Retrieves raw UTF-8 content within the first-byte deadline and sustained-throughput limit.
   * Redirects are followed under the same deadline; only a final HTTP 200 response is accepted.
   * The calling thread blocks, and transfer resources are released on success or failure.
   *
   * @param uri HTTP or HTTPS content URI
   * @return complete response text
   * @throws IOException              if the request fails, its status is rejected, or a transfer limit is exceeded
   * @throws IllegalArgumentException if the URI cannot be used for an HTTP request
   */
  static String fetch(URI uri) throws IOException {
    LanguageDownload download = new LanguageDownload();
    try (var watchdog = Executors.newSingleThreadScheduledExecutor(r ->
        Thread.ofPlatform().daemon().name("language-download-watchdog").unstarted(r));
         HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
      var timer = watchdog.scheduleAtFixedRate(download::checkSpeed, 500, 25, TimeUnit.MILLISECONDS);
      var request = client.sendAsync(HttpRequest.newBuilder(uri).GET().build(), response -> {
        if (response.statusCode() != 200) {
          download.onError(new IOException("Language HTTP status: " + response.statusCode()));
        }
        return download;
      });
      request.whenComplete((response, error) -> {
        if (error != null) {
          download.onError(error);
        }
      });
      try {
        return download.body.join();
      } catch (CompletionException e) {
        throw new IOException("Language download failed: " + uri, e.getCause());
      } finally {
        timer.cancel(false);
        request.cancel(true);
        client.shutdownNow();
      }
    }
  }

  /**
   * Abandons a pending transfer when its first-byte deadline or throughput limit is exceeded.
   * Completed transfers are unaffected; this method is safe for concurrent timer and body callbacks.
   */
  private synchronized void checkSpeed() {
    if (body.isDone()) {
      return;
    }
    long now = System.nanoTime();
    if (!receivedByte) {
      if (now - started >= FIRST_BYTE_TIMEOUT_NANOS) {
        onError(new IOException("Language first byte exceeded 1500 ms"));
      }
    } else if (now - windowStart >= WINDOW_NANOS) {
      if (windowBytes * TimeUnit.SECONDS.toNanos(1) / (now - windowStart) < MIN_BYTES_PER_SECOND) {
        onError(new IOException("Language transfer slower than 128 KiB/s"));
      } else {
        windowStart = now;
        windowBytes = 0;
      }
    }
  }

  @Override
  public CompletionStage<String> getBody() {
    return body;
  }

  @Override
  public synchronized void onSubscribe(Flow.Subscription incoming) {
    subscription = incoming;
    if (body.isDone()) {
      incoming.cancel();
    } else {
      incoming.request(Long.MAX_VALUE);
    }
  }

  @Override
  public synchronized void onNext(List<ByteBuffer> buffers) {
    checkSpeed();
    if (body.isDone()) {
      return;
    }
    for (ByteBuffer buffer : buffers) {
      int count = buffer.remaining();
      if (count == 0) {
        continue;
      }
      if (!receivedByte) {
        receivedByte = true;
        windowStart = System.nanoTime();
      }
      byte[] chunk = new byte[count];
      buffer.get(chunk);
      bytes.writeBytes(chunk);
      windowBytes += count;
    }
  }

  @Override
  public synchronized void onError(Throwable error) {
    body.completeExceptionally(error);
    if (subscription != null) {
      subscription.cancel();
    }
  }

  @Override
  public synchronized void onComplete() {
    checkSpeed();
    body.complete(bytes.toString(StandardCharsets.UTF_8));
  }
}
