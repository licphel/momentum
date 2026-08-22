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
import net.fmhi.util.internal.InternalApi;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33.*;

/**
 * Device-wide cache of configured OpenGL VAOs, implemented as an open-addressing
 * hash table with zero allocation on the hot path.
 *
 * <p>A VAO records buffer bindings and attribute pointers at setup time, so one VAO
 * is needed per (layout, VBO, instance-VBO, IBO, instance-base) combination. A chunked
 * world draws thousands of retained sections per frame, so the lookup must be fast and
 * allocation-free: the key is packed into two longs ({@code vbo<<32|ebo} and
 * {@code instVbo<<32|instanceBase}) plus a small layout id (layouts are interned by
 * reference into a tiny table — a world has a handful of layouts).
 *
 * <p>Deletions leave tombstones (reused by later inserts); a rehash when the table
 * crosses half capacity sweeps them. Entries referencing a destroyed buffer are
 * removed by a full-table scan in {@link #invalidateBuffer(int)} — buffer destruction
 * is rare (chunk unload), so the O(n) scan is cheaper than maintaining a reverse index.
 *
 * <p><b>Thread safety:</b> all methods must be called on the render thread.
 */
@InternalApi
public final class VaoRegistry {
  /** Initial table capacity; grown by doubling. */
  private static final int MIN_CAPACITY = 1 << 10;
  /** Safety cap for leaked buffers; reaching it clears the whole table. */
  private static final int MAX_CAPACITY = 1 << 17;
  /**
   * Tombstone marker: the slot held a VAO that was deleted (k0/k1 keep their
   * stale values but the slot is skipped by lookups and reusable by inserts).
   */
  private static final int TOMBSTONE = -1;

  private final OpenGLDevice ctx;
  /** Open-addressing table: keys and values in parallel arrays. */
  private long[] k0 = new long[MIN_CAPACITY];    // (vbo << 32) | ebo
  private long[] k1 = new long[MIN_CAPACITY];    // (instVbo << 32) | instanceBase
  private int[] layoutIds = new int[MIN_CAPACITY];
  private int[] vaos = new int[MIN_CAPACITY];
  private int size;
  private int tombstones;
  private int mask = MIN_CAPACITY - 1;
  /** Interned layouts; ids are 1-based (0 marks an empty slot). */
  private VertexLayout[] layouts = new VertexLayout[8];
  private int layoutCount = 1;

  VaoRegistry(OpenGLDevice ctx) {
    this.ctx = ctx;
  }

  private static boolean isIntType(VertexAttributeType type) {
    return switch (type) {
      case INT8, INT16, INT32, UINT8, UINT16, UINT32 -> true;
      default -> false;
    };
  }

  /** SplitMix-style mix of the three key parts. */
  private static int hash(long key0, long key1, int layoutId) {
    long h = key0 * 0x9E3779B97F4A7C15L;
    h ^= key1 * 0xBF58476D1CE4E5B9L;
    h ^= (long) layoutId * 0x94D049BB133111EBL;
    h ^= h >>> 29;
    h *= 0x9E3779B97F4A7C15L;
    h ^= h >>> 32;
    return (int) h;
  }

