package net.fmhi.gfx.brush.tint;

import net.fmhi.math.Color;

/**
 * Gradient that applies the same color to every vertex.
 *
 * <p>Mutable; not thread-safe. Each instance must be confined to a single thread.
 */
public class SimpleGradient implements Gradient {
  private long color;

  /**
   * Creates a white gradient.
   */
  public SimpleGradient() {
    this(Color.WHITE);
  }

  /**
   * Creates a gradient from a color.
   *
   * @param color the color applied to every vertex
   */
  public SimpleGradient(Color color) {
    this.color = color.pack();
  }

  /**
   * Creates a gradient from a packed color.
   *
   * @param color the packed color applied to every vertex
   */
  public SimpleGradient(long color) {
    this.color = color;
  }

  /**
   * Sets the color applied to every vertex.
   *
   * @param color the new color
   */
  public void setColor(Color color) {
    this.color = color.pack();
  }

  /**
   * Sets the color applied to every vertex.
   *
   * @param color the packed color
   */
  public void setColor(long color) {
    this.color = color;
  }

  /**
   * Returns the color applied to every vertex.
   *
   * @return the packed color
   */
  public long getColor() {
    return color;
  }

  @Override
  public long getColor(int vertexIndex) {
    return color;
  }
}
