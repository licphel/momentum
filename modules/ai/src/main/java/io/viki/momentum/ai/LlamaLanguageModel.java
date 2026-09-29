package io.viki.momentum.ai;

import io.viki.momentum.util.InternalApi;
import net.ladenthin.llama.LlamaModel;
import net.ladenthin.llama.parameters.InferenceParameters;
import net.ladenthin.llama.parameters.ModelParameters;
import net.ladenthin.llama.value.Pair;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides the {@link LanguageModel} contract through a native llama.cpp model instance.
 */
@InternalApi
final class LlamaLanguageModel implements LanguageModel {
  private final LlamaModel model;
  private final ExecutorService streamingExecutor;

  private LlamaLanguageModel(LlamaModel model) {
    this.model = model;
    streamingExecutor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("momentum-ai-stream").daemon(true).factory());
  }

  /**
   * Opens a native model using the supplied local configuration.
   *
   * @param config model file and execution settings
   * @return an initialized native model adapter
   * @throws LLMException if native model initialization fails
   */
  static LlamaLanguageModel open(LanguageModelInfo config) {
    ModelParameters parameters = new ModelParameters()
        .setModel(config.modelPath().toString())
        .setGpuLayers(config.gpuLayers());
    try {
      return new LlamaLanguageModel(new LlamaModel(parameters));
    } catch (RuntimeException | LinkageError exception) {
      throw new LLMException("Failed to load GGUF model: " + config.modelPath(), exception);
    }
  }

  private static InferenceParameters parameters(Request request) {
    InferenceParameters parameters = InferenceParameters.empty()
        .withMessages(request.systemPrompt(), List.of(new Pair<>("user", request.prompt())))
        .withNPredict(request.maxTokens())
        .withTemperature(request.temperature());
    if (!request.grammar().isEmpty()) {
      parameters.withGrammar(request.grammar());
    }
    return parameters;
  }

  @Override
  public CompletableFuture<String> complete(Request request) {
    InferenceParameters parameters = parameters(request);
    try {
      return model.chatCompleteTextAsync(parameters)
          .exceptionallyCompose(exception -> CompletableFuture.failedFuture(
              new LLMException("Local AI inference failed", exception)));
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(new LLMException("Local AI inference failed", exception));
    }
  }

  @Override
  public CompletableFuture<Void> stream(Request request, ResponseCallback callback) {
    InferenceParameters parameters = parameters(request);
    CompletableFuture<Void> response = CompletableFuture.runAsync(
        () -> model.streamChatCompletion(parameters,
            chunk -> ChatChunkDecoder.decode(chunk, callback)), streamingExecutor);
    response.whenComplete((ignored, failure) -> {
      if (failure == null) {
        callback.onComplete();
      } else {
        callback.onError(failure);
      }
    });
    return response;
  }

  @Override
  public void close() {
    streamingExecutor.close();
    model.close();
  }
}
