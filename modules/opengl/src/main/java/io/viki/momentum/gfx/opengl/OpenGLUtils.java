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

package io.viki.momentum.gfx.opengl;

import io.viki.momentum.gfx.buffer.BufferFrequency;
import io.viki.momentum.gfx.buffer.BufferType;
import io.viki.momentum.gfx.pipe.*;
import io.viki.momentum.gfx.shader.ShaderType;
import io.viki.momentum.gfx.shader.VertexAttributeType;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.gfx.texture.TextureFormat;
import io.viki.momentum.gfx.texture.TextureType;
import io.viki.momentum.gfx.texture.TextureWrap;
import io.viki.momentum.util.InternalApi;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

/**
 * Stateless converter from engine enums to OpenGL integer constants.
 *
 * <p>Every method maps a single {@code io.viki.momentum.graphics} enum value to
 * the corresponding {@code GL_*} integer. The class is a pure function collection — it holds no state and every method
 * is deterministic.
 *
 * <p><b>Usage note:</b> these methods are called on the calling thread
 * during command recording (not deferred to the render thread), so they must not make any GL calls themselves.
 */
@InternalApi
public final class OpenGLUtils {
  private OpenGLUtils() {
  }

  /**
   * Maps a {@link BufferType} to its GL binding target.
   *
   * @param type the engine buffer type
   * @return {@code GL_ARRAY_BUFFER}, {@code GL_ELEMENT_ARRAY_BUFFER}, or {@code GL_UNIFORM_BUFFER}
   */
  public static int bufferTarget(BufferType type) {
    return switch (type) {
      case VERTEX -> GL_ARRAY_BUFFER;
      case INDEX -> GL_ELEMENT_ARRAY_BUFFER;
      case UNIFORM -> GL_UNIFORM_BUFFER;
    };
  }

  /**
   * Maps a {@link BufferFrequency} to a GL usage hint.
   *
   * <p>When {@code gpuWrite} is {@code true}, uses the {@code _COPY} variants
   * (e.g. {@code GL_DYNAMIC_COPY}) indicating the GPU may also write to the buffer.
   *
   * @param freq     the expected update frequency
   * @param gpuWrite {@code true} if the GPU will write to this buffer
   * @return a {@code GL_STATIC_DRAW}, {@code GL_DYNAMIC_DRAW}, etc. constant
   */
  public static int bufferUsage(BufferFrequency freq, boolean gpuWrite) {
    return switch (freq) {
      case STATIC -> gpuWrite ? GL_STATIC_COPY : GL_STATIC_DRAW;
      case DYNAMIC -> gpuWrite ? GL_DYNAMIC_COPY : GL_DYNAMIC_DRAW;
      case STREAM -> gpuWrite ? GL_STREAM_COPY : GL_STREAM_DRAW;
    };
  }

  /**
   * Maps a {@link VertexAttributeType} to its GL data type.
   *
   * @param type the vertex attribute component type
   * @return the corresponding {@code GL_UNSIGNED_BYTE}, {@code GL_FLOAT}, etc.
   */
  public static int vertexAttribType(VertexAttributeType type) {
    return switch (type) {
      case UINT8 -> GL_UNSIGNED_BYTE;
      case UINT16 -> GL_UNSIGNED_SHORT;
      case UINT32 -> GL_UNSIGNED_INT;
      case INT8 -> GL_BYTE;
      case INT16 -> GL_SHORT;
      case INT32 -> GL_INT;
      case FLOAT16 -> GL_HALF_FLOAT;
      case FLOAT32 -> GL_FLOAT;
    };
  }

  /**
   * Maps a {@link BlendFactor} to its GL constant.
   *
   * @param f the blend factor
   * @return the corresponding GL blend-factor constant
   */
  public static int blendFactor(BlendFactor f) {
    return switch (f) {
      case ZERO -> GL_ZERO;
      case ONE -> GL_ONE;
      case SRC_COLOR -> GL_SRC_COLOR;
      case ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR;
      case DST_COLOR -> GL_DST_COLOR;
      case ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR;
      case SRC_ALPHA -> GL_SRC_ALPHA;
      case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
      case DST_ALPHA -> GL_DST_ALPHA;
      case ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA;
      case CONSTANT_COLOR -> GL_CONSTANT_COLOR;
      case ONE_MINUS_CONSTANT_COLOR -> GL_ONE_MINUS_CONSTANT_COLOR;
      case CONSTANT_ALPHA -> GL_CONSTANT_ALPHA;
      case ONE_MINUS_CONSTANT_ALPHA -> GL_ONE_MINUS_CONSTANT_ALPHA;
      case SRC_ALPHA_SATURATE -> GL_SRC_ALPHA_SATURATE;
    };
  }

