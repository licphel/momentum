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

package net.fmhi.gfx.shader;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.pipe.PipelineDesc;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A linked shader program composed of one or more compiled {@link ShaderModule} instances.
 *
 * <p>A graphics pipeline requires a linked program (vertex + fragment at
 * minimum; geometry is optional). Compute pipelines use a single compute module. Linking resolves cross-stage
 * references and validates interface matching. Link errors are reported as exceptions at creation time.
 *
 * <p>Once linked, the program is immutable. The individual modules may be
 * retained or closed independently after linking — the linked program holds its own references to the compiled GPU
 * objects.
 *
 * <p><b>Thread safety:</b> immutable after linking — safe to read from any
 * thread.
 *
 * @see ShaderModule
 * @see PipelineDesc.Builder#shaderProgram
 */
public interface ShaderProgram extends AutoCloseable {
  /**
   * Loads a vertex + fragment shader pair from classpath resources and
   * links them into a program.
   *
   * <p>File extension determines the source language: {@code .hlsl} for
   * HLSL, anything else for GLSL.
   *
   * @param device   the graphics device
   * @param vertPath classpath path to the vertex shader
   * @param fragPath classpath path to the fragment shader
   * @return a linked shader program
   * @throws GraphicsException if loading, compilation, or linking fails
   */
  static ShaderProgram load(Device device, String vertPath, String fragPath) {
    String vertSrc = ShaderProgram.loadResource(vertPath);
    String fragSrc = ShaderProgram.loadResource(fragPath);
    ShaderModule vert = device.getShaderModule(new ShaderModuleDesc(ShaderType.VERTEX, vertSrc));
    ShaderModule frag = device.getShaderModule(new ShaderModuleDesc(ShaderType.FRAGMENT, fragSrc));
    return device.getShaderProgram(vert, frag);
  }

  private static String loadResource(String path) {
    var url = ShaderProgram.class.getResource(path);
    if (url == null) {
      throw new GraphicsException("Shader resource not found: " + path);
    }
    try (var in = url.openStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new GraphicsException("Failed to read shader: " + path, e);
    }
  }

  /**
   * Returns the shader modules that were linked into this program.
   *
   * @return the constituent modules in link order
   */
  ShaderModule[] modules();

  /**
   * Returns the link error message, or {@code null} if linking succeeded.
   *
   * @return the info log on failure, or {@code null}
   */
  @Nullable String checkCompilationError();

  @Override
  void close();
}
