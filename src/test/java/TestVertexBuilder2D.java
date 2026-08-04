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

import net.fmhi.audio.Clip;
import net.fmhi.audio.Controller;
import net.fmhi.audio.Mixer;
import net.fmhi.audio.io.AudioFormatException;
import net.fmhi.audio.io.AudioInputStream;
import net.fmhi.audio.openal.OpenALMixer;
import net.fmhi.codec.i18n.Language;
import net.fmhi.codec.tag.CompoundTag;
import net.fmhi.fml.resource.ResourceFinder;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.View;
import net.fmhi.gfx.brush.ZeroCopyVertexStore;
import net.fmhi.gfx.brush.tint.SimpleGradient;
import net.fmhi.gfx.buffer.BufferFrequency;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.buffer.BufferType;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.cmd.EncoderDesc;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.input.Key;
import net.fmhi.gfx.input.KeyCode;
import net.fmhi.gfx.input.Modifiers;
import net.fmhi.gfx.io.ImageInfo;
import net.fmhi.gfx.io.ImageInputStream;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.brush.MeshGraphics2D;
import net.fmhi.gfx.opengl.OpenGLDevice;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.text.Font;
import net.fmhi.gfx.text.Literal;
import net.fmhi.gfx.text.MutableText;
import net.fmhi.gfx.text.TextFormat;
import net.fmhi.gfx.text.raster.Raster;
import net.fmhi.gfx.texture.*;
import net.fmhi.gfx.ui.AnchorLayout;
import net.fmhi.gfx.ui.UiContext;
import net.fmhi.gfx.ui.look.ModernFlat;
import net.fmhi.gfx.ui.widget.Button;
import net.fmhi.gfx.ui.widget.Window;
import net.fmhi.math.*;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.math.dim2.CatenaryCurve;
import net.fmhi.gfx.math.CameraPerspective3D;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Textured rotating cube with Phong lighting.
 */
public class TestVertexBuilder2D {

  // ---------------------------------------------------------------------------
  // Shaders
  // ---------------------------------------------------------------------------

  private static final String VERT_SRC = """
      #version 330 core
      layout(location = 0) in vec3 aPos;
      layout(location = 1) in vec3 aNormal;
      layout(location = 2) in vec2 aUV;
      
      layout(std140) uniform Matrices {
          mat4 uMVP;
          mat4 uModel;
          mat3 uNormalMatrix;
      };
      
      out vec3 vNormal;
      out vec3 vWorldPos;
      out vec2 vUV;
      
      void main() {
          vec4 worldPos = uModel * vec4(aPos, 1.0);
          vWorldPos = worldPos.xyz;
          vNormal = uNormalMatrix * aNormal;
          vUV = aUV;
          gl_Position = uMVP * vec4(aPos, 1.0);
      }
      """;

  private static final String FRAG_SRC = """
      #version 330 core
      in vec3 vNormal;
      in vec3 vWorldPos;
      in vec2 vUV;
      
      uniform sampler2D uTexture;
      
      layout(std140) uniform Lighting {
          vec3 uLightPos;
          vec3 uLightColor;
          vec3 uAmbient;
          vec3 uViewPos;
      };
      
      out vec4 fragColor;
      
      void main() {
          vec3 normal = normalize(vNormal);
          vec3 ambient = uAmbient;
          vec3 lightDir = normalize(uLightPos - vWorldPos);
          float diff = max(dot(normal, lightDir), 0.0);
          vec3 diffuse = diff * uLightColor;
          vec3 viewDir = normalize(uViewPos - vWorldPos);
          vec3 reflectDir = reflect(-lightDir, normal);
          float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
          vec3 specular = spec * uLightColor * 0.5;
          vec3 texColor = texture(uTexture, vUV).rgb;
          vec3 result = (ambient + diffuse) * texColor + specular;
          fragColor = vec4(result, 1.0);
      }
      """;

  // ---------------------------------------------------------------------------
  // Cube — 24 vertices, CCW winding, pos + normal + uv
  // ---------------------------------------------------------------------------

