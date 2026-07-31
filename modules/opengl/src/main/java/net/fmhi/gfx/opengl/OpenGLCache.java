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

import net.fmhi.util.InternalApi;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL state cache — avoids redundant API calls by tracking the currently bound state.
 *
 * <p>All setters compare the incoming value against the cached value and call the
 * underlying GL function only when the state actually changes.
 *
 * <p>This class is <b>not thread-safe</b> and must only be accessed from the render thread.
 */
@InternalApi
public final class OpenGLCache {
  final int[] buffers = new int[8];
  final int[] textures = new int[32];
  final int[] samplers = new int[32];
  final int[] uniformBuffers = new int[32];
  // Scissor / viewport
  final int[] scissorRect = new int[4];
  final int[] viewportRect = new int[4];
  int texActive = 0;
  int program = 0;
  int vao = 0;
  boolean blend = false;
  boolean depthTest = false;
  boolean stencilTest = false;
  boolean scissorTest = false;
  boolean cullFace = false;
  boolean polyOffsetFill = false;

  // Blend
  int blendSrcRgb = GL_ONE;
  int blendDstRgb = GL_ZERO;
  int blendEqRgb = GL_FUNC_ADD;
  int blendSrcA = GL_ONE;
  int blendDstA = GL_ZERO;
  int blendEqA = GL_FUNC_ADD;
  float blendConstR;
  float blendConstG;
  float blendConstB;
  float blendConstA;

  // Depth
  boolean depthWrite = true;
  int depthFunc = GL_LESS;
  int fboR = 0;
  int fboW = 0;
  // Stencil (front)
  int stencilFFunc = GL_ALWAYS;
  int stencilFRef;
  int stencilFMask = 0xFF;
  int stencilFFail = GL_KEEP;
  int stencilFZFail = GL_KEEP;
  int stencilFZPass = GL_KEEP;
  int stencilFWriteMask = 0xFF;
  // Stencil (back)
  int stencilBFunc = GL_ALWAYS;
  int stencilBRef;
  int stencilBMask = 0xFF;
  int stencilBFail = GL_KEEP;
  int stencilBZFail = GL_KEEP;
  int stencilBZPass = GL_KEEP;

  // Rasterization
  int polyMode = GL_FILL;
  int cullMode = GL_BACK;
  int frontFace = GL_CCW;
  float depthBiasConstant = 0f;
  float depthBiasSlope = 0f;
  int stencilBWriteMask = 0xFF;
  // Color write mask
  boolean colorMaskR = true;
  boolean colorMaskG = true;
  boolean colorMaskB = true;
  boolean colorMaskA = true;

  OpenGLCache() {
    Arrays.fill(textures, 0);
    Arrays.fill(samplers, 0);
    Arrays.fill(uniformBuffers, 0);
  }

  private static int bufferIndex(int target) {
    return switch (target) {
      case GL_ARRAY_BUFFER -> 0;
      case GL_ELEMENT_ARRAY_BUFFER -> 1;
      case GL_UNIFORM_BUFFER -> 2;
      default -> 3;
    };
  }

  /**
   * Binds a shader program.
   *
   * @param id the GL program handle
   */
  public void useProgram(int id) {
    if (program != id) {
      glUseProgram(id);
      program = id;
    }
  }

  /**
   * Binds a vertex array object.
   *
   * @param id the GL VAO handle
   */
  public void bindVao(int id) {
    if (vao != id) {
      glBindVertexArray(id);
      vao = id;
    }
  }

  /**
   * Binds a buffer object to the given target.
   *
   * @param target the GL buffer target (e.g. {@code GL_ARRAY_BUFFER})
   * @param id     the GL buffer handle
   */
  public void bindBuffer(int target, int id) {
    int idx = bufferIndex(target);
    if (buffers[idx] != id) {
      glBindBuffer(target, id);
      buffers[idx] = id;
    }
  }

  /**
   * Binds a buffer object to the given target, not using cache.
   *
   * @param target the GL buffer target (e.g. {@code GL_ARRAY_BUFFER})
   * @param id     the GL buffer handle
   */
  public void bindBufferForce(int target, int id) {
    int idx = bufferIndex(target);
    glBindBuffer(target, id);
    buffers[idx] = id;
  }

