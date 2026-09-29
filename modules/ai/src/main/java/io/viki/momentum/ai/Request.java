package io.viki.momentum.ai;

/**
 * Immutable prompt and sampling settings for one language-model request.
 *
 * @param systemPrompt optional system-level instruction text
 * @param prompt user prompt text
 * @param maxTokens maximum number of generated tokens
 * @param temperature sampling temperature
 * @param grammar optional grammar constraint
 */
public record Request(String systemPrompt, String prompt, int maxTokens, float temperature, String grammar) {
  /** Default upper bound for generated tokens when no limit is supplied. */
  public static final int DEFAULT_MAX_TOKENS = 1024;
  /** Default sampling temperature for gameplay-oriented responses. */
  public static final float DEFAULT_TEMPERATURE = 0.3F;

  /**
   * Validates one request and rejects settings that cannot produce a useful response.
   *
   * @param systemPrompt optional system-level instruction text
   * @param prompt user prompt text, which must not be blank
   * @param maxTokens maximum number of generated tokens, which must be positive
   * @param temperature sampling temperature, which must be finite and non-negative
   * @param grammar optional grammar constraint, or an empty string when unconstrained
   * @throws IllegalArgumentException if the prompt or any numeric setting is invalid
   */
  public Request {
    if (prompt.isBlank()) {
      throw new IllegalArgumentException("AI prompt must not be blank");
    }
    if (maxTokens <= 0) {
      throw new IllegalArgumentException("AI maxTokens must be positive: " + maxTokens);
    }
    if (!Float.isFinite(temperature) || temperature < 0.0F) {
      throw new IllegalArgumentException("AI temperature must be finite and non-negative: " + temperature);
    }
  }

  /**
   * Creates a request with the standard unconstrained default settings.
   *
   * @param prompt user prompt text, which must not be blank
   * @return a request using the standard token and temperature limits
   * @throws IllegalArgumentException if {@code prompt} is blank
   */
  public static Request of(String prompt) {
    return new Request("", prompt, DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE, "");
  }
}
