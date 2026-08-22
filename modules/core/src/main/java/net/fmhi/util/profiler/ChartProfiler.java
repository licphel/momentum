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

package net.fmhi.util.profiler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The profiling-enabled {@link Profiler} implementation.
 *
 * <p>Records per-section aggregate statistics and a bounded time series of recent
 * durations, and on {@link Profiler#dump()} prints a summary table and writes one
 * duration-curve PNG chart per section into the configured output directory.
 */
final class ChartProfiler implements Profiler {
  static final ChartProfiler INSTANCE = new ChartProfiler();
  private static final double NS_TO_MS = 1.0 / 1_000_000.0;
  /** Maximum number of samples retained per section. */
  private static final int SERIES_CAPACITY = 1024;
  /** Line colors for the PNG charts, picked by a stable hash of the section name. */
  private static final Color[] PALETTE = {
      new Color(0xE0, 0x1B, 0x24),
      new Color(0x1F, 0x77, 0xB4),
      new Color(0x2C, 0xA0, 0x2C),
      new Color(0xFF, 0x7F, 0x0E),
      new Color(0x94, 0x63, 0x7E),
      new Color(0x17, 0xBE, 0xCF),
      new Color(0xBE, 0x57, 0x2F),
      new Color(0x8C, 0x8C, 0x8C),
  };
  /** Nanosecond clock at creation, used to skip recording during the warm-up. */
  private final long bootNanos = System.nanoTime();
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  /** Per-thread LIFO stack of open sections. */
  private final ThreadLocal<Deque<Frame>> stack = ThreadLocal.withInitial(ArrayDeque::new);

  ChartProfiler() {
  }

  /** Writes one duration-curve PNG chart per section into the output directory. */
  private static void renderCharts(ArrayList<Map.Entry<String, Entry>> list) {
    Path dir = Profiler.outputPath();
    try {
      Files.createDirectories(dir);
      int written = 0;
      for (var e : list) {
        if (writeSectionPng(e.getKey(), e.getValue(), dir.resolve(sanitize(e.getKey()) + ".png"))) {
          written++;
        }
      }
      if (written > 0) {
        System.out.println("[Profiler] charts -> " + dir.toAbsolutePath() + " (" + written + " sections)");
      }
    } catch (IOException e) {
      System.err.println("[Profiler] failed to write charts to " + dir + ": " + e.getMessage());
    }
  }

  /**
   * Draws a section's recent-duration curve as a PNG.
   *
   * @param name  the section name, used as the chart title and line color
   * @param entry the recorded statistics for the section
   * @param file  the PNG file to write
   * @return {@code false} if the section has fewer than two samples, {@code true} otherwise
   * @throws IOException if the PNG file cannot be written
   */
  private static boolean writeSectionPng(String name, Entry entry, Path file) throws IOException {
    long[] window = entry.window();
    if (window.length < 2) {
      return false;
    }
    int n = window.length;
    int width = 900;
    int height = 420;
    int left = 70;
    int right = 40;
    int top = 60;
    int bottom = 40;
    int plotW = width - left - right;
    int plotH = height - top - bottom;

    double maxMs = 0;
    for (long v : window) {
      maxMs = Math.max(maxMs, v * NS_TO_MS);
    }
    maxMs = Math.max(maxMs, 0.5);
    double step = Math.pow(10, Math.floor(Math.log10(maxMs)));
    maxMs = Math.ceil(maxMs / step) * step;

    Color line = PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];

    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, height);

      // title + summary line
      g.setColor(Color.BLACK);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
      g.drawString(name, left, 26);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
      g.drawString(String.format("calls %d   avg %.2f ms   max %.2f ms   p99 %.2f ms   stdev %.2f ms",
              entry.count, entry.avgMs(), entry.maxMs(), entry.percentileMs(0.99), entry.stddevMs()),
          left, 44);

      // horizontal grid
      g.setColor(new Color(0xE0, 0xE0, 0xE0));
      for (int i = 0; i <= 4; i++) {
        int y = top + plotH - (int) Math.round((double) i / 4 * plotH);
        g.drawLine(left, y, left + plotW, y);
      }

      // curve + translucent fill under it so spikes stand out
      int[] xs = new int[n];
      int[] ys = new int[n];
      for (int i = 0; i < n; i++) {
        xs[i] = left + (int) Math.round((double) i / (n - 1) * plotW);
        ys[i] = top + plotH - (int) Math.round(Math.min(window[i] * NS_TO_MS, maxMs) / maxMs * plotH);
      }
      int plotBottom = top + plotH;
      int[] fillXs = new int[n + 2];
      int[] fillYs = new int[n + 2];
      fillXs[0] = xs[0];
      fillYs[0] = plotBottom;
      System.arraycopy(xs, 0, fillXs, 1, n);
      System.arraycopy(ys, 0, fillYs, 1, n);
      fillXs[n + 1] = xs[n - 1];
      fillYs[n + 1] = plotBottom;
      g.setColor(new Color(line.getRed(), line.getGreen(), line.getBlue(), 40));
      g.fillPolygon(fillXs, fillYs, n + 2);

      // axes + tick labels
      g.setColor(Color.BLACK);
      g.drawRect(left, top, plotW, plotH);
      for (int i = 0; i <= 4; i++) {
        int y = top + plotH - (int) Math.round((double) i / 4 * plotH);
        g.drawString(fmtMs(maxMs * i / 4), left - 58, y + 4);
      }
      g.drawString("sample index", left + plotW / 2 - 30, height - 10);

      g.setColor(line);
      g.drawPolyline(xs, ys, n);
    } finally {
      g.dispose();
    }
    ImageIO.write(img, "png", file.toFile());
    return true;
  }

  /** Replaces characters that are illegal in file names with {@code _}. */
  private static String sanitize(String name) {
    StringBuilder sb = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c < 0x20 || c == ':' || c == '\\' || c == '/' || c == '*' || c == '?'
          || c == '"' || c == '<' || c == '>' || c == '|') {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String fmtMs(double v) {
    return v >= 1 ? String.format("%.1f", v) : String.format("%.2f", v);
  }

  @Override
  public void start(String name) {
    stack.get().push(new Frame(name, System.nanoTime()));
  }

  @Override
  public void end(String name) {
    Deque<Frame> st = stack.get();
    if (st.isEmpty()) {
      return;
    }
    Frame top = st.pop();
    if (!top.name.equals(name)) {
      return;
    }
    long now = System.nanoTime();
    if (now - bootNanos < ProfilerConfig.warmupNanos) {
      return; // ignore the startup phase, dominated by JIT/resource loading
    }
    long elapsed = now - top.start;
    entries.compute(name, (k, e) -> (e == null ? new Entry() : e).record(elapsed));
  }

  @Override
  public void dump() {
    var list = sortedEntries();
    if (list.isEmpty()) {
      System.out.println("[Profiler] no data");
      return;
    }

    System.out.println("[Profiler]");
    System.out.printf("  %-40s %8s %8s %8s %8s %9s %8s %8s %8s %10s%n",
        "section", "calls", "min ms", "avg ms", "max ms", "stdev ms", "p50", "p90", "p99", "total ms");
    System.out.println("  " + "-".repeat(118));
    for (var e : list) {
      Entry entry = e.getValue();
      System.out.printf("  %-40s %8d %8.3f %8.3f %8.3f %9.3f %8.3f %8.3f %8.3f %10.3f%n",
          e.getKey(), entry.count, entry.minMs(), entry.avgMs(), entry.maxMs(),
          entry.stddevMs(), entry.percentileMs(0.50), entry.percentileMs(0.90),
          entry.percentileMs(0.99), entry.totalMs());
    }
    System.out.println();
    renderCharts(list);
  }

  @Override
  public void reset() {
    entries.clear();
  }

  /** Returns the recorded sections sorted by total time, most expensive first. */
  private ArrayList<Map.Entry<String, Entry>> sortedEntries() {
    var list = new ArrayList<>(entries.entrySet());
    list.sort(Comparator.<Map.Entry<String, Entry>>comparingLong(e -> e.getValue().totalNs).reversed());
    return list;
  }

  /** An open section: its name and the time at which it was started. */
  private record Frame(String name, long start) {
  }

  /** Aggregated statistics and a bounded time series for a single section. */
  private static final class Entry {
    final long[] series = new long[SERIES_CAPACITY];
    long count;
    long totalNs;
    long minNs = Long.MAX_VALUE;
    long maxNs = Long.MIN_VALUE;
    double mean;
    double m2;
    int head;
    int size;

    /**
     * Records one sample, updating the running statistics and the time series.
     *
     * @param ns the elapsed time of one section run, in nanoseconds
     * @return this entry
     */
    synchronized Entry record(long ns) {
      count++;
      totalNs += ns;
      if (ns < minNs) {
        minNs = ns;
      }
      if (ns > maxNs) {
        maxNs = ns;
      }
      double delta = ns - mean;
      mean += delta / count;
      m2 += delta * (ns - mean);
      series[head] = ns;
      head = (head + 1) % series.length;
      if (size < series.length) {
        size++;
      }
      return this;
    }

    /** Returns the recent samples in chronological order (oldest first). */
    synchronized long[] window() {
      if (size == 0) {
        return new long[0];
      }
      long[] out = new long[size];
      for (int i = 0; i < size; i++) {
        out[i] = series[(head - size + i + series.length) % series.length];
      }
      return out;
    }

    double minMs() {
      return minNs * NS_TO_MS;
    }

    double maxMs() {
      return maxNs * NS_TO_MS;
    }

    double avgMs() {
      return count == 0 ? 0 : totalNs * NS_TO_MS / count;
    }

    double totalMs() {
      return totalNs * NS_TO_MS;
    }

    double stddevMs() {
      return count < 2 ? 0 : Math.sqrt(m2 / count) * NS_TO_MS;
    }

    /**
     * Returns a nearest-rank percentile over the recent samples.
     *
     * @param p the percentile in {@code (0, 1]}, e.g. {@code 0.99} for the 99th percentile
     * @return the percentile in milliseconds, or {@code 0} if no samples exist
     */
    synchronized double percentileMs(double p) {
      long[] w = window();
      if (w.length == 0) {
        return 0;
      }
      long[] sorted = w.clone();
      Arrays.sort(sorted);
      int idx = (int) Math.ceil(p * sorted.length) - 1;
      return sorted[Math.max(idx, 0)] * NS_TO_MS;
    }
  }
}