  /**
   * Binds a framebuffer object to the given target.
   *
   * @param target the GL framebuffer target (e.g. {@code GL_FRAMEBUFFER})
   * @param id     the GL framebuffer handle
   */
  public void bindFramebuffer(int target, int id) {
    if (target == GL_FRAMEBUFFER) {
      if (fboR != id || fboW != id) {
        fboR = fboW = id;
        glBindFramebuffer(target, id);
      }
    } else if (target == GL_READ_FRAMEBUFFER) {
      if (fboR != id) {
        fboR = id;
        glBindFramebuffer(target, id);
      }
    } else if (target == GL_DRAW_FRAMEBUFFER) {
      if (fboW != id) {
        fboW = id;
        glBindFramebuffer(target, id);
      }
    }
  }

  /**
   * Binds a texture to the specified texture unit.
   *
   * @param unit   the texture unit index (0-based)
   * @param target the GL texture target (e.g. {@code GL_TEXTURE_2D})
   * @param id     the GL texture handle
   */
  public void setTexture(int unit, int target, int id) {
    if (texActive != unit) {
      glActiveTexture(GL_TEXTURE0 + unit);
      texActive = unit;
    }

    if (textures[unit] != id) {
      glBindTexture(target, id);
      textures[unit] = id;
    }
  }

  /**
   * Binds a sampler object to the specified texture unit.
   *
   * @param unit the texture unit index (0-based)
   * @param id   the GL sampler handle
   */
  public void setSampler(int unit, int id) {
    if (samplers[unit] != id) {
      glBindSampler(unit, id);
      samplers[unit] = id;
    }
  }

  /**
   * Binds a uniform buffer range to the specified binding point.
   *
   * @param point  the UBO binding point index
   * @param id     the GL buffer handle
   * @param offset the byte offset into the buffer
   * @param size   the byte size of the bound range
   */
  public void setUniformBuffer(int point, int id, int offset, int size) {
    if (uniformBuffers[point] != id) {
      glBindBufferRange(GL_UNIFORM_BUFFER, point, id, offset, size);
      uniformBuffers[point] = id;
    }
  }

  /**
   * Enables or disables blending.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setBlendEnabled(boolean on) {
    if (blend != on) {
      if (on) {
        glEnable(GL_BLEND);
      } else {
        glDisable(GL_BLEND);
      }
      blend = on;
    }
  }

  /**
   * Enables or disables depth testing.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setDepthTestEnabled(boolean on) {
    if (depthTest != on) {
      if (on) {
        glEnable(GL_DEPTH_TEST);
      } else {
        glDisable(GL_DEPTH_TEST);
      }
      depthTest = on;
    }
  }

  /**
   * Enables or disables stencil testing.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setStencilTestEnabled(boolean on) {
    if (stencilTest != on) {
      if (on) {
        glEnable(GL_STENCIL_TEST);
      } else {
        glDisable(GL_STENCIL_TEST);
      }
      stencilTest = on;
    }
  }

  /**
   * Enables or disables scissor testing.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setScissorEnabled(boolean on) {
    if (scissorTest != on) {
      if (on) {
        glEnable(GL_SCISSOR_TEST);
      } else {
        glDisable(GL_SCISSOR_TEST);
      }
      scissorTest = on;
    }
  }

  /**
   * Enables or disables face culling.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setCullFaceEnabled(boolean on) {
    if (cullFace != on) {
      if (on) {
        glEnable(GL_CULL_FACE);
      } else {
        glDisable(GL_CULL_FACE);
      }
      cullFace = on;
    }
  }

  /**
   * Enables or disables polygon offset fill mode.
   *
   * @param on {@code true} to enable, {@code false} to disable
   */
  public void setPolyOffsetEnabled(boolean on) {
    if (polyOffsetFill != on) {
      if (on) {
        glEnable(GL_POLYGON_OFFSET_FILL);
      } else {
        glDisable(GL_POLYGON_OFFSET_FILL);
      }
      polyOffsetFill = on;
    }
  }