  private static final float[] CUBE_DATA = {
      // Front  (+z)
      -0.5F, -0.5F, 0.5F, 0, 0, 1, 0, 1, 0.5F, -0.5F, 0.5F, 0, 0, 1, 1, 1, 0.5F, 0.5F, 0.5F, 0, 0, 1, 1, 0, -0.5F,
      0.5F, 0.5F, 0, 0, 1, 0, 0,
      // Back   (-z)
      0.5F, -0.5F, -0.5F, 0, 0, -1, 0, 1, -0.5F, -0.5F, -0.5F, 0, 0, -1, 1, 1, -0.5F, 0.5F, -0.5F, 0, 0, -1, 1, 0,
      0.5F, 0.5F, -0.5F, 0, 0, -1, 0, 0,
      // Right  (+x)
      0.5F, -0.5F, 0.5F, 1, 0, 0, 0, 1, 0.5F, -0.5F, -0.5F, 1, 0, 0, 1, 1, 0.5F, 0.5F, -0.5F, 1, 0, 0, 1, 0, 0.5F,
      0.5F, 0.5F, 1, 0, 0, 0, 0,
      // Left   (-x)
      -0.5F, -0.5F, -0.5F, -1, 0, 0, 0, 1, -0.5F, -0.5F, 0.5F, -1, 0, 0, 1, 1, -0.5F, 0.5F, 0.5F, -1, 0, 0, 1, 0,
      -0.5F, 0.5F, -0.5F, -1, 0, 0, 0, 0,
      // Top    (+y)
      -0.5F, 0.5F, 0.5F, 0, 1, 0, 0, 1, 0.5F, 0.5F, 0.5F, 0, 1, 0, 1, 1, 0.5F, 0.5F, -0.5F, 0, 1, 0, 1, 0, -0.5F,
      0.5F, -0.5F, 0, 1, 0, 0, 0,
      // Bottom (-y)
      -0.5F, -0.5F, -0.5F, 0, -1, 0, 0, 1, 0.5F, -0.5F, -0.5F, 0, -1, 0, 1, 1, 0.5F, -0.5F, 0.5F, 0, -1, 0, 1, 0,
      -0.5F, -0.5F, 0.5F, 0, -1, 0, 0, 0,};

  private static final int[] CUBE_INDICES = {0, 1, 2, 0, 2, 3,    // Front  — CCW from +z
      4, 5, 6, 4, 6, 7,    // Back   — CCW from -z
      8, 9, 10, 8, 10, 11,  // Right  — CCW from +x
      12, 13, 14, 12, 14, 15, // Left  — CCW from -x
      16, 17, 18, 16, 18, 19, // Top   — CCW from +y
      20, 21, 22, 20, 22, 23, // Bottom— CCW from -y
  };

  // ---------------------------------------------------------------------------
  // Entry point
  // ---------------------------------------------------------------------------

