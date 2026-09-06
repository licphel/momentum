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

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.internal.InternalApi;

/**
 * Provides OpenGL-specific coordinate and texture conversions.
 *
 * <p>The handler is independent of camera orientation. Each camera supplies the projection
 * bounds that represent its selected Y-axis direction before asking this handler to build a
 * projection matrix.
 */
@InternalApi
public class OpenGLTransformHandler implements TransformHandler {
  @Override
  public float u(Texture tex, float u) {
    return u / tex.width();
  }

  @Override
  public float v(Texture tex, float v) {
    return 1.0F - v / tex.height();
  }

  @Override
  public Matrix4x4 createOrthographic(float left, float right, float bottom, float top, float near, float far) {
    return Matrix4x4.createOrthographic(left, right, bottom, top, near, far);
  }

  @Override
  public Matrix4x4 createPerspective(float fovY, float aspect, float near, float far) {
    return Matrix4x4.createPerspective(fovY, aspect, near, far);
  }
}
