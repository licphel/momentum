package net.fmhi.gfx.pipe;

/**
 * A simple scissor object.
 *
 * @param x      scissor x
 * @param y      scissor y
 * @param width  scissor width
 * @param height scissor height
 * @param enable whether the scissor test is enabled
 */
public record Scissor(int x, int y, int width, int height, boolean enable) {
  public static final Scissor DISABLED = new Scissor(0, 0, 0, 0, false);
}
