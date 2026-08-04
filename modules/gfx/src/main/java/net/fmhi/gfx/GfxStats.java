package net.fmhi.gfx;

import java.util.concurrent.atomic.LongAdder;

public class GfxStats {
  public static final LongAdder DCPT = new LongAdder();
  public static final LongAdder ECMDPT = new LongAdder();
  public static final LongAdder DCMDPT = new LongAdder();

  public static void profile() {
    System.out.println("=".repeat(20));
    System.out.println("DCPT: " + DCPT.sum());
    System.out.println("ECMDPT: " + ECMDPT.sum());
    System.out.println("DCMDPT: " + DCMDPT.sum());
    System.out.println();

    DCPT.reset();
    ECMDPT.reset();
    DCMDPT.reset();
  }
}
