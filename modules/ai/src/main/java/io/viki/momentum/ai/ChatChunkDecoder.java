package io.viki.momentum.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.viki.momentum.util.InternalApi;

import java.io.IOException;

/**
 * Converts native streaming envelopes into the text fragments exposed by {@link ResponseCallback}.
 */
@InternalApi
final class ChatChunkDecoder {
  private static final ObjectReader CHUNK_READER = new ObjectMapper().readerFor(JsonNode.class);

  private ChatChunkDecoder() {
  }

  /**
   * Delivers textual content from one or more adjacent streaming response envelopes.
   *
   * @param json raw response envelope text from the native model
   * @param callback destination for decoded text fragments
   * @throws LLMException if the response text is not valid JSON
   */
  static void decode(String json, ResponseCallback callback) {
    try (MappingIterator<JsonNode> chunks = CHUNK_READER.readValues(json)) {
      while (chunks.hasNextValue()) {
        emitChoices(chunks.nextValue().path("choices"), callback);
      }
    } catch (IOException exception) {
      throw new LLMException("Failed to decode llama.cpp streaming response", exception);
    }
  }

  private static void emitChoices(JsonNode choices, ResponseCallback callback) {
    if (!choices.isArray()) {
      return;
    }
    for (JsonNode choice : choices) {
      JsonNode content = choice.path("delta").path("content");
      if (content.isTextual() && !content.textValue().isEmpty()) {
        callback.onToken(content.textValue());
      }
    }
  }
}
