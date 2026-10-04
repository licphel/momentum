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

package io.viki.momentum.gfx.ui.look;

import io.viki.momentum.gfx.ui.Look;
import io.viki.momentum.gfx.ui.element.*;
import io.viki.momentum.gfx.ui.look.auto.*;

/**
 * Provides the shared default visual policy for standard UI elements.
 *
 * <p>The returned registry is mutable so an application can extend the default appearance. Its
 * configuration and use are intended to remain on the owning UI thread.
 */
public final class AutoLook {
  private static final Look INSTANCE = create();

  private AutoLook() {
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
