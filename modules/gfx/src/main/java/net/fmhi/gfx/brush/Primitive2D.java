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

package net.fmhi.gfx.brush;

import net.fmhi.gfx.pipe.Topology;

/**
 * Primitive type that selects how vertices are assembled and rendered.
 *
 * <p>Each constant maps to a distinct draw mode: textured or untextured triangles, with
 * or without an index buffer, plus lines and points. The mode determines the pipeline,
 * resource set layout, and topology used to draw.
 *
 * @see VertexBuilder2D
 */
public enum Primitive2D {
  /** Textured triangles drawn through an index buffer. */
  TEXTURE_TRIANGLE_INDEXED(true, Topology.TRIANGLE, true, 28),
  /** Untextured triangles drawn through an index buffer. */
  COLOR_TRIANGLE_INDEXED(true, Topology.TRIANGLE, false, 20),
  /** Untextured triangles drawn without an index buffer. */
  COLOR_TRIANGLE(false, Topology.TRIANGLE, false, 20),
  /** Straight lines with per-vertex colors. */
  COLOR_LINE(false, Topology.LINE, false, 20),
  /** Points with per-vertex colors. */
  COLOR_POINT(false, Topology.POINT, false, 20);

  final boolean indexed;
  final Topology topology;
  final boolean textured;
  final int vertexSize;

  Primitive2D(boolean indexed, Topology topology, boolean textured, int vertexSize) {
    this.indexed = indexed;
    this.topology = topology;
    this.textured = textured;
    this.vertexSize = vertexSize;
  }

  /**
   * Returns whether the primitive samples textures.
   *
   * @return {@code true} if the primitive is textured
   */
  public boolean isTextured() {
    return textured;
  }

  /**
   * Returns whether the primitive draws through an index buffer.
   *
   * @return {@code true} if the primitive is indexed
   */
  public boolean isIndexed() {
    return indexed;
  }

  /**
   * Returns the topology the primitive is drawn with.
   *
   * @return the {@link Topology} used for this primitive
   */
  public Topology topology() {
    return topology;
  }

  /**
   * Returns the byte size of a single vertex.
   *
   * @return the vertex size in bytes
   */
  public int vertexSize() {
    return vertexSize;
  }
}
