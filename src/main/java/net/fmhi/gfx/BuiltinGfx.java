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

package net.fmhi.gfx;

import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.text.Font;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.TextureFilter;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Lazy-initialized built-in 2D pipelines and resource-set layouts.
 *
 * <p>Two pipelines are provided:
 * <ul>
 *   <li>{@link #pipeColor} — colored primitives (no texture)
 *   <li>{@link #pipeTexture} — textured primitives
 * </ul>
 *
 * <p>Call {@link #init(Device)} once before use; subsequent calls are no-ops.
 *
 * <p>All fields are thread-safe after initialization.
 */
@SuppressWarnings("all")
public final class BuiltinGfx {
  public static Pipeline pipeColor;
  public static Pipeline pipeTexture;
  public static ResourceSetLayout rslColor;
  public static ResourceSetLayout rslTexture;
  public static VertexLayout vlColor;
  public static VertexLayout vlTexture;
  public static Sampler sampler;
  public static @Nullable Font font;
  private static boolean init;

  private BuiltinGfx() {
  }

  /**
   * Initializes all built-in GPU resources. Idempotent.
   *
   * @param device the graphics device
   */
  public static void init(Device device) {
    if (init) {
      return;
    }
    init = true;

    ShaderProgram spCol = ShaderProgram.load(device, "/shaders/builtin_color.vert.hlsl", "/shaders/builtin_color.frag.hlsl");
    ShaderProgram spTex = ShaderProgram.load(device, "/shaders/builtin_texture.vert.hlsl", "/shaders/builtin_texture.frag.hlsl");

    rslColor = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER));
    rslTexture = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_tex", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE));

    vlColor = VertexLayout.bake(new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false));
    vlTexture = VertexLayout.bake(new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false));

    pipeColor = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(spCol).vertexLayout(vlColor).resourceLayouts(rslColor).build());
    pipeTexture = device.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(spTex).vertexLayout(vlTexture).resourceLayouts(rslTexture).build());

    sampler = device.getSampler(new SamplerDesc.Builder()
        .minFilter(TextureFilter.LINEAR).magFilter(TextureFilter.LINEAR).build());

    // Font init
    try {
      ByteBuffer ffd = ByteBuffer.wrap(BuiltinGfx.class.getResource("/font.ttf").openStream().readAllBytes());
      font = Font.open(device, ffd);
    } catch (Exception e) {
      throw new GraphicsException("Cannot initialize builtin font", e);
    }
  }
}
