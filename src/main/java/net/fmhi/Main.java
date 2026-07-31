package net.fmhi;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.View;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.input.Key;
import net.fmhi.gfx.input.KeyCode;
import net.fmhi.gfx.input.Modifiers;
import net.fmhi.gfx.input.Snapshot;
import net.fmhi.gfx.input.event.ResizeEvent;
import net.fmhi.gfx.io.PngInputStream;
import net.fmhi.gfx.mesh.BatchedGraphics2D;
import net.fmhi.gfx.opengl.OpenGLDevice;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.text.FallbackFont;
import net.fmhi.gfx.texture.*;
import net.fmhi.math.Box2D;
import net.fmhi.math.Color;
import net.fmhi.math.Vector2;
import net.fmhi.gfx.math.Camera2D;

import net.fmhi.math.random.RandomGenerator;
import net.fmhi.util.Profiler;
import net.fmhi.util.ResourceProvider;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.BlockStateHolder;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.FlatTerrainGenerator;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.LightBuffer;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;
import net.fmhi.world.util.PrecisePos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Two-pass with lightmap composition via custom shader.
 *
 * <p>Pass 1: world → off-screen RenderTarget (full bright).
 * Pass 2: fullscreen quad with compose shader → swapchain
 * ({@code albedo * lightmap}).
 */
public class Main {

  private static final int WIN_W = 1280;
  private static final int WIN_H = 720;
  private static final float WALK_SPEED = 12F;
  private static final float JUMP_SPEED = 22F;
  private static final int GROUND_Y = 20;
  private static final int WORLD_RADIUS = 16;
  private static float worldViewW = 32F;
  static RenderTarget colorRT;
  static Texture texture;

  public static void main(String[] args) {
    Registries.bootstrap();

    View view = new GlfwView();
    view.setTitle("fmhi — lightmap compose");
    view.setSize(new Vector2(WIN_W, WIN_H));
    view.initialize();

    Device dev = new OpenGLDevice();
    dev.load(view);
    FallbackFont.init(dev);

    var gen = new FlatTerrainGenerator(GROUND_Y);
    var level = new Level(gen, 42L);
    for (int cx = -WORLD_RADIUS; cx <= WORLD_RADIUS; cx++)
      for (int cy = -WORLD_RADIUS; cy <= WORLD_RADIUS; cy++)
        level.getOrLoadChunk(new ChunkPos(cx, cy));
    try {
      texture = Texture.loadRGBA8(dev, new PngInputStream(ResourceProvider.classpath(Main.class).openStream("/img.png")).info());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Entity player = Entity.player(new PrecisePos(0, GROUND_Y - 2.65F));
    player.enterChunk(level);

    List<ThrownItem> thrownItems = new ArrayList<>();
    boolean qWasDown = false;

    var colorfulstate = defaultState(Registries.COLORFUL);
    var airState = defaultState(Registries.AIR);

    Camera2D camera = new Camera2D((float) WIN_W, (float) WIN_H, dev.getTransformHandler());

    // -- off-screen buffers -------------------------------------------------
    colorRT = dev.getRenderTarget(RenderTargetDesc.offscreen(WIN_W, WIN_H));
    RenderTarget frontRT = dev.getRenderTarget(RenderTargetDesc.offscreen(WIN_W, WIN_H));
    var le = level.lightEngine();
    le.initLightmaps(dev, 800, 450);
    Sampler lmSampler = le.sampler();

    // -- compose pipeline (matches built-in vlTexture vertex layout) -------
    VertexLayout vlCompose = VertexLayout.bake(
        new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false), // pos
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false), // color
        new VertexLayout.Attr(2, VertexAttributeType.FLOAT32, false)  // uv
    );

    ResourceSetLayout rslCompose = ResourceSetLayout.bake(
        new Slot(1, "T", ShaderType.VERTEX_BIT, ResourceType.UNIFORM_BUFFER),
        new Slot(1, "u_albedo", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE),
        new Slot(1, "u_lightmap", ShaderType.FRAGMENT_BIT, ResourceType.TEXTURE));

    ResourceProvider rp = ResourceProvider.classpath(Main.class);
    ShaderProgram spCompose = ShaderProgram.load(dev,
        rp.readString("/shaders/light_compose.vert.glsl"),
        rp.readString("/shaders/light_compose.frag.glsl"),
        ShaderLanguage.GLSL);

