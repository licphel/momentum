package io.viki.momentum.ai;

/**
 * Receives text fragments and terminal notifications from an asynchronous model stream.
 * Implementations are called on an inference worker and should marshal updates to a UI thread
 * when necessary.
 */
@FunctionalInterface
public interface ResponseCallback {
  /**
   * Receives the next non-empty text fragment.
   *
   * @param token text produced since the previous callback notification
   */
  void onToken(String token);

  /**
   * Receives notification that the model produced a complete response.
   */
  default void onComplete() {
  }

  /**
   * Receives notification that the stream terminated with a failure.
   *
   * @param failure failure that interrupted response generation
   */
  default void onError(Throwable failure) {
  }
}
