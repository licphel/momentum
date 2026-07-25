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

import net.fmhi.gfx.BuiltinGfx;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.buffer.BufferType;
import net.fmhi.gfx.io.ImageInfo;
import net.fmhi.gfx.io.ImageInputStream;
import net.fmhi.gfx.mesh.dim2.BatchedGraphics2D;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.texture.*;
import net.fmhi.gfx.View;
import net.fmhi.math.*;
import net.fmhi.math.dim2.Camera2D;
import net.fmhi.fml.resource.ResourceFinder;
import net.fmhi.util.NativeLookup;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Custom pipeline test: Gaussian blur post-processing.
 */
public class CustomPipeMain {
  // shared vertex shader — same layout as BuiltinGfx textured
  private static final String VERT = """
      #version 330 core
      layout(location = 0) in vec3 i_pos; layout(location = 1) in vec4 i_col;
      layout(location = 2) in vec2 i_uv;
      out vec2 o_uv;
      layout(std140) uniform T { mat4 u_vp; };
      void main() { o_uv = i_uv; gl_Position = u_vp * vec4(i_pos, 1.0); }
      """;

  private static final String BLUR_FRAG = """
      #version 330 core
      in vec2 o_uv;
      uniform sampler2D u_tex;
      layout(std140) uniform D { vec2 u_dir; };
      layout(location = 0) out vec4 f;
      void main() {
        float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
        vec2 s = u_dir;
        vec3 c = texture(u_tex, o_uv).rgb * w[0];
        c += texture(u_tex, o_uv + s).rgb * w[1] + texture(u_tex, o_uv - s).rgb * w[1];
        c += texture(u_tex, o_uv + 2.*s).rgb * w[2] + texture(u_tex, o_uv - 2.*s).rgb * w[2];
        c += texture(u_tex, o_uv + 3.*s).rgb * w[3] + texture(u_tex, o_uv - 3.*s).rgb * w[3];
        c += texture(u_tex, o_uv + 4.*s).rgb * w[4] + texture(u_tex, o_uv - 4.*s).rgb * w[4];
        f = vec4(c, 1.0);
      }
      """;

