package net.fmhi;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.GfxStats;
import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.View;
import net.fmhi.gfx.brush.ZeroCopyVertexStore;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.buffer.BufferObjectDesc;
import net.fmhi.gfx.glfw.GlfwView;
import net.fmhi.gfx.input.Key;
import net.fmhi.gfx.input.KeyCode;
import net.fmhi.gfx.input.Modifiers;
import net.fmhi.gfx.input.Snapshot;
import net.fmhi.gfx.input.event.ResizeEvent;
import net.fmhi.gfx.io.PngInputStream;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.brush.Primitive2D;
import net.fmhi.gfx.opengl.OpenGLDevice;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.pipe.*;
import net.fmhi.gfx.shader.*;
import net.fmhi.gfx.text.FallbackFont;
import net.fmhi.gfx.text.Literal;
import net.fmhi.gfx.texture.*;
import net.fmhi.math.Box2D;
import net.fmhi.math.Color;
import net.fmhi.math.Vector2;
import net.fmhi.render.LiquidRenderer;
import net.fmhi.render.SkyRenderer;
import net.fmhi.render.TileRenderer;
import net.fmhi.gfx.math.Camera2D;

import net.fmhi.math.random.RandomGenerator;
import net.fmhi.util.Profiler;
import net.fmhi.util.ResourceProvider;
import net.fmhi.util.Util;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.BlockStateHolder;
import net.fmhi.world.block.Shape;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.FlatTerrainGenerator;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.CelestialUtil;
import net.fmhi.world.light.Channel;
import net.fmhi.world.light.LightMapRenderer;
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
  /** Day clock multiplier while Ctrl is held (8 real minutes → 15 s per day). */
  private static final float FAST_TIME_SCALE = 32F;
  private static TileRenderer tileRenderer;
  private static LiquidRenderer liquidRenderer;
  private static SkyRenderer skyRenderer;
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
    // demo fluids: a water pool at the spawn, a lava pool on the plateau,
    // and a splash of water next to the lava so the reaction is visible
    // (Y-up: the ground is at GROUND_Y, above it is higher Y)
    for (int x = -4; x <= 4; x++) level.setLiquid(x, GROUND_Y + 1, Liquids.WATER, FluidEngine.FULL);
    for (int x = 13; x <= 16; x++) level.setLiquid(x, GROUND_Y + 8, Liquids.LAVA, FluidEngine.FULL);
    level.setLiquid(12, GROUND_Y + 8, Liquids.WATER, FluidEngine.FULL);
    try {
      texture = Texture.loadRGBA8(dev, new PngInputStream(ResourceProvider.classpath(Main.class).openStream("/img.png")).info());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    tileRenderer = TileRenderer.create(dev);
    liquidRenderer = LiquidRenderer.create(dev);
    skyRenderer = new SkyRenderer();

    // Y-up: the player position is the feet; the ground block at GROUND_Y
    // spans [GROUND_Y, GROUND_Y+1], so the feet rest on its top
    Entity player = Entity.player(new PrecisePos(0, GROUND_Y + 1F));
    player.enterChunk(level);
    // release GPU meshes when the chunk streaming drops a chunk
    level.setUnloadListener(pos -> { if (tileRenderer != null) tileRenderer.unloadChunk(pos); });

    List<ThrownItem> thrownItems = new ArrayList<>();
    boolean qWasDown = false;

    var colorfulstate = defaultState(Registries.COLORFUL);
    var airState = defaultState(Registries.AIR);

    Camera2D camera = new Camera2D((float) WIN_W, (float) WIN_H, dev.getTransformHandler());
    // screen-space camera for the sky, fixed to the viewport
    Camera2D screenCam = new Camera2D((float) WIN_W, (float) WIN_H, dev.getTransformHandler());
    screenCam.setOrthographic(WIN_W, WIN_H);
    screenCam.setCenter(new Vector2(WIN_W / 2F, WIN_H / 2F));

    // -- off-screen buffers -------------------------------------------------
    colorRT = dev.getRenderTarget(RenderTargetDesc.offscreen(WIN_W, WIN_H));
    RenderTarget frontRT = dev.getRenderTarget(RenderTargetDesc.offscreen(WIN_W, WIN_H));
    var le = level.lightEngine();
    var lightMapRenderer = new LightMapRenderer(level);
    lightMapRenderer.init(dev, 800, 450);
    Sampler lmSampler = lightMapRenderer.sampler();

    // -- compose pipeline (matches built-in vlTexture vertex layout) -------
    VertexLayout vlCompose = VertexLayout.bake(
        new VertexLayout.Attr(3, VertexAttributeType.FLOAT32, false), // pos
        new VertexLayout.Attr(4, VertexAttributeType.FLOAT16, false), // gradient
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
        .rasterization(RasterizationDesc.NOT_CULL)
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

    BatchedGraphics2D g = new BatchedGraphics2D(new ZeroCopyVertexStore(true), dev);
    Key ML = view.snapshot().key(KeyCode.MOUSE_LEFT);
    Key MR = view.snapshot().key(KeyCode.MOUSE_RIGHT);
    Vector2[] oldCtRef = {Vector2.ZERO};
    Vector2[] prevCamRef = {Vector2.ZERO};

    Snapshot[] snapRef = new Snapshot[1];

    Util.launch(20, () -> {
      if (view.shouldClose()) {
        Util.stop();
      }

      // -- tick: fixed 60 Hz logic ----------------------------------------
      float dt = Util.delta();
      view.pollEvents();
      Snapshot snap = view.snapshot();
      snapRef[0] = snap;

      if (snap.isDown(KeyCode.ESCAPE)) {
        Util.stop();
        return;
      }
      if (snap.isDown(KeyCode.X)) worldViewW = Math.min(400F, worldViewW + 40F * dt);
      if (snap.isDown(KeyCode.Z)) worldViewW = Math.max(8F, worldViewW - 40F * dt);
      if (snap.isDown(KeyCode.S)) player.ignorePlatformTemporarily();

      float vx = 0F;
      if (snap.isDown(KeyCode.A)) vx = -WALK_SPEED;
      if (snap.isDown(KeyCode.D)) vx = WALK_SPEED;
      boolean jump = snap.isDown(KeyCode.W) || snap.isDown(KeyCode.SPACE);
      if (jump && player.onGround())
        player.setVelocity(vx, JUMP_SPEED); // Y-up: jumping is +Y
      else if (jump) {
        // Starbound-style swimming: burst on press, smooth approach while
        // held, never launches out of the water
        player.setVelocity(vx, player.velocity().y());
        player.liquidJump(true, dt);
      } else
        player.setVelocity(vx, player.velocity().y());

      var vp = Box2D.create(0, 0, view.width(), view.height());
      var w = camera.unproject(new Vector2((float) snap.cursorX(), (float) snap.cursorY()), vp);
      var bp = new BlockPos((int) Math.floor(w.x()), (int) Math.floor(w.y()));

      if (ML.isDown() || MR.isDown()) {
        if (ML.isDown(Modifiers.CONTROL))
          level.setWall(bp, airState);
        else if (level.getBlock(bp).block() == Registries.AIR && MR.isDown(Modifiers.CONTROL))
          level.setWall(bp, colorfulstate);
        else if (ML.isDown())
          level.setBlock(bp, airState);
        else if (level.getBlock(bp).block() == Registries.AIR && MR.isDown())
          level.setBlock(bp, colorfulstate);
      }

      // F1/F2: spawn water / lava at the cursor, breaking the block there
      if (snap.isDown(KeyCode.F1)) {
        level.setBlock(bp, airState);
        level.setLiquid(bp.x(), bp.y(), Liquids.WATER, FluidEngine.FULL);
      }
      if (snap.isDown(KeyCode.F2)) {
        level.setBlock(bp, airState);
        level.setLiquid(bp.x(), bp.y(), Liquids.LAVA, FluidEngine.FULL);
      }

      boolean qDown = snap.isDown(KeyCode.Q);
      if (qDown) {
        var m = camera.unproject(new Vector2((float) snap.cursorX(), (float) snap.cursorY()), vp);
        float dx = m.x() - player.position().xf();
        float dy = m.y() - player.position().yf();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        var item = new ThrownItem(player.position(),
            new Vector2(dx / len * 25F, dy / len * 25F));
        item.enterChunk(level);
        thrownItems.add(item);
      }

      // focus drives the chunk streaming (which chunks to keep loaded)
      var pc = player.center();
      level.setFocus(pc.xf(), pc.yf());

      // debug: hold F1 for the full-bright lightmap (isolates lightmap render cost)
      lightMapRenderer.fullBright = snap.isDown(KeyCode.F1);

      level.tick(dt);
      // hold Ctrl to rush the day cycle (simulation keeps real-time pace)
      level.setTimeScale(snap.isDown(KeyCode.LEFT_CONTROL) || snap.isDown(KeyCode.RIGHT_CONTROL)
          ? FAST_TIME_SCALE
          : 1F);

      float wh = worldViewW * view.height() / view.width();
      camera.setOrthographic(worldViewW, wh);
      float cx = player.center().xf();
      float cy = player.center().yf();

      prevCamRef[0] = oldCtRef[0];
      camera.setCenter(oldCtRef[0]);
      oldCtRef[0] = oldCtRef[0].add(new Vector2(cx, cy).subtract(oldCtRef[0]).multiply(0.2F));
      // -- sky / light engine ----------------------------------------------
      // sunlight is injected externally from the day phase (CelestialUtil); the
      // engine stays time-of-day agnostic
      Color sun = CelestialUtil.lightingSunlight(level);
      level.lightEngine().sunlight[0] = sun.red();
      level.lightEngine().sunlight[1] = sun.green();
      level.lightEngine().sunlight[2] = sun.blue();
      var camBounds = cameraBounds(camera);
      level.lightEngine().tick(camBounds);
    }, () -> {
      // -- draw: once per frame --------------------------------------------
      String err = spCompose.checkCompilationError();
      if (err != null) throw new GraphicsException("Compose shader error:\n" + err);

      // camera interpolated between the previous and the current tick
      camera.setCenter(new Vector2(
          Util.lerp(prevCamRef[0].x(), oldCtRef[0].x(), Util.partialTicks()),
          Util.lerp(prevCamRef[0].y(), oldCtRef[0].y(), Util.partialTicks())));

      // ===================================================================
      // PASS 0: sky → swapchain (screen space, behind everything)
      // ===================================================================
      dev.getTransformHandler().flipY(false); // Y-up screen space, like Enchant
      g.begin(RenderPass.of(new Color(0.05F, 0.05F, 0.08F)));
      g.setCamera(screenCam);
      try (Profiler.Scope _ = Profiler.scope("rendering:sky")) {
        skyRenderer.render(g, level, WIN_W, WIN_H, (int) camera.center().y());
      }
      g.end();

      // ===================================================================
      // PASS 1a: wall layer → wallRT (full bright; tint from lightmap;
      // clear transparent so the compose alpha-mix reveals the sky)
      // ===================================================================
      dev.getTransformHandler().flipY(false); // world is Y-up: render without flipping
      g.begin(RenderPass.of(colorRT, new Color(0, 0, 0, 0)));
      g.setCamera(camera);
      try (Profiler.Scope _ = Profiler.scope("rendering:wall")) {
        if (tileRenderer != null) tileRenderer.renderWalls(g, level, camera);
        else renderWalls(g, level, camera);
      }
      g.end();

      // ===================================================================
      // lightmaps (wall + front; front covers ALL tiles so entities
      // always have light — Enchant style)
      // ===================================================================
      lightMapRenderer.render(g, camera, level.lightEngine());
      dev.getTransformHandler().flipY(true);

      // ===================================================================
      // PASS 2a: wall albedo × wallLightmap → screen (alpha mix;
      // transparent background reveals the sky behind)
      // ===================================================================
      Camera2D orthoCam = new Camera2D(WIN_W, WIN_H, dev.getTransformHandler());
      orthoCam.setOrthographic(WIN_W, WIN_H);
      orthoCam.setCenter(new Vector2(WIN_W / 2F, WIN_H / 2F));
      try (Profiler.Scope _ = Profiler.scope("rendering:wall_compose")) {
        g.begin(RenderPass.NOT_CLEAR);
        g.setCamera(orthoCam);

        Texture wallTex = colorRT.pin();
        ResourceSet rsWall = dev.getResourceSet(rslCompose);
        rsWall.bindUniform(0, composeUbo, 64);
        if (wallTex != null) rsWall.bindTexture(1, wallTex, lmSampler);
        rsWall.bindTexture(2, lightMapRenderer.backLightmap().pin(), lmSampler);
        g.setPipeline(pipeAlpha, rsWall);
        g.setColor(Color.WHITE);
        g.drawTexture(wallTex, 0, 0, WIN_W, WIN_H);
        g.setPipeline(null, null);
        g.end();
      }

      // ===================================================================
      // PASS 1b: front layer (blocks + entities) → frontRT (transparent bg)
      // ===================================================================
      dev.getTransformHandler().flipY(false); // world is Y-up: render without flipping
      g.begin(RenderPass.of(frontRT, new Color(0, 0, 0, 0)));
      g.setCamera(camera);
      try (Profiler.Scope _ = Profiler.scope("rendering:block")) {
        renderPlayer(g, player);
        for (var item : thrownItems) {
          g.setColor(new Color(1F, 0.8F, 0F));
          var ipos = item.renderPosition();
          var ib = item.bounds();
          g.drawRectangle(ipos.xf(), ipos.yf(), ib.width(), ib.height());
        }
        if (liquidRenderer != null) liquidRenderer.render(g, level, camera);
        else renderLiquids(g, level, camera);
        if (tileRenderer != null) tileRenderer.render(g, level, camera);
        else renderBlocks(g, level, camera);
      }

      g.end();
      dev.getTransformHandler().flipY(true);

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
        rsFront.bindTexture(2, lightMapRenderer.frontLightmap().pin(), lmSampler);
        g.setPipeline(pipeAlpha, rsFront);
        g.setColor(Color.WHITE);
        g.drawTexture(frontTex, 0, 0, WIN_W, WIN_H);
        g.setPipeline(null, null);

        g.drawText(Literal.of("Pos=" + player.position()), 5, 5);
        g.end();
      }

      dev.submit(view::present);
      dev.execute();
      GfxStats.profile();

      dev.pollEvents();
      snapRef[0].clearFrameState();
    });

    g.close();
    pipeCompose.close();
    pipeAlpha.close();
    spCompose.close();
    lightMapRenderer.close();
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
    var cp = cam.center();
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
    var cp = cam.center();
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

  private static void renderLiquids(BatchedGraphics2D g, Level level, Camera2D cam) {
    // the tile renderer leaves the batch in the textured pipeline; the
    // liquid quads below are plain colored vertices (20 B) and must
    // switch back to the gradient primitive (flushes the pending batch)
    g.setPrimitive(Primitive2D.COLOR_TRIANGLE_INDEXED);
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    for (int cx = (int)Math.floor((cp.x()-vw/2F)/cs); cx <= (int)Math.floor((cp.x()+vw/2F)/cs); cx++)
      for (int cy = (int)Math.floor((cp.y()-vh/2F)/cs); cy <= (int)Math.floor((cp.y()+vh/2F)/cs); cy++) {
        Chunk ck = level.getChunk(new ChunkPos(cx, cy));
        if (ck == null) continue;
        for (int ly = 0; ly < cs; ly++)
          for (int lx = 0; lx < cs; lx++) {
            int wx = cx * cs + lx, wy = cy * cs + ly;
            int lv = ck.getLiquidLevel(wx, wy);
            if (lv <= 0) continue;
            Liquid liq = Liquids.byId(ck.getLiquidType(wx, wy));
            long packed = liq.color().pack();
            // falling liquid (nothing below) renders as a small centered
            // block, like Starbound
            if (isFalling(level, wx, wy)) {
              // centered rectangle, width scaled by the level (0..1)
              float half = (float) lv / FluidEngine.FULL / 2F;
              g.putPosColor(wx + 0.5F - half, wy + 0.5F - half, 0, packed);
              g.putPosColor(wx + 0.5F + half, wy + 0.5F - half, 0, packed);
              g.putPosColor(wx + 0.5F + half, wy + 0.5F + half, 0, packed);
              g.putPosColor(wx + 0.5F - half, wy + 0.5F + half, 0, packed);
              g.endQuad();
              continue;
            }
            // Y-up: liquid fills the tile from the bottom up; the surface
            // edge is smoothed against the neighbouring surfaces
            float surf = wy + Math.min(lv, FluidEngine.FULL) / (float) FluidEngine.FULL;
            float topL = (surf + surfaceOf(level, wx - 1, wy, liq, surf)) / 2F;
            float topR = (surf + surfaceOf(level, wx + 1, wy, liq, surf)) / 2F;
            g.putPosColor(wx, topL, 0, packed);
            g.putPosColor(wx + 1, topR, 0, packed);
            g.putPosColor(wx + 1, wy, 0, packed);
            g.putPosColor(wx, wy, 0, packed);
            g.endQuad();
          }
      }
  }

  private static float surfaceOf(Level level, int wx, int wy, Liquid type, float fallback) {
    Chunk c = level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy));
    if (c == null) return fallback;
    int lv = c.getLiquidLevel(wx, wy);
    if (lv <= 0 || Liquids.byId(c.getLiquidType(wx, wy)) != type) return fallback;
    return wy + Math.min(lv, FluidEngine.FULL) / (float) FluidEngine.FULL;
  }

  /** Whether the liquid tile has nothing supporting it below (falling).
   * Y-up: below is {@code wy - 1}. */
  private static boolean isFalling(Level level, int wx, int wy) {
    Chunk c = level.getChunkByKey(ChunkPos.packBlockPosAsLong(wx, wy - 1));
    if (c == null) return false;
    if (c.getLiquidLevel(wx, wy - 1) == FluidEngine.FULL) return false;
    return c.getBlock(wx, wy - 1).shape() != Shape.SOLID;
  }

  // -- shared --------------------------------------------------------------

  private static void renderPlayer(BatchedGraphics2D g, Entity p) {
    g.setColor(Color.RED);
    var rp = p.renderPosition();
    var b = p.bounds();
    g.drawRectangle(rp.xf(), rp.yf(), b.width(), b.height());
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
    var p = c.center();
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
      rgb[0] = (float)RandomGenerator.DEFAULT.nextDouble();
      rgb[1] = (float)RandomGenerator.DEFAULT.nextDouble();
      rgb[2] = (float)RandomGenerator.DEFAULT.nextDouble();
    }

    @Override
    public float emitAmbient(byte channel) {
      return switch (channel) {
        case Channel.RED -> rgb[0];
        case Channel.GREEN -> rgb[1];
        default -> rgb[2];
      };
    }
  }
}
