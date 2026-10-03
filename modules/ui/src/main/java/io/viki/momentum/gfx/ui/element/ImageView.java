package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Displays a drawable image inside an element bounds.
 *
 * <p>The view borrows the drawable and never closes or otherwise manages its resources. The
 * element is mutable and is intended for use from one owning UI thread.
 */
public final class ImageView extends Element {
  private Drawable2D image;

  /**
   * Creates an image view backed by an arbitrary drawable.
   *
   * @param bounds local destination bounds
   * @param image drawable to display
   */
  public ImageView(Rectangle bounds, Drawable2D image) {
    super(bounds);
    this.image = image;
  }

  /**
   * Creates an image view that displays the complete texture.
   *
   * @param bounds local destination bounds
   * @param image texture to display
   */
  public ImageView(Rectangle bounds, Texture image) {
    this(bounds, new TexturePart(image));
  }

  /**
   * Returns the drawable currently displayed by this view.
   *
   * @return current borrowed drawable
   */
  public Drawable2D image() {
    return image;
  }

  /**
   * Replaces the drawable displayed by this view.
   *
   * @param value new borrowed drawable
   */
  public void setImage(Drawable2D value) {
    image = value;
  }

  /**
   * Replaces the displayed image with a complete texture.
   *
   * @param value texture to display
   */
  public void setImage(Texture value) {
    setImage(new TexturePart(value));
  }
}
