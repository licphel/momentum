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

import net.fmhi.gfx.Device;
import net.fmhi.gfx.View;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.buffer.BufferType;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.cmd.EncoderDesc;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.opengl.OpenGLDevice;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.text.Font;
import net.fmhi.gfx.text.Literal;
import net.fmhi.gfx.text.MutableText;
import net.fmhi.gfx.text.TextFormat;
import net.fmhi.gfx.text.raster.Raster;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.TextureFilter;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.Color;
import net.fmhi.math.Matrix4x4;
import net.fmhi.math.Vector3;
import net.fmhi.gfx.math.CameraPerspective3D;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 3D text test — renders text as texture-mapped quads in 3D space. */
public class Test3D {

  private static final String VERT = """
      #version 330 core
      layout(location = 0) in vec3 aPos;
      layout(location = 1) in vec2 aUV;
      layout(std140) uniform Matrices { mat4 uMVP; };
      out vec2 vUV;
      void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos, 1.0); }
      """;
  private static final String FRAG = """
      #version 330 core
      in vec2 vUV; uniform sampler2D uTexture;
      layout(location = 0) out vec4 fragColor;
      void main() { fragColor = texture(uTexture, vUV); }
      """;

  static void main(String[] args) {
    View view = new GlfwView();
    view.setTitle("3D Text");
    view.setMaximized(true);
    view.initialize();
    Device dev = new OpenGLDevice();
    dev.load(view);

    // Camera
    CameraPerspective3D cam = new CameraPerspective3D(dev.getTransformHandler());
    cam.setAspectRatio((float) view.width() / view.height());

    // Shader
    ShaderModule vert = dev.getShaderModule(new ShaderModuleDesc(ShaderType.VERTEX, VERT));
    ShaderModule frag = dev.getShaderModule(new ShaderModuleDesc(ShaderType.FRAGMENT, FRAG));
    ShaderProgram prog = dev.getShaderProgram(vert, frag);
    VertexLayout layout = VertexLayout.bake(new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false));
    ResourceSetLayout rsLayout = ResourceSetLayout.bake(new Slot(1, "uTexture", ShaderType.FRAGMENT_BIT,
        ResourceType.TEXTURE), new Slot(1, "Matrices", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER));
    Pipeline pipe =
        dev.getRenderPipeline(new PipelineDesc.Builder().blend(Blend.ALPHA_MIX).depth(Depth.DISABLED).rasterization(RasterizationDesc.NOT_CULL).vertexLayout(layout).shaderProgram(prog).resourceLayouts(rsLayout).build());

