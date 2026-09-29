package io.viki.momentum.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Provides asynchronous access to a locally loaded language model.
 * Implementations are safe to call from arbitrary threads and keep native model resources alive
 * until {@link #close()} is called.
 */
public interface LanguageModel extends AutoCloseable {
  /**
   * Loads a language model from the supplied local configuration.
   *
   * @param config model location and execution configuration
   * @return an opened language model that owns its native resources
   * @throws IllegalArgumentException if the configuration refers to an unusable model file
   * @throws LLMException if the native model cannot be initialized
   */
  static LanguageModel open(LanguageModelInfo config) {
    return LlamaLanguageModel.open(config);
  }

  /**
   * Completes one request without blocking the caller.
   *
   * @param request prompt and generation settings
   * @return a future completed with the assistant response, or exceptionally if generation fails
   */
  CompletableFuture<String> complete(Request request);

  /**
   * Generates a response incrementally for interactive consumers.
   *
   * @param request prompt and generation settings
   * @param callback receiver for text fragments and terminal state notifications
   * @return a future completed when the stream terminates
   */
  CompletableFuture<Void> stream(Request request, ResponseCallback callback);

  /**
   * Releases native model memory and stops outstanding model work.
   */
  @Override
  void close();
}