  /**
   * Maps a {@link BlendFunc} to its GL blend equation constant.
   *
   * @param f the blend function
   * @return the corresponding {@code GL_FUNC_ADD}, {@code GL_MIN}, etc.
   */
  public static int blendFunc(BlendFunc f) {
    return switch (f) {
      case ADD -> GL_FUNC_ADD;
      case SUBTRACT -> GL_FUNC_SUBTRACT;
      case REVERSE_SUBTRACT -> GL_FUNC_REVERSE_SUBTRACT;
      case MIN -> GL_MIN;
      case MAX -> GL_MAX;
    };
  }

  /**
   * Maps a {@link CompareOp} to its GL depth/stencil comparison constant.
   *
   * @param op the comparison operator
   * @return the corresponding {@code GL_LESS}, {@code GL_EQUAL}, etc.
   */
  public static int compareOp(CompareOp op) {
    return switch (op) {
      case NEVER -> GL_NEVER;
      case LESS -> GL_LESS;
      case EQUAL -> GL_EQUAL;
      case LESS_OR_EQUAL -> GL_LEQUAL;
      case GREATER -> GL_GREATER;
      case NOT_EQUAL -> GL_NOTEQUAL;
      case GREATER_OR_EQUAL -> GL_GEQUAL;
      case ALWAYS -> GL_ALWAYS;
    };
  }

  /**
   * Maps a {@link StencilFunc} to its GL stencil operation constant.
   *
   * @param f the stencil operation
   * @return the corresponding {@code GL_KEEP}, {@code GL_REPLACE}, etc.
   */
  public static int stencilFunc(StencilFunc f) {
    return switch (f) {
      case KEEP -> GL_KEEP;
      case ZERO -> GL_ZERO;
      case REPLACE -> GL_REPLACE;
      case INVERT -> GL_INVERT;
      case INCR -> GL_INCR;
      case DECR -> GL_DECR;
      case INCR_WRAP -> GL_INCR_WRAP;
      case DECR_WRAP -> GL_DECR_WRAP;
    };
  }

  /**
   * Maps a {@link Topology} to its GL primitive type.
   *
   * @param t the primitive topology
   * @return {@code GL_TRIANGLES}, {@code GL_LINES}, {@code GL_POINTS}, etc.
   */
  public static int topology(Topology t) {
    return switch (t) {
      case TRIANGLE -> GL_TRIANGLES;
      case TRIANGLE_FAN -> GL_TRIANGLE_FAN;
      case TRIANGLE_STRIP -> GL_TRIANGLE_STRIP;
      case LINE -> GL_LINES;
      case LINE_STRIP -> GL_LINE_STRIP;
      case LINE_LOOP -> GL_LINE_LOOP;
      case POINT -> GL_POINTS;
    };
  }

  /**
   * Maps a {@link PolygonMode} to its GL rasterization mode.
   *
   * @param m the polygon mode
   * @return {@code GL_FILL}, {@code GL_LINE}, or {@code GL_POINT}
   */
  public static int polygonMode(PolygonMode m) {
    return switch (m) {
      case FILL -> GL_FILL;
      case LINE -> GL_LINE;
      case POINT -> GL_POINT;
    };
  }

  /**
   * Maps a {@link CullMode} to its GL cull-face constant.
   *
   * <p>Returns -1 for {@link CullMode#NONE} to signal that culling should
   * be disabled entirely.
   *
   * @param m the cull mode
   * @return {@code GL_FRONT}, {@code GL_BACK}, {@code GL_FRONT_AND_BACK}, or -1
   */
  public static int cullMode(CullMode m) {
    return switch (m) {
      case NONE -> -1;
      case FRONT -> GL_FRONT;
      case BACK -> GL_BACK;
      case FRONT_AND_BACK -> GL_FRONT_AND_BACK;
    };
  }

  /**
   * Maps a {@link FrontFace} to its GL winding-order constant.
   *
   * @param f the front-face winding order
   * @return {@code GL_CW} or {@code GL_CCW}
   */
  public static int frontFace(FrontFace f) {
    return switch (f) {
      case CLOCKWISE -> GL_CW;
      case COUNTER_CLOCKWISE -> GL_CCW;
    };
  }

