package net.fmhi.gfx;

import java.util.concurrent.atomic.LongAdder;

public class GfxStats {
  public static final LongAdder DCPT = new LongAdder();
  public static final LongAdder ECMDPT = new LongAdder();
  public static final LongAdder DCMDPT = new LongAdder();
  private static long lastProfile;

  public static void profile() {
    if (System.currentTimeMillis() - lastProfile > 1000) {
      System.out.println("=".repeat(20));
      System.out.println("DCPT: " + DCPT.sum());
      System.out.println("ECMDPT: " + ECMDPT.sum());
      System.out.println("DCMDPT: " + DCMDPT.sum());
      System.out.println();
      lastProfile = System.currentTimeMillis();
    }

    DCPT.reset();
    ECMDPT.reset();
    DCMDPT.reset();
  }
}
