package net.fmhi.gfx.opengl;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.View;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TextureDesc;
import net.fmhi.gfx.texture.TextureFormat;
import net.fmhi.math.Box3D;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glReadPixels;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RGBA;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGetTexImage;

/**
 * Verifies that {@code OpenGLTexture.submit} and {@code FboTexture.submit} store the
 * uploaded rows identically: a known top-origin pattern (row 0 = red, row 1 = blue) is
 * submitted to both, read back, and compared byte for byte.
 */
public class TestTextureFlip {
  /** 2×2 RGBA8: top row red, bottom row blue. */
  private static final byte[] PATTERN = {
      (byte) 255, 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255,
      0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255
  };

  @Test
  void storageMatches() {
    View view = new GlfwView();
    view.setTitle("Texture flip test");
    view.initialize();
    Device dev = new OpenGLDevice();
    dev.load(view);

    try {
      byte[] fromTexture = readOpenGLTexture(dev);
      byte[] fromFbo = readFboTexture(dev);
      System.out.println("OpenGLTexture readback: " + hex(fromTexture));
      System.out.println("FboTexture     readback: " + hex(fromFbo));
      // row 0 of the readback is the GL first row: it must hold the submitted
      // bottom row (blue) — the flip is applied, matching the lightmap convention
      System.out.println("Expected pattern  [bottom-first]: " + hex(new byte[] {
          0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255,
          (byte) 255, 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255
      }));
      System.out.println("Identical storage: " + Arrays.equals(fromTexture, fromFbo));
      assertTrue(Arrays.equals(fromTexture, fromFbo), "OpenGLTexture and FboTexture store rows differently");
    } finally {
      dev.close();
      view.close();
    }
  }

  /** Submits the pattern to a plain texture and reads the stored bytes back. */
  private static byte[] readOpenGLTexture(Device dev) {
    OpenGLTexture tex = (OpenGLTexture) dev.getTexture(new TextureDesc.Builder()
        .width(2).height(2).format(TextureFormat.RGBA8).build());
    tex.submit(ByteBuffer.wrap(PATTERN), Box3D.create(0, 0, 0, 2, 2, 1));
    dev.execute();

    ByteBuffer out = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    glBindTexture(GL_TEXTURE_2D, tex.handle);
    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, out);
    glBindTexture(GL_TEXTURE_2D, 0);
    return drain(out);
  }

  /** Submits the pattern to a render target's FboTexture and reads its FBO back. */
  private static byte[] readFboTexture(Device dev) {
    OpenGLRenderTarget rt = (OpenGLRenderTarget) dev.getRenderTarget(RenderTargetDesc.offscreen(2, 2));
    Texture fboTex = rt.pin();
    fboTex.submit(ByteBuffer.wrap(PATTERN), Box3D.create(0, 0, 0, 2, 2, 1));
    dev.execute();

    ByteBuffer out = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    glBindFramebuffer(GL_FRAMEBUFFER, rt.fboHandle());
    glReadPixels(0, 0, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, out);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return drain(out);
  }

  private static byte[] drain(ByteBuffer buf) {
    byte[] out = new byte[buf.remaining()];
    buf.rewind();
    buf.get(out);
    return out;
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString().trim();
  }
}