    Pipeline pipeCompose = dev.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.DISABLED).depth(Depth.DISABLED)
        .rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(spCompose)
        .vertexLayout(vlCompose)
        .resourceLayouts(rslCompose)
        .build());

    // alpha-mix variant: front layer composites over the wall layer,
    // transparent background pixels reveal the walls underneath
    Pipeline pipeAlpha = dev.getRenderPipeline(new PipelineDesc.Builder()
        .blend(Blend.ALPHA_MIX).depth(Depth.DISABLED)
        .rasterization(RasterizationDesc.DEFAULT)
        .shaderProgram(spCompose)
        .vertexLayout(vlCompose)
        .resourceLayouts(rslCompose)
        .build());

    BufferObject composeUbo = dev.getBuffer(BufferObjectDesc.uniform());
    composeUbo.allocate(64, null);

    view.eventBus().register(ResizeEvent.class, (ctx, e) -> {
      colorRT = dev.getRenderTarget(RenderTargetDesc.offscreen(e.width(), e.height()));
    });

    BatchedGraphics2D g = new BatchedGraphics2D(dev);
    long lastNanos = System.nanoTime();
    Key ML = view.snapshot().key(KeyCode.MOUSE_LEFT);
    Key MR = view.snapshot().key(KeyCode.MOUSE_RIGHT);
    Vector2 oldCt = Vector2.ZERO;

    while (!view.shouldClose()) {
      String err = spCompose.checkCompilationError();
      if (err != null) throw new GraphicsException("Compose shader error:\n" + err);

      long nowNanos = System.nanoTime();
      float dt = (nowNanos - lastNanos) / 1_000_000_000F;
      lastNanos = nowNanos;
      if (dt > 1F / 20F) dt = 1F / 20F;

      view.pollEvents();
      Snapshot snap = view.snapshot();

      if (snap.isDown(KeyCode.ESCAPE)) break;
      if (snap.isDown(KeyCode.X)) worldViewW = Math.min(400F, worldViewW + 40F * dt);
      if (snap.isDown(KeyCode.Z)) worldViewW = Math.max(8F, worldViewW - 40F * dt);
      if (snap.isDown(KeyCode.S)) player.ignorePlatformTemporarily();

      float vx = 0F;
      if (snap.isDown(KeyCode.A)) vx = -WALK_SPEED;
      if (snap.isDown(KeyCode.D)) vx = WALK_SPEED;
      boolean jump = snap.isDown(KeyCode.W) || snap.isDown(KeyCode.SPACE);
      if (jump && player.onGround())
        player.setVelocity(vx, -JUMP_SPEED);
      else
        player.setVelocity(vx, player.velocity().y());

      if (ML.isDown() || MR.isDown()) {
        var vp = Box2D.create(0, 0, view.width(), view.height());
        var w = camera.unproject(new Vector2((float) snap.cursorX(), (float) snap.cursorY()), vp);
        var bp = new BlockPos((int) Math.floor(w.x()), (int) Math.floor(w.y()));
        if (ML.isDown(Modifiers.CONTROL))
          level.setWall(bp, airState);
        else if (level.getBlock(bp).block() == Registries.AIR && MR.isDown(Modifiers.CONTROL))
          level.setWall(bp, colorfulstate);
        else if (ML.isDown())
          level.setBlock(bp, airState);
        else if (level.getBlock(bp).block() == Registries.AIR && MR.isDown())
          level.setBlock(bp, colorfulstate);
      }

      boolean qDown = snap.isDown(KeyCode.Q);
      if (qDown) {
        var vp = Box2D.create(0, 0, view.width(), view.height());
        var m = camera.unproject(new Vector2((float) snap.cursorX(), (float) snap.cursorY()), vp);
        float dx = m.x() - player.position().xf();
        float dy = m.y() - player.position().yf();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        var item = new ThrownItem(player.position(),
            new Vector2(dx / len * 25F, dy / len * 25F));
        item.enterChunk(level);
        thrownItems.add(item);
      }

      player.tick(dt, level);
      for (var item : thrownItems) item.tick(dt, level);
      thrownItems.removeIf(i -> {
        if (i.position().y() > 500) {
          var cp = new ChunkPos(Math.floorDiv((int)Math.floor(i.position().xf()), ChunkPos.SIZE),
                                Math.floorDiv((int)Math.floor(i.position().yf()), ChunkPos.SIZE));
          Chunk c = level.getChunk(cp); if (c != null) c.removeEntity(i);
          return true;
        }
        return false;
      });

      float wh = worldViewW * view.height() / view.width();
      camera.setOrthographic(worldViewW, wh);
      float cx = player.center().xf();
      float cy = player.center().yf();

      camera.setCenter(oldCt);
      oldCt = oldCt.add(new Vector2(cx, cy).subtract(oldCt).multiply(0.2F));
      // -- light engine ----------------------------------------------------
      var camBounds = cameraBounds(camera);
      level.lightEngine().tick(camBounds);

      // ===================================================================
      // PASS 1a: wall layer → wallRT (full bright; tint from lightmap)
      // ===================================================================
      g.begin(RenderPass.of(colorRT, new Color(0.05F, 0.05F, 0.08F)));
      g.setCamera(camera);
      try (Profiler.Scope _ = Profiler.scope("rendering:wall")) {
        renderWalls(g, level, camera);
      }
      g.end();

      // ===================================================================
      // lightmaps (wall + front; front covers ALL tiles so entities
      // always have light — Enchant style)
      // ===================================================================
      level.lightEngine().generateLightmaps(g, camera);

      // ===================================================================
      // PASS 2a: wall albedo × wallLightmap → screen (OPAQUE)
      // ===================================================================
      Camera2D orthoCam = new Camera2D(WIN_W, WIN_H, dev.getTransformHandler());
      orthoCam.setOrthographic(WIN_W, WIN_H);
      orthoCam.setPosition(new Vector2(WIN_W / 2F, WIN_H / 2F));
      try (Profiler.Scope _ = Profiler.scope("rendering:wall_compose")) {
        g.begin(RenderPass.DEFAULT);
        g.setCamera(orthoCam);

        Texture wallTex = colorRT.pin();
        ResourceSet rsWall = dev.getResourceSet(rslCompose);
        rsWall.bindUniform(0, composeUbo, 64);
        if (wallTex != null) rsWall.bindTexture(1, wallTex, lmSampler);
        rsWall.bindTexture(2, le.backLightmap().pin(), lmSampler);
        g.setPipeline(pipeCompose, rsWall);
        g.setColor(Color.WHITE);
        g.drawTexture(wallTex, 0, 0, WIN_W, WIN_H);
        g.setPipeline(null, null);
        g.end();
      }

      // ===================================================================
      // PASS 1b: front layer (blocks + entities) → frontRT (transparent bg)
      // ===================================================================
      g.begin(RenderPass.of(frontRT, new Color(0, 0, 0, 0)));
      g.setCamera(camera);
      try (Profiler.Scope _ = Profiler.scope("rendering:block")) {
        renderBlocks(g, level, camera);
        renderPlayer(g, player);
        for (var item : thrownItems) {
          g.setColor(new Color(1F, 0.8F, 0F));
          g.drawRectangle(item.bounds().minX(), item.bounds().minY(), item.bounds().width(), item.bounds().height());
        }
      }
      g.end();

      // ===================================================================
      // PASS 2b: front albedo × frontLightmap → screen (alpha mix —
      //   transparent pixels reveal the wall layer below)
      // ===================================================================
      try (Profiler.Scope _ = Profiler.scope("rendering:block_compose")) {
        g.begin(RenderPass.NOT_CLEAR);
        g.setCamera(orthoCam);
        Texture frontTex = frontRT.pin();
        ResourceSet rsFront = dev.getResourceSet(rslCompose);
        rsFront.bindUniform(0, composeUbo, 64);
        if (frontTex != null) rsFront.bindTexture(1, frontTex, lmSampler);
        rsFront.bindTexture(2, le.frontLightmap().pin(), lmSampler);
        g.setPipeline(pipeAlpha, rsFront);
        g.setColor(Color.WHITE);
        g.drawTexture(frontTex, 0, 0, WIN_W, WIN_H);
        g.setPipeline(null, null);
        g.end();
      }

      dev.execute();
      dev.submit(view::present);
      dev.pollEvents();
      snap.clearFrameState();
    }

    g.close();
    pipeCompose.close();
    pipeAlpha.close();
    spCompose.close();
    le.close();
    colorRT.close();
    frontRT.close();
    composeUbo.close();
    dev.close();
    view.close();
    GlfwView.terminate();
    Profiler.dump();
  }

  // -- PASS 1 --------------------------------------------------------------

  private static void renderWalls(BatchedGraphics2D g, Level level, Camera2D cam) {
    var cp = cam.position();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    for (int cx = (int)Math.floor((cp.x()-vw/2F)/cs); cx <= (int)Math.floor((cp.x()+vw/2F)/cs); cx++)
      for (int cy = (int)Math.floor((cp.y()-vh/2F)/cs); cy <= (int)Math.floor((cp.y()+vh/2F)/cs); cy++) {
        Chunk ck = level.getOrLoadChunk(new ChunkPos(cx, cy));
        for (int lx = 0; lx < cs; lx++)
          for (int ly = 0; ly < cs; ly++) {
            BlockState s = ck.getWall(lx, ly);
            if (s == null || s.block() == Registries.AIR) continue;
            g.setColor(blockColor(s));
            g.drawRectangle(cx*cs+lx, cy*cs+ly, 1F, 1F);
          }
      }
  }

  private static void renderBlocks(BatchedGraphics2D g, Level level, Camera2D cam) {
    var cp = cam.position();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    for (int cx = (int)Math.floor((cp.x()-vw/2F)/cs); cx <= (int)Math.floor((cp.x()+vw/2F)/cs); cx++)
      for (int cy = (int)Math.floor((cp.y()-vh/2F)/cs); cy <= (int)Math.floor((cp.y()+vh/2F)/cs); cy++) {
        Chunk ck = level.getOrLoadChunk(new ChunkPos(cx, cy));
        for (int lx = 0; lx < cs; lx++)
          for (int ly = 0; ly < cs; ly++) {
            BlockState s = ck.getBlock(lx, ly);
            if (s == null || s.block() == Registries.AIR) continue;
            g.setColor(blockColor(s));
            g.drawRectangle(cx*cs+lx, cy*cs+ly, 1F, 1F);
          }
      }
  }

  // -- VP upload -----------------------------------------------------------

  private static void uploadVP(BufferObject ubo, net.fmhi.math.Matrix4x4 vpm) {
    float[] m = vpm.toFloatArray();
    byte[] b = new byte[64];
    for (int i = 0; i < 16; i++) {
      int bits = Float.floatToRawIntBits(m[i]);
      int off = i * 4;
      b[off] = (byte) bits; b[off + 1] = (byte) (bits >> 8);
      b[off + 2] = (byte) (bits >> 16); b[off + 3] = (byte) (bits >> 24);
    }
    ubo.submit(b, 0, 64);
  }

  // -- shared --------------------------------------------------------------

  private static void renderPlayer(BatchedGraphics2D g, Entity p) {
    g.setColor(Color.RED);
    var b = p.bounds();
    g.drawRectangle(b.minX(), b.minY(), b.width(), b.height());
  }

  private static Color blockColor(BlockState s) {
    var b = s.block();
    if (b == Registries.GRASS)       return new Color(0.2F, 0.6F, 0.15F);
    if (b == Registries.DIRT)        return new Color(0.4F, 0.27F, 0.15F);
    if (b == Registries.STONE)       return new Color(0.5F, 0.5F, 0.5F);
    if (b == Registries.SLOPE_RIGHT) return new Color(0.7F, 0.5F, 0.2F);
    if (b == Registries.SLOPE_LEFT)  return new Color(0.8F, 0.6F, 0.3F);
    if (b == Registries.PLATFORM)    return new Color(0.2F, 0.6F, 0.8F);
    if (b == Registries.WALL)        return new Color(0.6F, 0.4F, 0.2F);
    if (b == Registries.COLORFUL)        return new Color(0.3F, 0.5F, 0.8F);
    return Color.BLACK;
  }

  private static Box2D cameraBounds(Camera2D c) {
    var p = c.position();
    float vw = c.width() / c.zoom();
    float vh = c.height() / c.zoom();
    return Box2D.createCentral(p.x(), p.y(), vw, vh);
  }

  private static BlockState defaultState(net.fmhi.world.block.Block block) {
    int id = block.propertyDef().defaultMap().identity();
    return BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE.get(id);
  }

  static class ThrownItem extends Entity {
    float[] rgb = new  float[3];

    ThrownItem(PrecisePos pos, Vector2 vel) {
      super(0.55F, 0.55F);
      setPosition(pos);
      setVelocity(vel);
      bounceFactor = 0.5F;
      groundFriction = 0.2F;
      shouldSlideOnSlope = true;
      rgb[0] = (float)RandomGenerator.DEFAULT.nextDouble();
      rgb[1] = (float)RandomGenerator.DEFAULT.nextDouble();
      rgb[2] = (float)RandomGenerator.DEFAULT.nextDouble();
    }

    @Override
    public boolean getLight(LightBuffer buf) {
      buf.r(rgb[0]);
      buf.g(rgb[1]);
      buf.b(rgb[2]);
      return true;
    }
  }
}
