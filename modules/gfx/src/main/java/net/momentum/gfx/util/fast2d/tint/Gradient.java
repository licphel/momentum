package net.momentum.gfx.util.fast2d.tint;

/**
 * Supplies a color per vertex, enabling per-vertex tinting of drawn content.
 *
 * <p>Vertex builders query the gradient once per vertex, using the vertex index to select
 * the color. The single abstract method makes this interface directly usable as a lambda.
 */
@FunctionalInterface
public interface Gradient {
  /**
   * Returns the color for the given vertex index.
   *
   * @param vertexIndex the zero-based vertex index
   * @return the packed color of that vertex
   */
  long getColor(int vertexIndex);
}
