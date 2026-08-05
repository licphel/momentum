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

/**
 * Shader pipeline stages and their corresponding bitmask constants for resource-set layout declarations.
 *
 * <p>Each enum constant has an associated {@code *_BIT} flag used when
 * declaring which shader stages access a resource.
 */
public enum ShaderType {
  VERTEX,
  FRAGMENT,
  GEOMETRY,
  COMPUTE;

  /**
   * Bitmask flag for the vertex stage.
   */
  public static final int VERTEX_BIT = 1;
  /**
   * Bitmask flag for the fragment stage.
   */
  public static final int FRAGMENT_BIT = 2;
  /**
   * Bitmask flag for the geometry stage.
   */
  public static final int GEOMETRY_BIT = 4;
  /**
   * Bitmask flag for the compute stage.
   */
  public static final int COMPUTE_BIT = 8;
}