  /**
   * Returns a configured VAO for the given buffer quadruple, creating and caching one
   * if absent. The instance base is part of the key (its byte offset is baked into
   * the attribute pointers), so every distinct combination is cached.
   *
   * @param layout            the vertex layout defining the attribute pointers
   * @param vboHandle         the GL vertex buffer handle (must be non-zero)
   * @param instanceVboHandle the GL instance-data buffer handle (0 if not instanced)
   * @param eboHandle         the GL index buffer handle (0 if not indexed)
   * @param instanceBase      the GL instance base
   * @return a GL VAO handle configured for this combination
   */
  int acquire(VertexLayout layout, int vboHandle, int instanceVboHandle, int eboHandle, int instanceBase) {
    int lid = layoutId(layout);
    long key0 = ((long) vboHandle << 32) | (eboHandle & 0xFFFFFFFFL);
    long key1 = ((long) instanceVboHandle << 32) | (instanceBase & 0xFFFFFFFFL);

    int i = hash(key0, key1, lid) & mask;
    int firstGap = -1;
    while (true) {
      int vao = vaos[i];
      if (vao == TOMBSTONE) {
        if (firstGap < 0) {
          firstGap = i;
        }
      } else if (vao == 0 && k0[i] == 0) {
        break; // empty slot: nothing further can match this key
      } else if (k0[i] == key0 && k1[i] == key1 && layoutIds[i] == lid) {
        return vao; // cache hit
      }
      i = (i + 1) & mask;
    }

    int vao = create(layout, vboHandle, instanceVboHandle, eboHandle, instanceBase);
    if ((size + tombstones + 1) << 1 > vaos.length) {
      rehash();
      i = hash(key0, key1, lid) & mask;
      while (vaos[i] != 0 || k0[i] != 0) {
        i = (i + 1) & mask;
      }
    } else if (firstGap >= 0) {
      i = firstGap;
      tombstones--;
    }
    k0[i] = key0;
    k1[i] = key1;
    layoutIds[i] = lid;
    vaos[i] = vao;
    size++;
    return vao;
  }

  /**
   * Deletes every VAO referencing the given buffer (called when the buffer is
   * destroyed, so no VAO keeps pointing at a recycled GL handle). Full-table scan:
   * buffer destruction is rare, so this beats maintaining a reverse index.
   */
  void invalidateBuffer(int handle) {
    for (int i = 0; i < vaos.length; i++) {
      if (vaos[i] > 0
          && ((int) (k0[i] >>> 32) == handle || (int) k0[i] == handle
          || (int) (k1[i] >>> 32) == handle)) {
        glDeleteVertexArrays(vaos[i]);
        vaos[i] = TOMBSTONE;
        tombstones++;
        size--;
      }
    }
  }

  /** Deletes all cached VAOs. */
  void clear() {
    for (int vao : vaos) {
      if (vao > 0) {
        glDeleteVertexArrays(vao);
      }
    }
    Arrays.fill(k0, 0);
    Arrays.fill(k1, 0);
    Arrays.fill(layoutIds, 0);
    Arrays.fill(vaos, 0);
    size = 0;
    tombstones = 0;
  }

  /**
   * Rebuilds the table at double capacity, re-inserting live entries and
   * sweeping the tombstones.
   */
  private void rehash() {
    int newCap = Math.min(vaos.length << 1, MAX_CAPACITY);
    if (newCap == vaos.length) {
      clear(); // leaked buffers grew the table to the cap; drop everything
      return;
    }
    long[] nk0 = new long[newCap];
    long[] nk1 = new long[newCap];
    int[] nLid = new int[newCap];
    int[] nVaos = new int[newCap];
    int nMask = newCap - 1;
    int nSize = 0;
    for (int i = 0; i < vaos.length; i++) {
      if (vaos[i] > 0) {
        int j = hash(k0[i], k1[i], layoutIds[i]) & nMask;
        while (nVaos[j] != 0 || nk0[j] != 0) {
          j = (j + 1) & nMask;
        }
        nk0[j] = k0[i];
        nk1[j] = k1[i];
        nLid[j] = layoutIds[i];
        nVaos[j] = vaos[i];
        nSize++;
      }
    }
    k0 = nk0;
    k1 = nk1;
    layoutIds = nLid;
    vaos = nVaos;
    mask = nMask;
    size = nSize;
    tombstones = 0;
  }

  /** Returns the interned id of the layout, registering it on first use. */
  private int layoutId(VertexLayout layout) {
    for (int i = 1; i < layoutCount; i++) {
      if (layouts[i] == layout) {
        return i;
      }
    }
    if (layoutCount == layouts.length) {
      layouts = Arrays.copyOf(layouts, layouts.length * 2);
    }
    layouts[layoutCount] = layout;
    return layoutCount++;
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
            glVertexAttribPointer(attr.location(), attr.components(), glType, attr.normalized(),
                layout.instanceStride, attr.offset() + instanceByteOffset);
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
}
