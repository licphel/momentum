package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.*;
import io.viki.momentum.gfx.ui.render.*;

/**
 * Provides the shared default visual policy for standard UI elements.
 *
 * <p>The returned registry is mutable so an application can extend the default appearance. Its
 * configuration and use are intended to remain on the owning UI thread.
 */
public final class DefaultLook {
  private static final Look INSTANCE = create();

  private DefaultLook() {
  }

  /**
   * Returns the process-wide default visual policy.
   *
   * @return shared default look
   */
  public static Look get() {
    return INSTANCE;
  }

  private static Look create() {
    return new Look()
        .register(Panel.class, EmptyRenderer.INSTANCE)
        .register(Group.class, EmptyRenderer.INSTANCE)
        .register(Canvas.class, EmptyRenderer.INSTANCE)
        .register(Button.class, ButtonRenderer.INSTANCE)
        .register(CheckBox.class, CheckBoxRenderer.INSTANCE)
        .register(Slider.class, SliderRenderer.INSTANCE)
        .register(ScrollBar.class, ScrollBarRenderer.INSTANCE)
        .register(ScrollPane.class, ScrollPaneRenderer.INSTANCE)
        .register(Window.class, WindowRenderer.INSTANCE)
        .register(DropDown.class, DropDownRenderer.INSTANCE)
        .register(DropDown.Popup.class, DropDownPopupRenderer.INSTANCE)
        .register(DropDown.OptionPart.class, DropDownOptionRenderer.INSTANCE)
        .register(TextBox.class, TextBoxRenderer.INSTANCE)
        .register(TextView.class, TextViewRenderer.INSTANCE)
        .register(ImageView.class, ImageViewRenderer.INSTANCE);
  }
}
