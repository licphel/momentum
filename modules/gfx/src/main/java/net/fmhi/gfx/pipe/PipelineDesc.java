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

package net.fmhi.gfx.pipe;

import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.gfx.shader.ShaderProgram;
import net.fmhi.gfx.shader.VertexLayout;
import org.jspecify.annotations.Nullable;

/**
 * Creates a new {@code PipelineDesc} describing an immutable render pipeline.
 *
 * @param blend           blend state
 * @param depth           depth-test state
 * @param stencil         stencil-test state
 * @param rasterization   rasterization state
 * @param shaderProgram   the linked shader program
 * @param vertexLayout    vertex attribute layout
 * @param resourceLayouts resource-set layouts
 * @param usage           pipeline category
 */
public record PipelineDesc(Blend blend, Depth depth, Stencil stencil, RasterizationDesc rasterization,
                           @Nullable ShaderProgram shaderProgram, VertexLayout vertexLayout,
                           ResourceSetLayout[] resourceLayouts, PipelineUsage usage) {
  /**
   * Builder for {@link PipelineDesc} with sensible defaults — alpha blend, disabled depth/stencil, empty vertex layout,
   * render usage.
   */
  public static final class Builder {
    public Blend blend = Blend.ALPHA_MIX;
    public Depth depth = Depth.DISABLED;
    public Stencil stencil = Stencil.DISABLED;
    public RasterizationDesc rasterization = RasterizationDesc.DEFAULT;
    public @Nullable ShaderProgram shaderProgram = null;
    public VertexLayout vertexLayout = VertexLayout.bake();
    public ResourceSetLayout[] resourceLayouts = new ResourceSetLayout[0];
    public PipelineUsage usage = PipelineUsage.RENDER;

    /**
     * Sets the blend state.
     *
     * @param v the blend state
     * @return this builder
     */
    public Builder blend(Blend v) {
      blend = v;
      return this;
    }

    /**
     * Sets the depth-test state.
     *
     * @param v the depth-test state
     * @return this builder
     */
    public Builder depth(Depth v) {
      depth = v;
      return this;
    }

    /**
     * Sets the stencil-test state.
     *
     * @param v the stencil-test state
     * @return this builder
     */
    public Builder stencil(Stencil v) {
      stencil = v;
      return this;
    }

    /**
     * Sets the rasterization state.
     *
     * @param v the rasterization state
     * @return this builder
     */
    public Builder rasterization(RasterizationDesc v) {
      rasterization = v;
      return this;
    }

    /**
     * Sets the linked shader program.
     *
     * @param v the shader program
     * @return this builder
     */
    public Builder shaderProgram(ShaderProgram v) {
      shaderProgram = v;
      return this;
    }

    /**
     * Sets the vertex attribute layout.
     *
     * @param v the vertex attribute layout
     * @return this builder
     */
    public Builder vertexLayout(VertexLayout v) {
      vertexLayout = v;
      return this;
    }

    /**
     * Sets the resource-set layouts.
     *
     * @param v the resource-set layouts
     * @return this builder
     */
    public Builder resourceLayouts(ResourceSetLayout... v) {
      resourceLayouts = v;
      return this;
    }

    /**
     * Sets the pipeline category.
     *
     * @param v the pipeline usage category
     * @return this builder
     */
    public Builder usage(PipelineUsage v) {
      usage = v;
      return this;
    }

    /**
     * Builds the pipeline descriptor.
     *
     * @return a new {@link PipelineDesc} with the current builder values
     */
    public PipelineDesc build() {
      return new PipelineDesc(blend, depth, stencil, rasterization, shaderProgram, vertexLayout, resourceLayouts,
          usage);
    }
  }
}
