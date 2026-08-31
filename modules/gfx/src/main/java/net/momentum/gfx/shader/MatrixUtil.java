package net.momentum.gfx.shader;

import net.momentum.gfx.DirectBufferPool;
import net.momentum.gfx.buffer.BufferObject;
import net.momentum.math.Matrix4x4;

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
  public static void writeViewProjection(Matrix4x4 vpm, BufferObject ubo) {
    ByteBuffer out = DirectBufferPool.acquire(4 * 4 * Float.BYTES);

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

    ubo.submit(out.flip());
    DirectBufferPool.release(out);
  }
}