  /**
   * Sets the blend function for RGB and alpha separately.
   *
   * @param srcRgb the GL source factor for RGB
   * @param dstRgb the GL destination factor for RGB
   * @param srcA   the GL source factor for alpha
   * @param dstA   the GL destination factor for alpha
   */
  public void setBlendFunc(int srcRgb, int dstRgb, int srcA, int dstA) {
    if (blendSrcRgb != srcRgb || blendDstRgb != dstRgb || blendSrcA != srcA || blendDstA != dstA) {
      glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
      blendSrcRgb = srcRgb;
      blendDstRgb = dstRgb;
      blendSrcA = srcA;
      blendDstA = dstA;
    }
  }

  /**
   * Sets the blend equation for RGB and alpha separately.
   *
   * @param eqRgb the GL blend equation for RGB
   * @param eqA   the GL blend equation for alpha
   */
  public void setBlendEquation(int eqRgb, int eqA) {
    if (blendEqRgb != eqRgb || blendEqA != eqA) {
      glBlendEquationSeparate(eqRgb, eqA);
      blendEqRgb = eqRgb;
      blendEqA = eqA;
    }
  }

  /**
   * Sets the constant blend color.
   *
   * @param r the red component
   * @param g the green component
   * @param b the blue component
   * @param a the alpha component
   */
  public void setBlendColor(float r, float g, float b, float a) {
    if (blendConstR != r || blendConstG != g || blendConstB != b || blendConstA != a) {
      glBlendColor(r, g, b, a);
      blendConstR = r;
      blendConstG = g;
      blendConstB = b;
      blendConstA = a;
    }
  }

  /**
   * Enables or disables writes to the depth buffer.
   *
   * @param on {@code true} to allow depth writes, {@code false} to make the depth buffer read-only
   */
  public void setDepthWrite(boolean on) {
    if (depthWrite != on) {
      glDepthMask(on);
      depthWrite = on;
    }
  }

  /**
   * Sets the depth comparison function.
   *
   * @param func the GL depth comparison function (e.g. {@code GL_LESS})
   */
  public void setDepthFunc(int func) {
    if (depthFunc != func) {
      glDepthFunc(func);
      depthFunc = func;
    }
  }

  /**
   * Sets the polygon rasterization mode (fill, line, or point).
   *
   * @param mode the GL polygon mode (e.g. {@code GL_FILL})
   */
  public void setPolygonMode(int mode) {
    if (polyMode != mode) {
      glPolygonMode(GL_FRONT_AND_BACK, mode);
      polyMode = mode;
    }
  }

  /**
   * Sets the face culling mode.
   *
   * <p>Pass a negative value to disable culling entirely.
   *
   * @param mode the GL cull mode (e.g. {@code GL_BACK}), or a negative value to disable
   */
  public void setCullMode(int mode) {
    if (mode < 0) {
      setCullFaceEnabled(false);
    } else {
      setCullFaceEnabled(true);
      if (cullMode != mode) {
        glCullFace(mode);
        cullMode = mode;
      }
    }
  }

  /**
   * Sets the front-face winding order.
   *
   * @param ff the GL front-face direction ({@code GL_CW} or {@code GL_CCW})
   */
  public void setFrontFace(int ff) {
    if (frontFace != ff) {
      glFrontFace(ff);
      frontFace = ff;
    }
  }

  /**
   * Sets depth bias (polygon offset) parameters.
   *
   * @param enable   {@code true} to enable depth bias, {@code false} to disable
   * @param constant the constant depth bias factor
   * @param slope    the slope-dependent depth bias factor
   */
  public void setDepthBias(boolean enable, float constant, float slope) {
    setPolyOffsetEnabled(enable);
    if (enable) {
      if (depthBiasConstant != constant || depthBiasSlope != slope) {
        glPolygonOffset(slope, constant);
        depthBiasConstant = constant;
        depthBiasSlope = slope;
      }
    }
  }

