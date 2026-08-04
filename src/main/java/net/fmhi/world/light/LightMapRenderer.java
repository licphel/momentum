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

package net.fmhi.world.light;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.brush.MeshGraphics2D;
import net.fmhi.gfx.brush.ZeroCopyVertexStore;
import net.fmhi.gfx.brush.tint.QuadGradient;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.TextureFilter;
import net.fmhi.gfx.texture.TextureWrap;
import net.fmhi.math.Color;
import net.fmhi.math.Vector2;
import net.fmhi.util.Profiler;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Renders the wall and front lightmaps of a {@link LightEngine} into two off-screen
 * render targets.
 *
 * <p>Each layer is a retained colored-quad mesh over the whole light window, rebuilt
 * only when the engine's light version changes (world edits, sunlight shifts, window
 * moves or resizes); a static frame is two draw calls with no per-tile work. The
 * renderer is decoupled from the engine: it only reads the completed front buffer via
 * the engine's public accessors.
 */
public final class LightMapRenderer implements AutoCloseable {
  private final Level level;
  /**
   * Debug switch: when {@code true}, lightmaps are drawn as plain white
   * rectangles, skipping the light computation to isolate rendering cost.
   */
  public boolean fullBright;
  private @Nullable Device dev;
  private RenderTarget wallLightRT;
  private RenderTarget frontLightRT;
  private Sampler lmSampler;
  private @Nullable MeshGraphics2D lmMeshG;
  private @Nullable Mesh frontLightMesh;
  private @Nullable Mesh wallLightMesh;
  private long lastVersion = -1;

  /**
   * Creates a lightmap renderer for the given level.
   *
   * @param level the level used to determine which tiles are walls
   */
  public LightMapRenderer(Level level) {
    this.level = level;
  }

  /**
   * Allocates the two lightmap render targets and the sampler used to sample them.
   *
   * @param dev    the graphics device
   * @param width  the lightmap width in pixels
   * @param height the lightmap height in pixels
   */
  public void init(Device dev, int width, int height) {
    this.dev = dev;
    wallLightRT = dev.getRenderTarget(RenderTargetDesc.offscreen(width, height));
    frontLightRT = dev.getRenderTarget(RenderTargetDesc.offscreen(width, height));
    lmSampler = dev.getSampler(new SamplerDesc.Builder()
        .minFilter(TextureFilter.NEAREST)
        .magFilter(TextureFilter.NEAREST)
        .wrapX(TextureWrap.CLAMP_TO_EDGE)
        .wrapY(TextureWrap.CLAMP_TO_EDGE)
        .build());
  }

  /**
   * Returns the render target holding the wall lightmap.
   *
   * @return the wall lightmap target
   */
  public RenderTarget backLightmap() {
    return wallLightRT;
  }

  /**
   * Returns the render target holding the front lightmap.
   *
   * @return the front lightmap target
   */
  public RenderTarget frontLightmap() {
    return frontLightRT;
  }

  /**
   * Returns the sampler used when sampling the lightmaps.
   *
   * @return the lightmap sampler
   */
  public Sampler sampler() {
    return lmSampler;
  }

  /**
   * Renders both lightmap layers into their targets.
   *
   * <p>Must be called on the render thread after the engine's front buffer is complete.
   * The retained meshes are rebuilt only when {@link LightEngine#lightVersion()}
   * changed.
   *
   * @param g      the batched renderer to draw with
   * @param cam    the camera defining the visible area
   * @param engine the engine whose front buffer is rendered
   */
  public void render(BatchedGraphics2D g, Camera2D cam, LightEngine engine) {
    try (Profiler.Scope _ = Profiler.scope("rendering:lightmap")) {
      Vector2 cp = cam.center();
      float vw = cam.width() / cam.zoom();
      float vh = cam.height() / cam.zoom();

      if (fullBright) {
        // one full-view rectangle per lightmap: no per-tile work at all
        g.begin(RenderPass.of(frontLightRT, Color.BLACK));
        g.setCamera(cam);
        g.setColor(Color.WHITE);
        g.drawRectangle(cp.x() - vw / 2F, cp.y() - vh / 2F, vw, vh);
        g.end();

        g.begin(RenderPass.of(wallLightRT, Color.BLACK));
        g.setCamera(cam);
        g.setColor(Color.WHITE);
        g.drawRectangle(cp.x() - vw / 2F, cp.y() - vh / 2F, vw, vh);
        g.end();
        return;
      }

      // the meshes are rebuilt only when the light data or the window
      // changed; a static frame is two draw calls, zero per-tile work
      long version = engine.lightVersion();
      if (lastVersion != version && dev != null) {
        rebuildLightMeshes(engine);
        lastVersion = version;
      }

      // front lightmap: blocks, sky, entities — full brightness
      g.begin(RenderPass.of(frontLightRT, Color.BLACK));
      g.setCamera(cam);
      if (frontLightMesh != null) {
        g.drawMesh(frontLightMesh);
      }
      g.end();

      // wall lightmap: every wall tile — baked WALL_MULTIPLIER
      g.begin(RenderPass.of(wallLightRT, Color.BLACK));
      g.setCamera(cam);
      if (wallLightMesh != null) {
        g.drawMesh(wallLightMesh);
      }
      g.end();
    }
  }

