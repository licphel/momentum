package io.viki.momentum.ai;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Describes the local model file and optional accelerator usage for model initialization.
 *
 * @param modelPath path to the model file
 * @param gpuLayers number of layers eligible for GPU execution
 */
public record LanguageModelInfo(Path modelPath, int gpuLayers) {
  /**
   * Validates and normalizes a model loading configuration.
   *
   * @param modelPath path to an existing regular GGUF model file
   * @param gpuLayers number of model layers eligible for GPU execution
   * @throws IllegalArgumentException if the model file is missing or the layer count is negative
   */
  public LanguageModelInfo {
    modelPath = modelPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(modelPath)) {
      throw new IllegalArgumentException("GGUF model file does not exist: " + modelPath);
    }
    if (gpuLayers < 0) {
      throw new IllegalArgumentException("AI gpuLayers must be non-negative: " + gpuLayers);
    }
  }

  /**
   * Creates a configuration that keeps all model execution on the CPU.
   *
   * @param modelPath path to an existing regular GGUF model file
   * @return a CPU-only model configuration
   * @throws IllegalArgumentException if the model file is missing
   */
  public static LanguageModelInfo cpu(Path modelPath) {
    return new LanguageModelInfo(modelPath, 0);
  }
}
