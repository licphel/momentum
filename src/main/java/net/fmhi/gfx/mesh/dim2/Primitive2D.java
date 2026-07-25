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

package net.fmhi.gfx.mesh.dim2;

import net.fmhi.gfx.pipe.Topology;

/**
 * Primitive type that selects the pipeline, resource set layout, and topology for rendering.
 *
 * <p>Each value maps to a specific rendering fmt: textured or colored triangles, lines, or
 * points. The primitive type determines which built-in shader and topology are used when no
 * custom pipeline is set.
 *
 * @see VertexBuilder2D
 */
public enum Primitive2D {
  TEXTURE_TRIANGLE_INDEXED(true, Topology.TRIANGLE, true, 28),
  COLOR_TRIANGLE_INDEXED(true, Topology.TRIANGLE, false, 20),
  COLOR_TRIANGLE(false, Topology.TRIANGLE, false, 20),
  COLOR_LINE(false, Topology.LINE, false, 20),
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

  public boolean isTextured() {
    return textured;
  }

  public boolean isIndexed() {
    return indexed;
  }

  public Topology topology() {
    return topology;
  }

  public int vertexSize() {
    return vertexSize;
  }
}
