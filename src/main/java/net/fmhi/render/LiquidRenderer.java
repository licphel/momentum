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

package net.fmhi.render;

import net.fmhi.Registries;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.io.PngInputStream;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.math.Color;
import net.fmhi.util.ResourceProvider;
import net.fmhi.util.Util;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.physics.VoxelBox;
import net.fmhi.world.physics.VoxelClip;
import net.fmhi.world.physics.VoxelOutline;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Liquid renderer ported from Enchant's {@code LiquidRenderer}: the body
 * is the liquid texture cropped to the surface height, with the texture
 * scrolling for animation and the surface wobbling when exposed; a thin
 * surface strip (the {@code *_edge} texture) is drawn on top where the
 * liquid is exposed. In slope tiles the body fills the gaps of the
 * staircase outline instead of covering the solid boxes.
 */
@NullMarked
public class LiquidRenderer {

  private final Map<Liquid, Texture> bodies = new HashMap<>();
  private final Map<Liquid, Texture> surfaces = new HashMap<>();

  private LiquidRenderer() {
  }

  /** Loads the liquid textures ({@code /liquid/{name}.png} and
   * {@code /liquid/{name}_edge.png}) for every registered liquid; returns
   * {@code null} if any texture is missing. */
  public static @Nullable LiquidRenderer create(Device dev) {
    ResourceProvider rp = ResourceProvider.classpath(LiquidRenderer.class);
    LiquidRenderer r = new LiquidRenderer();
    for (Liquid liq : new Liquid[]{Liquids.WATER, Liquids.LAVA}) {
      try {
        r.bodies.put(liq, Texture.loadRGBA8(dev,
            new PngInputStream(rp.openStream("/liquid/" + liq.name() + ".png")).info()));
        r.surfaces.put(liq, Texture.loadRGBA8(dev,
            new PngInputStream(rp.openStream("/liquid/" + liq.name() + "_edge.png")).info()));
      } catch (Exception e) {
        return null;
      }
    }
    return r;
  }

  /** Renders the visible liquids: body texture cropped to the surface,
   * filled into slope gaps, with the surface strip on top. */
  public void render(BatchedGraphics2D g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    float time = Util.frameTime();
    float uScroll = time * 5F;
    float vScroll = time * 2.5F;
    g.setColor(Color.WHITE);
    for (int cx = (int) Math.floor((cp.x() - vw / 2F) / cs); cx <= (int) Math.floor((cp.x() + vw / 2F) / cs); cx++)
      for (int cy = (int) Math.floor((cp.y() - vh / 2F) / cs); cy <= (int) Math.floor((cp.y() + vh / 2F) / cs); cy++) {
        Chunk ck = level.getChunk(new ChunkPos(cx, cy));
        if (ck == null) continue;
        for (int ly = 0; ly < cs; ly++)
          for (int lx = 0; lx < cs; lx++) {
            int wx = cx * cs + lx;
            int wy = cy * cs + ly;
            int lv = ck.getLiquidLevel(wx, wy);
            if (lv <= 0) continue;
            Liquid liq = Liquids.byId(ck.getLiquidType(wx, wy));
            Texture tex = bodies.get(liq);
            Texture edge = surfaces.get(liq);
            if (tex == null || edge == null) continue;

            float p = lv / (float) FluidEngine.FULL;
            BlockState up = level.getBlock(wx, wy + 1);
            int upLv = level.getLiquidLevel(wx, wy + 1);
            // exposed surface wobbles (Enchant)
            if (upLv == 0 && (p < 1F || !perfect(up)))
              p += 0.025F * (float) Math.sin(wx * 0.5F + wy * 0.05F + time * 3F);
            float h = Math.clamp(p, 0F, 1F);

            // falling liquid (nothing below): a small centered blob
            if (level.getLiquidLevel(wx, wy - 1) < FluidEngine.FULL
                && level.getBlock(wx, wy - 1).block() == Registries.AIR) {
              float half = (float) lv / FluidEngine.FULL / 2F;
              g.drawTexture(tex, wx + 0.5F - half, wy + 0.5F - half, half * 2F, half * 2F,
                  (uScroll + wx * 8F) % tex.width(), (vScroll + wy * 8F) % tex.height(), 8F, 8F);
              continue;
            }

            float u = uScroll + wx * 8F;
            float v = vScroll + wy * 8F + 8F * (1F - h);
            // body: in a slope tile fill the gaps of the staircase outline,
            // otherwise a plain rectangle cropped to the surface. UVs stay
            // inside the sheet (floating-point modulo), so scrolling keeps
            // sub-texel precision and never samples outside
            float tw = tex.width();
            float th = tex.height();
            float cropH = h * 8F;
            u = (u % tw + tw) % tw;
            v = (v % th + th) % th;

            BlockState self = level.getBlock(wx, wy);
            VoxelClip shape = self.getVoxelShape();
            if (shape instanceof VoxelOutline outline) {
              for (VoxelBox box : outline.boxes()) {
                float boxTop = box.y() + box.h();
                if (h <= boxTop) continue;
                g.drawTexture(tex, wx + box.x(), wy + boxTop, box.w(), h - boxTop,
                    u + box.x() * 8F, v, box.w() * 8F, (h - boxTop) * 8F);
              }
            } else {
              g.drawTexture(tex, wx, wy, 1F, h, u, v, 8F, cropH);
            }

            // surface strip (Enchant): where the liquid is exposed
            if (!perfect(up) && upLv == 0)
              g.drawTexture(edge, wx, wy + h - 1F / 8F, 1F, 1F / 8F,
                  uScroll % edge.width(), 0F, 8F, 1F);
          }
      }
  }

  private static boolean perfect(BlockState s) {
    return s != null && s.block() != Registries.AIR && s.shape() == net.fmhi.world.block.Shape.SOLID;
  }
}
