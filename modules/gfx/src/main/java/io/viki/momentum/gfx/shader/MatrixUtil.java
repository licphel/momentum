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

package io.viki.momentum.gfx.shader;

import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.math.Matrix4x4;

import java.nio.ByteBuffer;

/**
 * Utility class for matrix data manipulation and GPU data transfer.
 *
 * <p>This class provides efficient, zero-allocation methods for packing matrix data
 * into buffers suitable for upload to GPU memory (e.g., Uniform Buffer Objects).
 * It leverages a global {@link DirectBufferPool} to eliminate temporary object
 * allocation overhead during high-frequency rendering loops.
 */
public final class MatrixUtil {
  private MatrixUtil() {
  }

  /**
   * Writes a 4x4 view-projection matrix into a Uniform Buffer Object (UBO).
   *
   * @param vpm the view-projection matrix to be uploaded
   * @param ubo the target Uniform Buffer Object to receive the matrix data
   */
  public static void store(Matrix4x4 vpm, BufferObject ubo) {
    ByteBuffer out = DirectBufferPool.acquire(4 * 4 * Float.BYTES);
    store(vpm, out);
    ubo.submit(out.flip());
    DirectBufferPool.release(out);
  }

  /**
   * Writes a 4x4 view-projection matrix into a Uniform Buffer Object (UBO).
   *
   * @param vpm    the view-projection matrix to be uploaded
   * @param out the cpu-side cursor buffer
   */
  public static void store(Matrix4x4 vpm, ByteBuffer out) {
    // Column 0
    out.putFloat(vpm.m00());
    out.putFloat(vpm.m10());
    out.putFloat(vpm.m20());
    out.putFloat(vpm.m30());

    // Column 1
    out.putFloat(vpm.m01());
    out.putFloat(vpm.m11());
    out.putFloat(vpm.m21());
    out.putFloat(vpm.m31());

    // Column 2
    out.putFloat(vpm.m02());
    out.putFloat(vpm.m12());
    out.putFloat(vpm.m22());
    out.putFloat(vpm.m32());

    // Column 3
    out.putFloat(vpm.m03());
    out.putFloat(vpm.m13());
    out.putFloat(vpm.m23());
    out.putFloat(vpm.m33());
  }
}