  /**
   * Maps a {@link TextureType} to its GL texture target.
   *
   * @param t the texture dimensionality
   * @return {@code GL_TEXTURE_1D}, {@code GL_TEXTURE_2D}, or {@code GL_TEXTURE_3D}
   */
  public static int textureTarget(TextureType t) {
    return switch (t) {
      case TEXTURE_1D -> GL_TEXTURE_1D;
      case TEXTURE_2D -> GL_TEXTURE_2D;
      case TEXTURE_3D -> GL_TEXTURE_3D;
    };
  }

  /**
   * Maps a {@link TextureWrap} to its GL wrapping mode.
   *
   * @param w the texture wrap mode
   * @return {@code GL_REPEAT}, {@code GL_CLAMP_TO_EDGE}, etc.
   */
  public static int textureWrap(TextureWrap w) {
    return switch (w) {
      case CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
      case CLAMP_TO_BORDER -> GL_CLAMP_TO_BORDER;
      case REPEAT -> GL_REPEAT;
      case MIRRORED_REPEAT -> GL_MIRRORED_REPEAT;
    };
  }

  /**
   * Maps a {@link TextureFilter} to its GL filter constant.
   *
   * @param f the texture filter mode
   * @return {@code GL_NEAREST}, {@code GL_LINEAR}, or a mipmapped variant
   */
  public static int textureFilter(TextureFilter f) {
    return switch (f) {
      case NEAREST -> GL_NEAREST;
      case LINEAR -> GL_LINEAR;
      case NEAREST_MIPMAP_NEAREST -> GL_NEAREST_MIPMAP_NEAREST;
      case LINEAR_MIPMAP_NEAREST -> GL_LINEAR_MIPMAP_NEAREST;
      case LINEAR_MIPMAP_LINEAR -> GL_LINEAR_MIPMAP_LINEAR;
      case NEAREST_MIPMAP_LINEAR -> GL_NEAREST_MIPMAP_LINEAR;
    };
  }

  /**
   * Maps a {@link TextureFormat} to a triple of GL format constants.
   *
   * @param f the texture format
   * @return a 3-element array: {@code [internalFormat, pixelFormat, pixelType]}
   */
  public static int[] textureFormat(TextureFormat f) {
    return switch (f) {
      case RED8 -> new int[] {GL_R8, GL_RED, GL_UNSIGNED_BYTE};
      case RG8 -> new int[] {GL_RG8, GL_RG, GL_UNSIGNED_BYTE};
      case RGB8 -> new int[] {GL_RGB8, GL_RGB, GL_UNSIGNED_BYTE};
      case RGBA8 -> new int[] {GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE};
      case RED16F -> new int[] {GL_R16F, GL_RED, GL_HALF_FLOAT};
      case RG16F -> new int[] {GL_RG16F, GL_RG, GL_HALF_FLOAT};
      case RGB16F -> new int[] {GL_RGB16F, GL_RGB, GL_HALF_FLOAT};
      case RGBA16F -> new int[] {GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT};
      case RED32F -> new int[] {GL_R32F, GL_RED, GL_FLOAT};
      case RG32F -> new int[] {GL_RG32F, GL_RG, GL_FLOAT};
      case RGB32F -> new int[] {GL_RGB32F, GL_RGB, GL_FLOAT};
      case RGBA32F -> new int[] {GL_RGBA32F, GL_RGBA, GL_FLOAT};
      case DEPTH16 -> new int[] {GL_DEPTH_COMPONENT16, GL_DEPTH_COMPONENT, GL_UNSIGNED_SHORT};
      case DEPTH24 -> new int[] {GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT};
      case DEPTH32F -> new int[] {GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT};
      case DEPTH24_STENCIL8 -> new int[] {GL_DEPTH24_STENCIL8, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8};
    };
  }

  /**
   * Maps a {@link ShaderType} to its GL shader stage constant.
   *
   * @param t the shader stage
   * @return {@code GL_VERTEX_SHADER}, {@code GL_FRAGMENT_SHADER}, {@code GL_GEOMETRY_SHADER}, or
   * {@code GL_COMPUTE_SHADER}
   */
  public static int shaderType(ShaderType t) {
    return switch (t) {
      case VERTEX -> GL_VERTEX_SHADER;
      case FRAGMENT -> GL_FRAGMENT_SHADER;
      case GEOMETRY -> GL_GEOMETRY_SHADER;
      case COMPUTE -> GL_COMPUTE_SHADER;
    };
  }
}
