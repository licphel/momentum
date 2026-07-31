package net.fmhi.world.util;

public interface Locatable {
  /**
   * Returns the Manhattan distance to another block position.
   *
   * @param a first position
   * @param b second position
   * @return the distance
   */
  static double manhattanDistance(Locatable a, Locatable b) {
    return Math.sqrt(distanceSquared(a, b));
  }

  /**
   * Returns the Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the distance
   */
  static double distance(Locatable a, Locatable b) {
    return Math.sqrt(distanceSquared(a, b));
  }

  /**
   * Returns the squared Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the squared distance
   */
  static double distanceSquared(Locatable a, Locatable b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    return dx * dx + dy * dy;
  }

  double getX();

  double getY();
}
