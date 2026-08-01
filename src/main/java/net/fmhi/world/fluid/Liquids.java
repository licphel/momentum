package net.fmhi.world.fluid;

import net.fmhi.Registries;
import net.fmhi.math.Color;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.LightBuffer;
import net.fmhi.world.util.BlockPos;
import org.jspecify.annotations.NullMarked;

/**
 * Built-in liquid types. The index in {@link #ALL} is the id stored per
 * tile, so id 0 is always the empty liquid.
 */
@NullMarked
public final class Liquids {

  public static final Liquid EMPTY = new Liquid((byte) 0) {
    @Override
    public String name() {
      return "empty";
    }

    @Override
    public Color color() {
      return new Color(0, 0, 0, 0);
    }
  };

  public static final Liquid WATER = new Liquid((byte) 1) {
    @Override
    public String name() {
      return "water";
    }

    @Override
    public Color color() {
      return new Color(0.2F, 0.4F, 0.9F, 0.85F);
    }

    @Override
    public float viscosity() {
      return 1F;
    }

    @Override
    public float density() {
      return 1F;
    }

    @Override
    public float temperature() {
      return 20F;
    }
  };

  public static final Liquid LAVA = new Liquid((byte) 2) {
    @Override
    public String name() {
      return "lava";
    }

    @Override
    public Color color() {
      return new Color(1.0F, 0.35F, 0.05F, 0.95F);
    }

    @Override
    public float viscosity() {
      return 3F;
    }

    @Override
    public float density() {
      return 2F;
    }

    @Override
    public float temperature() {
      return 1000F;
    }

    @Override
    public boolean getLight(LightBuffer buf) {
      buf.r(0.9F);
      buf.g(0.25F);
      buf.b(0.05F);
      return true;
    }

    @Override
    public void onTouch(LiquidStack src, LiquidStack dst, Level level, int x, int y, int nx, int ny) {
      // Starbound liquid interaction: when enough non-lava liquid surrounds
      // the lava tile, the lava solidifies into stone and the water is
      // consumed
      int sum = 0;
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if ((dx == 0) == (dy == 0)) continue;
          LiquidStack ns = level.getLiquidStack(x + dx, y + dy);
          if (ns.level() > 0 && ns.liquid() != Liquids.LAVA) sum += ns.level();
        }
      }
      if (sum < FluidEngine.LAVA_REACT_AMOUNT) return;
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if ((dx == 0) == (dy == 0)) continue;
          LiquidStack ns = level.getLiquidStack(x + dx, y + dy);
          if (ns.level() > 0 && ns.liquid() != Liquids.LAVA) {
            level.setLiquid(x + dx, y + dy, Liquids.EMPTY, 0);
          }
        }
      }
      level.setLiquid(x, y, Liquids.EMPTY, 0);
      level.setBlock(new BlockPos(x, y), Registries.STONE.defaultState());
    }
  };

  public static final Liquid POISON = new Liquid((byte) 3) {
    @Override
    public String name() {
      return "poison";
    }

    @Override
    public Color color() {
      return new Color(0.5F, 0.8F, 0.2F, 0.85F);
    }

    @Override
    public float viscosity() {
      return 1.2F;
    }

    @Override
    public float density() {
      return 1.1F;
    }

    @Override
    public float temperature() {
      return 30F;
    }
  };

  /** Lookup table: index = liquid id. */
  public static final Liquid[] ALL = {EMPTY, WATER, LAVA, POISON};

  /** Returns the liquid with the given id, or {@link #EMPTY}. */
  public static Liquid byId(int id) {
    return id >= 0 && id < ALL.length ? ALL[id] : EMPTY;
  }

  private Liquids() {
  }
}
