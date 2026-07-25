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

/**
 * Creates a new {@code RasterizationDesc} describing how primitives are converted to fragments.
 *
 * <p>Controls polygon rendering mode (fill, wireframe, points), face culling
 * (back, front, none, or both), front-face winding order, and depth-bias parameters for shadow-mapping and decal
 * rendering.
 *
 * <p>Two presets are provided:
 * <ul>
 *   <li>{@link #DEFAULT} — fill, back-face culled, counter-clockwise front faces
 *   <li>{@link #NOT_CULL} — fill, no culling
 * </ul>
 *
 * @param polygonMode             how to rasterize polygons ({@link PolygonMode#FILL}, {@link PolygonMode#LINE}, or
 *                                {@link PolygonMode#POINT})
 * @param cullMode                which faces to cull ({@link CullMode#NONE} disables culling)
 * @param frontFace               winding order of front-facing triangles
 * @param depthBiasEnable         {@code true} to apply a depth offset
 * @param depthBiasConstantFactor constant factor added to each fragment's depth
 * @param depthBiasClamp          maximum (or minimum) depth bias clamp (currently unused by the GL backend)
 * @param depthBiasSlopeFactor    scale factor applied to the fragment's depth slope
 */
public record RasterizationDesc(PolygonMode polygonMode, CullMode cullMode, FrontFace frontFace,
                                boolean depthBiasEnable, float depthBiasConstantFactor, float depthBiasClamp,
                                float depthBiasSlopeFactor) {
  /** Fill mode, back-face culled, counter-clockwise front faces, no depth bias. */
  public static final RasterizationDesc DEFAULT = new RasterizationDesc(PolygonMode.FILL, CullMode.BACK,
      FrontFace.COUNTER_CLOCKWISE, false, 0.0F, 0.0F, 0.0F);
  /** Fill mode, no face culling. */
  public static final RasterizationDesc NOT_CULL = new RasterizationDesc(PolygonMode.FILL, CullMode.NONE,
      FrontFace.COUNTER_CLOCKWISE, false, 0.0F, 0.0F, 0.0F);
}