  /**
   * Sets the viewport rectangle. The caller is responsible for computing the correct {@code glY} (i.e.
   * {@code fbHeight - y - h}) before calling this method.
   *
   * @param x   the left edge in pixels
   * @param glY the bottom edge in OpenGL coordinates (already Y-flipped)
   * @param w   the viewport width
   * @param h   the viewport height
   */
  public void setViewport(int x, int glY, int w, int h) {
    if (viewportRect[0] != x || viewportRect[1] != glY || viewportRect[2] != w || viewportRect[3] != h) {
      glViewport(x, glY, w, h);
      viewportRect[0] = x;
      viewportRect[1] = glY;
      viewportRect[2] = w;
      viewportRect[3] = h;
    }
  }

  /**
   * Sets the scissor rectangle. The caller is responsible for computing the correct {@code glY} (i.e.
   * {@code fbHeight - y - h}) before calling this method.
   *
   * @param x      the left edge in pixels
   * @param glY    the bottom edge in OpenGL coordinates (already Y-flipped)
   * @param w      the scissor width
   * @param h      the scissor height
   * @param enable {@code true} to enable scissor testing, {@code false} to disable
   */
  public void setScissor(int x, int glY, int w, int h, boolean enable) {
    setScissorEnabled(enable);
    if (enable) {
      if (scissorRect[0] != x || scissorRect[1] != glY || scissorRect[2] != w || scissorRect[3] != h) {
        glScissor(x, glY, w, h);
        scissorRect[0] = x;
        scissorRect[1] = glY;
        scissorRect[2] = w;
        scissorRect[3] = h;
      }
    }
  }

  /**
   * Sets the stencil test state for the specified face.
   *
   * @param face      the polygon face to configure, either {@code GL_FRONT} or {@code GL_BACK}
   * @param func      the stencil test function (e.g., {@code GL_ALWAYS}, {@code GL_EQUAL})
   * @param ref       the reference value used in the stencil test
   * @param mask      the mask applied to both the reference and stencil buffer values before testing
   * @param fail      the action taken when the stencil test fails
   * @param zfail     the action taken when the stencil test passes but the depth test fails
   * @param zpass     the action taken when both the stencil and depth tests pass
   * @param writeMask the bitmask used to control which stencil bits can be written
   */
  public void setStencilFace(int face, int func, int ref, int mask, int fail, int zfail, int zpass, int writeMask) {
    if (face == GL_FRONT) {
      if (stencilFFunc != func || stencilFRef != ref || stencilFMask != mask) {
        glStencilFuncSeparate(GL_FRONT, func, ref, mask);
        stencilFFunc = func;
        stencilFRef = ref;
        stencilFMask = mask;
      }
      if (stencilFFail != fail || stencilFZFail != zfail || stencilFZPass != zpass) {
        glStencilOpSeparate(GL_FRONT, fail, zfail, zpass);
        stencilFFail = fail;
        stencilFZFail = zfail;
        stencilFZPass = zpass;
      }
      if (stencilFWriteMask != writeMask) {
        glStencilMaskSeparate(GL_FRONT, writeMask);
        stencilFWriteMask = writeMask;
      }
    } else {
      if (stencilBFunc != func || stencilBRef != ref || stencilBMask != mask) {
        glStencilFuncSeparate(GL_BACK, func, ref, mask);
        stencilBFunc = func;
        stencilBRef = ref;
        stencilBMask = mask;
      }
      if (stencilBFail != fail || stencilBZFail != zfail || stencilBZPass != zpass) {
        glStencilOpSeparate(GL_BACK, fail, zfail, zpass);
        stencilBFail = fail;
        stencilBZFail = zfail;
        stencilBZPass = zpass;
      }
      if (stencilBWriteMask != writeMask) {
        glStencilMaskSeparate(GL_BACK, writeMask);
        stencilBWriteMask = writeMask;
      }
    }
  }

  /**
   * Sets the color mask for writing color components to the framebuffer.
   *
   * @param r whether the red component can be written ({@code true} to enable, {@code false} to disable)
   * @param g whether the green component can be written
   * @param b whether the blue component can be written
   * @param a whether the alpha component can be written
   */
  public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
    if (colorMaskR != r || colorMaskG != g || colorMaskB != b || colorMaskA != a) {
      glColorMask(r, g, b, a);
      colorMaskR = r;
      colorMaskG = g;
      colorMaskB = b;
      colorMaskA = a;
    }
  }
}