  static void main(String[] args) throws IOException {
    // --- Audio ---
    Mixer mixer = new OpenALMixer();
    Clip clip;
    try {
      Path p = ResourceFinder.getAppRoot().resolve(".ref", "dopd.wav");
      AudioInputStream audio = AudioInputStream.open(new FileInputStream(p.toFile()));
      byte[] dat = audio.readAllBytes();
      clip = mixer.getClip();
      clip.open(audio.format(), dat);
      clip.set(Controller.VOLUME, 1);
      clip.set(Controller.PITCH, 1);
      clip.loop(1);
    } catch (AudioFormatException e) {
      throw new RuntimeException(e);
    }

    // --- Window ---
    View theView = new GlfwView();
    theView.setTitle("Fmhi Test");
    theView.setDecorated(true);
    theView.setMaximized(false);
    theView.initialize();
    Device dev = new OpenGLDevice();
    dev.load(theView);

    // --- UI ---
    UiContext ui = new UiContext(dev, theView, new ModernFlat(), new Box2D(0, 0, 800, 450));

    // Camera ---
    CameraPerspective3D camera = new CameraPerspective3D(dev.getTransformHandler());
    camera.setAspectRatio((float) theView.width() / theView.height());
    camera.setNearPlane(0.1F);
    camera.setFarPlane(100.0F);
    camera.setPosition(new Vector3(2.0F, 1.5F, 3.0F));
    camera.setTarget(Vector3.ZERO);

    // --- Light ---
    Vector3 lightPos = new Vector3(3.0F, 5.0F, 2.0F);
    Vector3 lightColor = new Vector3(1.0F, 0.95F, 0.8F);
    Vector3 ambient = new Vector3(0.25F, 0.25F, 0.28F);

    // --- Shaders ---

    CompoundTag tag = new CompoundTag();
    tag.put("win.locale", "Window {0}");
    Language.get(Language.EN_US_KEY).merge(tag);

    // --- Texture ---
    ImageInputStream img = ImageInputStream.open(new FileInputStream(ResourceFinder.getAppRoot().resolve(".ref",
        "img" + ".png").toFile()));
    ImageInfo texInfo = img.info();
    TextureDesc texDesc =
        new TextureDesc.Builder().width(texInfo.width()).height(texInfo.height()).initialBytes(texInfo.pixels()).mipLevels(4).build();
    Texture texture = dev.getTexture(texDesc);
    Texture resized = dev.getTexture(new TextureDesc.Builder().width(500).height(500).build());
    texture.blit(resized, 0, 0, texture.width(), texture.height(), 0, 0, 150, 50);
    resized.blit(texture, 0, 0, 150, 50, 0, 0, 150, 50);

    Sampler sampler =
        dev.getSampler(new SamplerDesc.Builder().minFilter(TextureFilter.LINEAR_MIPMAP_LINEAR).magFilter(TextureFilter.LINEAR).build());

    // --- Vertex layout ---
    VertexLayout layout = VertexLayout.bake(new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false),
        new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false), new VertexLayout.Attr(2,
            VertexAttributeType.FLOAT32, false));

    // --- Resource set layout ---
    ResourceSetLayout rsLayout = ResourceSetLayout.bake(new Slot(1, "uTexture", ShaderType.FRAGMENT_BIT,
        ResourceType.TEXTURE), new Slot(1, "Matrices", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "Lighting", ShaderType.FRAGMENT_BIT, ResourceType.UNIFORM_BUFFER));

    // --- Pipeline ---

    // --- Buffers ---
    BufferObject vbo = dev.getBuffer(BufferObjectDesc.vertex(BufferFrequency.STATIC));
    vbo.submit(floatArrayToBytes(CUBE_DATA));
    BufferObject ibo = dev.getBuffer(BufferObjectDesc.index(BufferFrequency.STATIC));
    ibo.submit(intArrayToBytes(CUBE_INDICES));

    // --- Uniforms ---
    // UBO 0: Matrices — MVP(64) + Model(64) + NormalMatrix(48) = 176
    BufferObject uboMatrices = dev.getBuffer(new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM));
    uboMatrices.allocate(176, null);
    // UBO 1: Lighting — LightPos(16) + LightColor(16) + Ambient(16) + ViewPos(16) = 64
    BufferObject uboLighting = dev.getBuffer(new BufferObjectDesc(BufferFrequency.DYNAMIC, BufferType.UNIFORM));
    uboLighting.allocate(64, null);

    // --- Resource set ---
    ResourceSet resourceSet = dev.getResourceSet(rsLayout);
    resourceSet.bindTexture(0, texture, sampler);
    resourceSet.bindUniform(1, uboMatrices, 176, 0);
    resourceSet.bindUniform(2, uboLighting, 64, 0);

    Encoder enc = dev.getEncoder(EncoderDesc.DEFAULT);
    RenderPass clearDesc = RenderPass.of(new Color(0.1F, 0.12F, 0.15F, 1.0F));

    Font font = Font.open(dev, ByteBuffer.wrap(new FileInputStream(".ref/kst.ttf").readAllBytes()));
    Font font2 = Font.open(dev, ByteBuffer.wrap(new FileInputStream(".ref/main.ttf").readAllBytes()));

    // --- UI widgets ---
    TextFormat fmt = new TextFormat.Builder(font2).size(10).build();

    Window uiWin = Window.create(40, 40, 260, 180);
    uiWin.setTitle(Literal.translated("win.locale", "Hello").with(TextFormat.of().size(8)));
    ui.addWindow(uiWin);
    uiWin.open();

    Window uiWin2 = Window.create(320, 60, 200, 150);
    uiWin2.setTitle(Literal.of("Another Window"));
    ui.addWindow(uiWin2);
    uiWin2.open();

    Window uiWin3 = Window.create(10, 10, 100, 100);
    uiWin3.setTitle(Literal.of("Another Window"));
    uiWin.addChild(uiWin3);
    uiWin3.open();

    Button btn = Button.create(10, 10, 100, 24);
    btn.setLabel(Literal.of("Click me!"));
    btn.onClick(() -> System.out.println("[UI] Button clicked!"));
    // Stretch full width, pin to top with 8px margin and 24px height
    btn.setAnchorLayout(AnchorLayout.topStretch(8, 8, 24));
    uiWin.addChild(btn);

    // --- TestVertexBuilder2D loop ---
    float angle = 0.0F;
    double lastTime = System.nanoTime();
    int frame = 0;
    final int MAX_FRAMES = 6000000;

    Key key0 = theView.snapshot().key(KeyCode.E);
    boolean init = false;

    while (!theView.shouldClose() && frame < MAX_FRAMES) {
      double now = System.nanoTime();
      float delta = (float) ((now - lastTime) * 1e-9);
      lastTime = now;

      theView.pollEvents();
      mixer.pollEvents();
      dev.pollEvents();

      angle += 45.0F * delta;
      float rad = (float) Math.toRadians(angle * 0.5F);
      camera.setPosition(new Vector3((float) Math.sin(rad) * 3.0F, 1.5F + (float) Math.sin(rad * 1.3F) * 0.5F,
          (float) Math.cos(rad) * 3.0F));
      camera.setTarget(Vector3.ZERO);

      Matrix4x4 model = Matrix4x4.createRotation(Vector3.UNIT_Y, (float) Math.toRadians(angle));
      Matrix4x4 view = camera.getViewMatrix();
      Matrix4x4 projection = camera.getProjectionMatrix();
      Matrix4x4 mvp = projection.multiply(view).multiply(model);
      Matrix3x3 normalMatrix = model.invert().transpose().toMatrix3x3();

      updateMatricesUbo(uboMatrices, mvp, model, normalMatrix);
      updateLightingUbo(uboLighting, lightPos, lightColor, ambient, camera.getPosition());

      /*
      dev.getSwapchain().acquire(clearDesc);
      enc.reset();
      enc.setRenderPipe(pipe);
      enc.setTopology(Topology.TRIANGLE);
      enc.setBuffer(vbo);
      enc.setBuffer(ibo);
      enc.setViewport(0, 0, theView.width(), theView.height());
      enc.setResource(0, resourceSet);
      enc.drawIndexed(CUBE_INDICES.length, 0);
      enc.queuedExecute();
       */
      dev.getTransformHandler().flipY(true);

      MeshGraphics2D g = new MeshGraphics2D(new ZeroCopyVertexStore(), dev);
      g.begin(RenderPass.DEFAULT);
      g.setCamera(new Camera2D(800, 450, dev.getTransformHandler()));
      g.setColor(Color.WHITE);
      MutableText cns = new MutableText().justify(true).flipY(true).maxWidth(260);
      cns.newline();
      cns.append(Literal.of("多语言测试多语言测试多语言测试多语言测试多语言测试",
          new TextFormat.Builder(font2).size(8).style(Font.BOLD | Font.ITALIC).build()));
      cns.newline();
      cns.append(Literal.of("Success is not final, failure is not fatal: it is the courage to continue that " +
          "counts. Every morning brings a fresh opportunity to start anew, to shed the weight of yesterday's " +
          "mistakes, and to move forward with a quiet determination. Life rarely unfolds in straight lines; instead, "
          + "it twists and turns, presenting challenges that test our resolve and moments of joy that remind us why " +
          "we " + "persevere. The small, consistent steps we take each day often matter far more than the occasional " +
          "leaps. " + "In the end, it is not the applause or the accolades that define us, but the strength we find " +
          "within " + "ourselves during the quiet, uncelebrated moments—the times when no one is watching, yet we " +
          "choose to keep " + "going. Embrace the journey with all its imperfections, for it is through struggle that" +
          " we discover who we " + "truly are.").with(new TextFormat.Builder(font).size(8).build()));
      cns.append(Literal.of("Insert a BIG component!").with(new TextFormat(font, new SimpleGradient(Color.WHITE),
          Font.ITALIC | Font.BOLD | Font.UNDERLINE, 16)));
      cns.append(Literal.of("Success is not final, failure is not fatal: it is the courage to continue that " +
          "counts. Every morning brings a fresh opportunity to start anew, to shed the weight of yesterday's " +
          "mistakes, and to move forward with a quiet determination. Life rarely unfolds in straight lines; instead, "
          + "it twists and turns, presenting challenges that test our resolve and moments of joy that remind us why " +
          "we " + "persevere. The small, consistent steps we take each day often matter far more than the occasional " +
          "leaps. " + "In the end, it is not the applause or the accolades that define us, but the strength we find " +
          "within " + "ourselves during the quiet, uncelebrated moments—the times when no one is watching, yet we " +
          "choose to keep " + "going. Embrace the journey with all its imperfections, for it is through struggle that" +
          " we discover who we " + "truly are.").with(new TextFormat.Builder(font2).size(8).build()));
      cns.append(Literal.of("多语言测试多语言测试多语言测试多语言测试多语言测试").with(new TextFormat(font2, new SimpleGradient(Color.WHITE),
          Font.ITALIC | Font.STRIKETHROUGH | Font.UNDERLINE, 8)));
      cns.newline();
      g.drawRectangleFrame(0, 0, 256, 256);
      g.drawTexture(texture, 0, 0, texture.width(), texture.height(), 0, 0, texture.width(), texture.height() / 2);
      g.drawText(cns, 200, 100);
      g.drawRectangle(200, 100, 3, 3);
      g.setColor(Color.RED);
      // bounds frame: compensate for originY so it aligns with the rendered text position.
      float boundsOriginX = 200 - cns.raster().bounds().minX();
      float boundsOriginY = 100 - cns.raster().bounds().minY();
      g.drawRectangleFrame(cns.raster().bounds().translate(new Vector2(boundsOriginX, boundsOriginY)));
      g.setColor(Color.WHITE);

      Vector2 cursor = theView.cursorPosition();
      // drawText places text so that bounds top-left maps to (200, 100).
      // hitTest operates in pen space, so subtract (200 - bounds.minX(), 100 - bounds.minY()).
      float hitOriginX = 200 - cns.raster().bounds().minX();
      float hitOriginY = 100 - cns.raster().bounds().minY();
      cursor = new Camera2D(800, 450, dev.getTransformHandler()).unproject(cursor, g.currentViewport());
      int idx = cns.raster().hitTest(cursor.subtract(new Vector2(hitOriginX, hitOriginY)));
      if (idx >= 0) {
        // hitTest returns a char index; find the entry whose charIndex matches.
        for (Raster.Entry entry : cns.raster().entries()) {
          if (entry.charIndex() == idx) {
            g.drawRectangleFrame(entry.bounds().translate(new Vector2(boundsOriginX, boundsOriginY)));
            break;
          }
        }
        if (idx < cns.text().length()) {
          g.drawText(Literal.of("" + cns.text().charAt(idx), new TextFormat.Builder(font).size(16).build()), 5, 5);
        }
      }
      g.setViewport(Box2D.create(0, 0, theView.width(), theView.height()));
      g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.2F));
      for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 10; i++) {
          g.transform().push();
          g.transform().load(Matrix3x2.createRotation(frame / 50.0F,
              new Vector2(i * 50.0F, j * 100F).add(new Vector2(50, 50))).toMatrix4x4());
          g.drawTexture(texture, i / 2.0F, j * 100F, 100, 100);
          g.transform().pop();
        }
      }
      g.setColor(Color.WHITE);
      g.drawLine(0, 100, 200, 200);
      g.drawCurve(new CatenaryCurve(500, 300, -275F, 400, 600), 100);

      ui.render(g, delta);

      g.end();

      BatchedGraphics2D bg = new BatchedGraphics2D(new ZeroCopyVertexStore(), dev);
      Mesh mesh = g.bake(dev);

      bg.begin();
      bg.setCamera(new Camera2D(800, 450, dev.getTransformHandler()));
      bg.drawMesh(mesh);
      bg.drawTexture(texture, 0, 0, texture.width(), texture.height());
      bg.drawRectangleFrame(0, 0, 200, 200);
      bg.drawPolygonFrame(new Vector2(0, 0), new Vector2(200, 200), new Vector2(200, 400), new Vector2(20, 400));
      bg.drawOvalFrame(100, 100, 50, 30);
      bg.end();

      dev.submit(theView::present);
      dev.execute();

      frame++;

      if (key0.transitioned()) {
        System.out.println(1);
      }
      if (key0.transitionedOrRepeated()) {
        System.out.println(3);
      }
      if (key0.transitioned(Modifiers.ALT)) {
        System.out.println(4);
      }

      theView.snapshot().clearFrameState();
    }

    ui.close();
    enc.close();
    resourceSet.close();
    texture.close();
    sampler.close();
    uboMatrices.close();
    uboLighting.close();
    ibo.close();
    vbo.close();

    theView.close();
  }

  private static byte[] floatArrayToBytes(float[] a) {
    ByteBuffer bb = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float f : a) {
      bb.putFloat(f);
    }
    return bb.array();
  }

  private static byte[] intArrayToBytes(int[] a) {
    ByteBuffer bb = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (int i : a) {
      bb.putInt(i);
    }
    return bb.array();
  }

  private static void updateMatricesUbo(BufferObject ubo, Matrix4x4 mvp, Matrix4x4 model, Matrix3x3 normal) {
    ubo.submit(matrixToBytes(mvp), 0);
    ubo.submit(matrixToBytes(model), 64);
    ubo.submit(matrix3ToBytes(normal), 128);
  }

  private static void updateLightingUbo(BufferObject ubo, Vector3 lp, Vector3 lc, Vector3 amb, Vector3 vp) {
    ubo.submit(vec3ToBytes16(lp), 0);
    ubo.submit(vec3ToBytes16(lc), 16);
    ubo.submit(vec3ToBytes16(amb), 32);
    ubo.submit(vec3ToBytes16(vp), 48);
  }

  private static byte[] matrixToBytes(Matrix4x4 m) {
    ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    MatrixUtil.writeViewProjection(m, bb);
    return bb.array();
  }

  private static byte[] matrix3ToBytes(Matrix3x3 m) {
    ByteBuffer bb = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
    bb.putFloat(m.m00()).putFloat(m.m10()).putFloat(m.m20());
    bb.putFloat(m.m01()).putFloat(m.m11()).putFloat(m.m21());
    bb.putFloat(m.m02()).putFloat(m.m12()).putFloat(m.m22());
    return bb.array();
  }

  private static byte[] vec3ToBytes16(Vector3 v) {
    ByteBuffer bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
    bb.putFloat(v.x()).putFloat(v.y()).putFloat(v.z()).putFloat(0);
    return bb.array();
  }
}
