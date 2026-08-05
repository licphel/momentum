package net.fmhi.gfx.opengl;

import net.fmhi.gfx.math.TransformHandler;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.math.Matrix4x4;
import net.fmhi.util.InternalApi;

/**
 * OpenGL transform handler - encodes OpenGL coordinate convention to ours.
 */
@InternalApi
public class OpenGLTransformHandler implements TransformHandler {
  boolean flipY;

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
    if (flipY) {
      float tmp = top;
      top = bottom;
      bottom = tmp;
    }
    return Matrix4x4.createOrthographic(left, right, bottom, top, near, far);
  }

  @Override
  public Matrix4x4 createPerspective(float fovY, float aspect, float near, float far) {
    return Matrix4x4.createPerspective(fovY, aspect, near, far);
  }

  @Override
  public void flipY(boolean flipY) {
    this.flipY = flipY;
  }

  @Override
  public boolean isYFlipped() {
    return flipY;
  }
}
