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

package net.fmhi.gfx.opengl;

import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.VertexAttributeType;
import net.fmhi.gfx.shader.VertexLayout;
import net.fmhi.util.InternalApi;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Immutable OpenGL render pipeline that applies all fixed-function and programmable state via the {@link OpenGLCache}
 * diff mechanism.
 *
 * <p>On {@link #apply(OpenGLCache)}, each pipeline state block (blend, depth,
 * stencil, rasterization) is diffed against the cache so that only changed GL calls are issued. The shader program is
 * bound via {@code glUseProgram}.
 *
 * <p><b>VAO caching:</b> OpenGL VAOs record buffer bindings at setup time,
 * so a unique VAO is required for each (VBO, instance-VBO, IBO) triple. This class maintains a cache keyed by the
 * combined handles so that repeated draws with the same buffer triple reuse the same VAO.
 *
 * <p><b>Thread safety:</b> immutable after construction. {@link #apply} and
 * {@link #acquireVao} must be called on the render thread.
 */
@InternalApi
public final class OpenGLPipeline implements Pipeline {
  private static final int MAX_VAO_CACHE = 16;

  private final OpenGLDevice ctx;
  private final PipelineDesc desc;
  /**
   * LRU VAO cache keyed by {@code VaoKey}. Evicts the least recently accessed VAO when
   * over {@value #MAX_VAO_CACHE} entries.
   */
  private final Map<VaoKey, Integer> vaoCache = new LinkedHashMap<>(MAX_VAO_CACHE, 0.75F, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<VaoKey, Integer> eldest) {
      if (size() > MAX_VAO_CACHE) {
        glDeleteVertexArrays(eldest.getValue());
        return true;
      }
      return false;
    }
  };

  /**
   * Creates a new GL render pipeline.
   *
   * <p>The pipeline is immediately usable — no GL calls are made during
   * construction. State is applied lazily on the first {@link #apply}.
   *
   * @param ctx  the GL context
   * @param desc the pipeline descriptor (blend, depth, stencil, rasterization, shader)
   */
  OpenGLPipeline(OpenGLDevice ctx, PipelineDesc desc) {
    this.ctx = ctx;
    this.desc = desc;
  }

  private static void applyBlend(OpenGLCache cache, Blend b) {
    cache.setBlendEnabled(b.enable());
    if (b.enable()) {
      cache.setBlendFunc(OpenGLUtils.blendFactor(b.srcColor()), OpenGLUtils.blendFactor(b.dstColor()),
          OpenGLUtils.blendFactor(b.srcAlpha()), OpenGLUtils.blendFactor(b.dstAlpha()));
      cache.setBlendEquation(OpenGLUtils.blendFunc(b.colorFunc()), OpenGLUtils.blendFunc(b.alphaFunc()));
      cache.setBlendColor(b.constant().red(), b.constant().green(), b.constant().blue(), b.constant().alpha());
    }
  }

  private static void applyDepth(OpenGLCache cache, Depth d) {
    cache.setDepthTestEnabled(d.depthTest());
    if (d.depthTest()) {
      cache.setDepthWrite(d.depthWrite());
      cache.setDepthFunc(OpenGLUtils.compareOp(d.depthCompare()));
    }
  }

  private static void applyStencil(OpenGLCache cache, Stencil s) {
    boolean enabled = s.front() != StencilFace.DISABLED || s.back() != StencilFace.DISABLED;
    cache.setStencilTestEnabled(enabled);
    if (enabled) {
      applyStencilFace(cache, true, s.front());
      applyStencilFace(cache, false, s.back());
    }
  }

  private static void applyStencilFace(OpenGLCache cache, boolean front, StencilFace f) {
    int face = front ? GL_FRONT : GL_BACK;
    cache.setStencilFace(face, OpenGLUtils.compareOp(f.compareOp()), f.reference(), f.compareMask(),
        OpenGLUtils.stencilFunc(f.failOp()), OpenGLUtils.stencilFunc(f.depthFailOp()),
        OpenGLUtils.stencilFunc(f.passOp()), f.writeMask());
  }

  private static void applyRasterization(OpenGLCache cache, RasterizationDesc r) {
    cache.setPolygonMode(OpenGLUtils.polygonMode(r.polygonMode()));
    cache.setCullMode(OpenGLUtils.cullMode(r.cullMode()));
    cache.setFrontFace(OpenGLUtils.frontFace(r.frontFace()));
    cache.setDepthBias(r.depthBiasEnable(), r.depthBiasConstantFactor(), r.depthBiasSlopeFactor());
  }

  private static boolean isIntType(VertexAttributeType type) {
    return switch (type) {
      case INT8, INT16, INT32, UINT8, UINT16, UINT32 -> true;
      default -> false;
    };
  }

  /**
   * Applies all pipeline state to the GL context via the state cache.
   *
   * <p>Must be called on the render thread. Each state block is diffed
   * against the cache so only changed values trigger GL calls.
   *
   * @param cache the GL state cache (render-thread only)
   */
  public void apply(OpenGLCache cache) {
    applyBlend(cache, desc.blend());
    applyDepth(cache, desc.depth());
    applyStencil(cache, desc.stencil());
    applyRasterization(cache, desc.rasterization());

    if (desc.shaderProgram() instanceof OpenGLShaderProgram prog) {
      cache.useProgram(prog.handle);
    }
  }

  /**
   * Returns a configured VAO for the given buffer triple, creating one if necessary.
   *
   * <p>Must be called on the render thread. The VAO encodes the vertex
   * attribute pointers for the given VBO and instance VBO, using the vertex layout from the pipeline descriptor.
   *
   * @param vboHandle         the GL vertex buffer handle (must be non-zero)
   * @param instanceVboHandle the GL instance-data buffer handle (0 if not instanced)
   * @param eboHandle         the GL index buffer handle (0 if not indexed)
   * @return a GL VAO handle configured for this (VBO, instance-VBO, IBO) triple
   */
  public int acquireVao(int vboHandle, int instanceVboHandle, int eboHandle) {
    VaoKey key = new VaoKey(vboHandle, instanceVboHandle, eboHandle);
    Integer cached = vaoCache.get(key);
    if (cached != null) {
      return cached;
    }

    int vao = glGenVertexArrays();
    ctx.cache.bindVao(vao);

    VertexLayout layout = desc.vertexLayout();

    // Set up per-instance attributes (from instance VBO, divisor > 0)
    if (instanceVboHandle != 0 && layout.instanceStride > 0) {
      ctx.cache.bindBufferForce(GL_ARRAY_BUFFER, instanceVboHandle);
      for (VertexLayout.Attr attr : layout.attrs) {
        if (attr.divisor() > 0) {
          glEnableVertexAttribArray(attr.location());
          int glType = OpenGLUtils.vertexAttribType(attr.type());
          if (isIntType(attr.type()) && !attr.normalized()) {
            glVertexAttribIPointer(attr.location(), attr.components(), glType, layout.instanceStride, attr.offset());
          } else {
            glVertexAttribPointer(attr.location(), attr.components(), glType, attr.normalized(), layout.instanceStride,
                attr.offset());
          }
          glVertexAttribDivisor(attr.location(), attr.divisor());
        }
      }
    }

    // Set up per-vertex attributes (from main VBO, divisor = 0)
    ctx.cache.bindBufferForce(GL_ARRAY_BUFFER, vboHandle);
    for (VertexLayout.Attr attr : layout.attrs) {
      if (attr.divisor() == 0) {
        glEnableVertexAttribArray(attr.location());
        int glType = OpenGLUtils.vertexAttribType(attr.type());
        if (isIntType(attr.type()) && !attr.normalized()) {
          glVertexAttribIPointer(attr.location(), attr.components(), glType, layout.stride, attr.offset());
        } else {
          glVertexAttribPointer(attr.location(), attr.components(), glType, attr.normalized(), layout.stride,
              attr.offset());
        }
      }
    }

    if (eboHandle != 0) {
      ctx.cache.bindBufferForce(GL_ELEMENT_ARRAY_BUFFER, eboHandle);
    }

    ctx.cache.bindVao(0);
    vaoCache.put(key, vao);
    return vao;
  }

  @Override
  public PipelineDesc desc() {
    return desc;
  }

  /**
   * Releases all cached VAOs.
   *
   * <p>Must be called on the render thread (via {@link OpenGLDevice#submit}).
   */
  @Override
  public void close() {
    ctx.submit(() -> {
      for (int vao : vaoCache.values()) {
        glDeleteVertexArrays(vao);
      }
      vaoCache.clear();
    });
  }

  /**
   * Key for the VAO cache combining vertex, instance, and index buffer handles.
   */
  private record VaoKey(int vbo, int instanceVbo, int ebo) {
  }
}