    BufferObject ubo = dev.getBuffer(new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM));
    ResourceSet rs = dev.getResourceSet(rsLayout);
    Encoder enc = dev.getEncoder(EncoderDesc.DEFAULT);
    Sampler sampler =
        dev.getSampler(new SamplerDesc.Builder().minFilter(TextureFilter.NEAREST).magFilter(TextureFilter.NEAREST).build());

    // Text
    Font font;
    try {
      font = Font.open(dev, ByteBuffer.wrap(new FileInputStream(".ref/main.ttf").readAllBytes()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    MutableText tc = new MutableText().justify(false).flipY(false).maxWidth(190);
    tc.append(Literal.of("苹果 (Apple)：",
        new TextFormat.Builder(font).size(8).color(Color.WHITE).style(Font.BOLD).build()));
    tc.append(Literal.of("1234567890",
        new TextFormat.Builder(font).size(8).color(Color.WHITE).style(Font.BOLD).build()));
    tc.append(Literal.of("多语言混排Multi-language line",
        new TextFormat.Builder(font).size(16).color(Color.RED).style(Font.BOLD).build()));
    tc.newline();
    tc.append(Literal.of("NEWLINE",
        new TextFormat.Builder(font).size(8).color(Color.RED).style(Font.BOLD).build()));
    Raster raster = tc.raster();

    // Quad geometry: pos(3f) + uv(2f), 4 verts, 6 indices
    float[] qVerts = new float[4 * 5];
    int[] qIdx = {0, 1, 2, 0, 2, 3};
    BufferObject vbo = dev.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STREAM));
    BufferObject ibo = dev.getBuffer(BufferObjectDesc.index(BufferFrequency.STATIC));
    ibo.submit(intsToBytes(qIdx));

    int frames = 0;
    while (!view.shouldClose()) {
      frames++;
      cam.setPosition(new Vector3(0, 0, 1));
      cam.setTarget(Vector3.ZERO);

      view.pollEvents();
      dev.pollEvents();

      enc.reset();
      enc.beginPass(RenderPass.DEFAULT);

      Matrix4x4 vp = cam.getProjectionMatrix().multiply(cam.getViewMatrix());

      enc.setRenderPipe(pipe);
      enc.setTopology(Topology.TRIANGLE);
      enc.setViewport(0, 0, view.width(), view.height());
      enc.endPass();
      enc.queuedExecute();

      // Draw each glyph as a textured quad (one at a time to avoid VBO overwrite)
      for (Raster.Entry b : raster.entries()) {
        if (b.glyph() == null) {
          continue;
        }
        TexturePart tp = b.glyph().texPart();
        float atlW = tp.src().width();
        float atlH = tp.src().height();
        float u0 = tp.u() / atlW;
        float u1 = (tp.u() + tp.width()) / atlW;
        // Atlas is always stored top-row-first. For Y-up (flipY=false), the visual top of the
        // quad is at by+sy, so atlas-top (v0) must map there — flip v0/v1 accordingly.
        float v0;
        float v1;
        if (raster.flipY()) {
          v0 = tp.v() / atlH;
          v1 = (tp.v() + tp.height()) / atlH;
        } else {
          v0 = (tp.v() + tp.height()) / atlH;
          v1 = tp.v() / atlH;
        }

        // Map text pixel coords → 3D space, centered on the raster bounds.
        // Use actual glyph bitmap size (texPart * scale), not the advance-cell size.
        float scale3d = 1.0F / Math.max(raster.bounds().width(), raster.bounds().height());
        float centerY = raster.bounds().minY() + raster.bounds().height() / 2f;
        float gx = b.bounds().minX();
        // Y-up: quad bottom = penY + (bearingY - texHeight)*scale, quad top = penY + bearingY*scale
        float gy = b.bounds().minY();
        float bx = (gx - raster.bounds().width() / 2f) * scale3d;
        float by = (gy - centerY) * scale3d;
        float sx = tp.width() * b.scale() * scale3d;
        float sy = tp.height() * b.scale() * scale3d;

        qVerts[0] = bx;
        qVerts[1] = by;
        qVerts[2] = 0;
        qVerts[3] = u0;
        qVerts[4] = v0;
        qVerts[5] = bx + sx;
        qVerts[6] = by;
        qVerts[7] = 0;
        qVerts[8] = u1;
        qVerts[9] = v0;
        qVerts[10] = bx + sx;
        qVerts[11] = by + sy;
        qVerts[12] = 0;
        qVerts[13] = u1;
        qVerts[14] = v1;
        qVerts[15] = bx;
        qVerts[16] = by + sy;
        qVerts[17] = 0;
        qVerts[18] = u0;
        qVerts[19] = v1;

        enc.reset();
        enc.beginPass(RenderPass.NOT_CLEAR);
        enc.setRenderPipe(pipe);
        enc.setTopology(Topology.TRIANGLE);
        enc.setViewport(0, 0, view.width(), view.height());

        Matrix4x4 mvp = vp.multiply(Matrix4x4.IDENTITY);
        MatrixUtil.writeViewProjection(mvp, ubo);
        rs.bindUniform(0, ubo, 64);
        rs.bindTexture(1, tp.src(), sampler);
        enc.setResource(0, rs);

        vbo.submit(floatsToBytes(qVerts));
        enc.setVertexBuffer(vbo);
        enc.setIndexBuffer(ibo);
        enc.drawIndexed(6, 0);

        enc.endPass();
        enc.queuedExecute();
      }

      dev.submit(view::present);
      dev.execute();
    }
    view.close();
  }

  static byte[] floatsToBytes(float[] a) {
    ByteBuffer bb = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float f : a) {
      bb.putFloat(f);
    }
    return bb.array();
  }

  static byte[] intsToBytes(int[] a) {
    ByteBuffer bb = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (int i : a) {
      bb.putInt(i);
    }
    return bb.array();
  }
}