  static void main(String[] args) throws IOException {
    View view = NativeLookup.create(View.class);
    view.setTitle("Custom Pipeline — Gaussian Blur");
    view.setMaximized(true);
    view.initialize();
    Device dev = NativeLookup.create(Device.class);
    dev.load(view);

    int w = view.width(), h = view.height();

    // --- FBOs ---
    RenderTarget sceneFbo = dev.getRenderTarget(w, h);
    RenderTarget blurFbo  = dev.getRenderTarget(w, h);

    // --- u_dir UBO & VP UBO ---
    BufferObject dirUbo = dev.getBuffer(new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM));
    dirUbo.allocate(8, null);
    BufferObject vpUbo = dev.getBuffer(new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM));
    vpUbo.allocate(64, null);

    // --- blur layout: [VP UBO, u_tex, u_dir UBO] ---
    ResourceSetLayout blurRsl = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_tex", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE),
        new Slot(1, "D", ShaderType.FRAGMENT_BIT, ResourceType.UNIFORM_BUFFER));

    ShaderModule vMod = dev.getShaderModule(new ShaderModuleDesc(ShaderType.VERTEX, VERT));
    ShaderModule bFrag = dev.getShaderModule(new ShaderModuleDesc(ShaderType.FRAGMENT, BLUR_FRAG));
    ShaderProgram bProg = dev.getShaderProgram(vMod, bFrag);
    Pipeline pipeBlur = dev.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.DISABLED).depth(Depth.DISABLED).rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(bProg).vertexLayout(BuiltinGfx.vlTexture).resourceLayouts(blurRsl).build());

    // --- texture ---
    ImageInputStream img = ImageInputStream.open(new FileInputStream(
        ResourceFinder.getAppRoot().resolve(".ref/a.png").toFile()));
    ImageInfo info = img.info();
    Texture tex = dev.getTexture(new TextureDesc.Builder()
        .width(info.width()).height(info.height()).initialBytes(info.pixels()).mipLevels(4).build());
    Sampler samp = BuiltinGfx.sampler;

    BatchedGraphics2D batch = new BatchedGraphics2D(dev);
    Camera2D cam = new Camera2D(w, h);
    double last = System.nanoTime();

    while (!view.shouldClose()) {
      view.pollEvents();
      dev.pollEvents();
      double t = (System.nanoTime() - last) * 1e-9;

      // ---- Pass 1: scene → sceneFbo ----
      batch.setCamera(cam);
      batch.begin(new RenderPass.Builder().target(sceneFbo)
          .clearColor(new Color(0.04f, 0.04f, 0.06f, 1f)).build());

      float bx = w / 2f + (float) Math.sin(t) * 120;
      float by = h / 2f + (float) Math.cos(t * 1.3f) * 80;
      batch.setColor(new Color(1f, 0.35f, 0.15f, 1f));
      batch.drawRectangle(bx - 50, by - 50, 100, 100);
      batch.setColor(new Color(0.15f, 0.5f, 1f, 1f));
      batch.drawRectangle(80, 60, 180, 180);
      batch.setColor(Color.WHITE);
      batch.drawTexture(tex, w / 2f - 20, h / 2f - 20, 50, 50);
      batch.setColor(new Color(1f, 0.85f, 0.1f, 1f));
      batch.drawLine(40, 40, w - 40, h - 40);
      batch.drawLine(40, h - 40, w - 40, 40);
      batch.end();

      // ---- Pass 2: H-blur sceneFbo → blurFbo ----
      uploadVP(vpUbo, cam.viewProjectionMatrix());
      setBlurDir(dirUbo, 1f / w, 0f);
      ResourceSet rsH = dev.getResourceSet(blurRsl);
      rsH.bindUniform(0, vpUbo, 64);
      rsH.bindTexture(1, sceneFbo.pin(), samp);
      rsH.bindUniform(2, dirUbo, 8);

      batch.begin(new RenderPass.Builder().target(blurFbo).clearColor(Color.BLACK).build());
      batch.setPipeline(pipeBlur, rsH);
      batch.drawTexture(sceneFbo, 0, 0, w, h);  // fullscreen quad
      batch.end();

      // ---- Pass 3: V-blur blurFbo → screen ----
      setBlurDir(dirUbo, 0f, 1f / h);
      ResourceSet rsV = dev.getResourceSet(blurRsl);
      rsV.bindUniform(0, vpUbo, 64);
      rsV.bindTexture(1, blurFbo.pin(), samp);
      rsV.bindUniform(2, dirUbo, 8);

      batch.begin(new RenderPass.Builder().clearColor(Color.BLACK).build());
      batch.setPipeline(pipeBlur, rsV);
      batch.drawTexture(blurFbo, w / 2f - 120, h / 2f - 120, 250, 250);
      batch.end();

      dev.submit(view::present);
      dev.execute();
      view.snapshot().clearFrameState();
    }

    batch.close(); tex.close();
    sceneFbo.close(); blurFbo.close();
    pipeBlur.close(); bProg.close();
    vMod.close(); bFrag.close();
    dirUbo.close(); vpUbo.close();
    view.close();
  }

  private static void setBlurDir(BufferObject ubo, float x, float y) {
    int bx = Float.floatToRawIntBits(x), by = Float.floatToRawIntBits(y);
    ubo.submit(new byte[]{(byte) bx, (byte) (bx >> 8), (byte) (bx >> 16), (byte) (bx >> 24),
        (byte) by, (byte) (by >> 8), (byte) (by >> 16), (byte) (by >> 24)}, 0, 8);
  }

  private static void uploadVP(BufferObject ubo, Matrix4x4 vpm) {
    float[] m = vpm.toFloatArray();
    byte[] bytes = new byte[64];
    for (int i = 0; i < 16; i++) {
      int bits = Float.floatToRawIntBits(m[i]);
      int off = i * 4;
      bytes[off] = (byte) bits;
      bytes[off + 1] = (byte) (bits >> 8);
      bytes[off + 2] = (byte) (bits >> 16);
      bytes[off + 3] = (byte) (bits >> 24);
    }
    ubo.submit(bytes, 0, 64);
  }
}