  /**
   * Rebuilds the retained lightmap meshes from the engine's front buffer.
   *
   * <p>The meshes cover the whole light window, so the camera can move inside it without
   * redrawing; only a stale window (resize, recompute) triggers a rebuild.
   */
  private void rebuildLightMeshes(LightEngine engine) {
    assert dev != null;
    if (lmMeshG == null) {
      lmMeshG = new MeshGraphics2D(new ZeroCopyVertexStore(true), dev);
    }
    if (frontLightMesh != null) {
      frontLightMesh.close();
      frontLightMesh = null;
    }
    if (wallLightMesh != null) {
      wallLightMesh.close();
      wallLightMesh = null;
    }
    frontLightMesh = buildLightMesh(engine, false);
    wallLightMesh = buildLightMesh(engine, true);
  }

  /**
   * Builds one lightmap layer mesh (front or wall) from the front buffer, one colored
   * quad per tile over the whole window.
   */
  private Mesh buildLightMesh(LightEngine engine, boolean wall) {
    assert lmMeshG != null;
    assert dev != null;

    float[] buf = engine.buffer();
    QuadGradient tg = new QuadGradient();
    lmMeshG.begin();
    lmMeshG.setColor(Color.WHITE);
    for (int y = engine.frontOriginY(); y < engine.frontOriginY() + engine.sizeY(); y++) {
      for (int x = engine.frontOriginX(); x < engine.frontOriginX() + engine.sizeX(); x++) {
        int o = engine.bufferIndex(x, y);
        if (o < 0) {
          continue;
        }
        float m = 1F;
        if (wall) {
          // the wall lightmap bakes in WALL_MULTIPLIER only on wall tiles
          m = level.getWall(x, y) == BlockState.EMPTY ? 1F : LightEngine.WALL_MULTIPLIER;
        }
        tg.setColors(
            Color.pack(engine.r(buf, o, 0) * m, engine.g(buf, o, 0) * m, engine.b(buf, o, 0) * m, 1F),
            Color.pack(engine.r(buf, o, 1) * m, engine.g(buf, o, 1) * m, engine.b(buf, o, 1) * m, 1F),
            Color.pack(engine.r(buf, o, 2) * m, engine.g(buf, o, 2) * m, engine.b(buf, o, 2) * m, 1F),
            Color.pack(engine.r(buf, o, 3) * m, engine.g(buf, o, 3) * m, engine.b(buf, o, 3) * m, 1F)
        );
        lmMeshG.setGradient(tg);
        lmMeshG.drawRectangle(x, y, 1, 1);
      }
    }
    Mesh m = lmMeshG.bake(dev);
    lmMeshG.end(); // end() clears the drafts — bake must consume them first
    return m;
  }

  @Override
  public void close() {
    if (wallLightRT != null) {
      wallLightRT.close();
    }
    if (frontLightRT != null) {
      frontLightRT.close();
    }
    if (lmSampler != null) {
      lmSampler.close();
    }
    if (frontLightMesh != null) {
      frontLightMesh.close();
    }
    if (wallLightMesh != null) {
      wallLightMesh.close();
    }
    if (lmMeshG != null) {
      lmMeshG.close();
    }
  }
}
