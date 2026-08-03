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

import net.fmhi.gfx.shader.VertexAttributeType;
import net.fmhi.gfx.shader.VertexLayout;
import net.fmhi.util.InternalApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Device-wide cache of configured OpenGL VAOs.
 *
 * <p>A VAO records buffer bindings and attribute pointers at setup time, so
 * one VAO is needed per (layout, VBO, instance-VBO, IBO) combination. A
 * chunked world holds thousands of retained sections, so the cache is shared
 * across all pipelines and sized for the live geometry. Entries are released
 * when one of their buffers is destroyed ({@link #invalidateBuffer(int)}),
 * keeping the count bounded without per-frame eviction; the capacity is only
 * a safety cap for leaked buffers.
 *
 * <p><b>Thread safety:</b> all methods must be called on the render thread.
 */
@InternalApi
public final class VaoRegistry {
  /** Safety cap; normal operation never reaches it because buffers unregister their VAOs on close. */
  private static final int MAX_VAOS = 16384;

  private final OpenGLDevice ctx;
  /** LRU cache keyed by the buffer triple; the layout is part of the key (reference identity). */
  private final Map<VaoKey, Integer> cache = new LinkedHashMap<>(1024, 0.75F, true);
  /** Reverse index: GL buffer handle to the VAO keys that reference it. */
  private final Map<Integer, List<VaoKey>> byBuffer = new HashMap<>();

  VaoRegistry(OpenGLDevice ctx) {
    this.ctx = ctx;
  }

  /**
   * Returns a configured VAO for the given buffer triple, creating one if necessary.
   *
   * @param layout            the vertex layout defining the attribute pointers
   * @param vboHandle         the GL vertex buffer handle (must be non-zero)
   * @param instanceVboHandle the GL instance-data buffer handle (0 if not instanced)
   * @param eboHandle         the GL index buffer handle (0 if not indexed)
   * @param instanceBase      the GL instance base; non-zero disables caching
   * @return a GL VAO handle configured for this (layout, VBO, instance-VBO, IBO) triple
   */
  int acquire(VertexLayout layout, int vboHandle, int instanceVboHandle, int eboHandle, int instanceBase) {
    boolean useCache = instanceBase == 0;
    VaoKey key = new VaoKey(layout, vboHandle, instanceVboHandle, eboHandle);
    if (useCache) {
      Integer cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
    }

    int vao = create(layout, vboHandle, instanceVboHandle, eboHandle, instanceBase);
    if (useCache) {
      cache.put(key, vao);
      byBuffer.computeIfAbsent(vboHandle, k -> new ArrayList<>()).add(key);
      if (instanceVboHandle != 0) byBuffer.computeIfAbsent(instanceVboHandle, k -> new ArrayList<>()).add(key);
      if (eboHandle != 0) byBuffer.computeIfAbsent(eboHandle, k -> new ArrayList<>()).add(key);
      if (cache.size() > MAX_VAOS) {
        // leaked buffers would grow the cache unbounded; drop everything once
        clear();
      }
    }
    return vao;
  }

  /**
   * Deletes every VAO referencing the given buffer (called when the buffer
   * is destroyed, so no VAO keeps pointing at a recycled GL handle).
   */
  void invalidateBuffer(int handle) {
    List<VaoKey> keys = byBuffer.remove(handle);
    if (keys == null) {
      return;
    }
    for (VaoKey key : keys) {
      Integer vao = cache.remove(key);
      if (vao != null) {
        glDeleteVertexArrays(vao);
      }
    }
  }

  /** Deletes all cached VAOs. */
  void clear() {
    for (int vao : cache.values()) {
      glDeleteVertexArrays(vao);
    }
    cache.clear();
    byBuffer.clear();
  }

  /** Creates a VAO bound to the given buffers, using the layout's attribute pointers. */
  private int create(VertexLayout layout, int vboHandle, int instanceVboHandle, int eboHandle, int instanceBase) {
    int vao = glGenVertexArrays();
    ctx.cache.bindVao(vao);

    // Set up per-instance attributes (from instance VBO, divisor > 0)
    int instanceByteOffset = instanceBase * layout.instanceStride;
    if (instanceVboHandle != 0 && layout.instanceStride > 0) {
      ctx.cache.bindBufferForce(GL_ARRAY_BUFFER, instanceVboHandle);
      for (VertexLayout.Attr attr : layout.attrs) {
        if (attr.divisor() > 0) {
          glEnableVertexAttribArray(attr.location());
          int glType = OpenGLUtils.vertexAttribType(attr.type());
          if (isIntType(attr.type()) && !attr.normalized()) {
            glVertexAttribIPointer(attr.location(), attr.components(), glType, layout.instanceStride,
                attr.offset() + instanceByteOffset);
          } else {
            glVertexAttribPointer(attr.location(), attr.components(), glType, attr.normalized(), layout.instanceStride,
                attr.offset() + instanceByteOffset);
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
    return vao;
  }

  private static boolean isIntType(VertexAttributeType type) {
    return switch (type) {
      case INT8, INT16, INT32, UINT8, UINT16, UINT32 -> true;
      default -> false;
    };
  }

  /** Key combining the layout and the buffer triple. */
  private record VaoKey(VertexLayout layout, int vbo, int instanceVbo, int ebo) {
  }
}
