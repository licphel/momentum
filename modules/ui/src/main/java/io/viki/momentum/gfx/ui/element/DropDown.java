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

package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.DropDownOptionRenderer;
import io.viki.momentum.gfx.ui.render.DropDownPopupRenderer;
import io.viki.momentum.gfx.ui.render.DropDownRenderer;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Represents a single-selection drop-down menu with an optional scrolling popup.
 *
 * <p>The header owns selection and focus state, while the popup exposes each option as a child
 * element and keeps its scrollbar separate from option clipping. The menu is mutable and should be
 * used from one UI thread.
 */
public final class DropDown extends Element {
  private static final int DEFAULT_SCROLLBAR_MINIMUM_ENTRIES = 8;

  private final List<String> options;
  private final List<OptionPart> optionParts = new ArrayList<>();
  private final Popup popup;
  private int selectedIndex;
  private boolean expanded;
  private boolean enabled = true;
  private boolean hovered;
  private boolean focused;
  private int dropCount = DEFAULT_SCROLLBAR_MINIMUM_ENTRIES;
  private float scrollbarThickness = 4.0F;
  private float scrollbarGap = 2.0F;
  private @Nullable IntConsumer onSelectionChanged;

  /**
   * Creates a drop-down menu from immutable option text.
   *
   * @param bounds the header's local bounds
   * @param options the non-empty option list
   * @param selectedIndex the initial selected option index
   * @throws IllegalArgumentException if {@code options} is empty
   * @throws IndexOutOfBoundsException if {@code selectedIndex} is outside the option list
   */
  public DropDown(Rectangle bounds, List<String> options, int selectedIndex) {
    super(bounds);
    if (options.isEmpty()) {
      throw new IllegalArgumentException("Drop-down requires at least one option");
    }
    if (selectedIndex < 0 || selectedIndex >= options.size()) {
      throw new IndexOutOfBoundsException("Selected option index out of range: " + selectedIndex);
    }
    this.options = List.copyOf(options);
    this.selectedIndex = selectedIndex;
    popup = new Popup();
    addPart(popup);
    rebuildParts();
    updatePopupLayout();
  }

  /**
   * Returns the immutable option list displayed by this menu.
   *
   * @return options in their selection order
   */
  public List<String> options() {
    return options;
  }

  /**
   * Returns the index of the selected option.
   *
   * @return selected option index
   */
  public int selectedIndex() {
    return selectedIndex;
  }

  /**
   * Returns the text of the selected option.
   *
   * @return selected option text
   */
  public String selectedOption() {
    return options.get(selectedIndex);
  }

  /**
   * Changes the selected option without notifying the selection listener.
   *
   * @param value the option index
   * @throws IndexOutOfBoundsException if {@code value} is outside the option list
   */
  public void setSelectedIndex(int value) {
    select(value, false);
  }

  /**
   * Reports whether the options popup is visible.
   *
   * @return whether the drop-down is expanded
   */
  public boolean expanded() {
    return expanded;
  }

  /**
   * Reports whether the pointer is currently over the header.
   *
   * @return whether the header is hovered
   */
  public boolean hovered() {
    return hovered;
  }

  /**
   * Reports whether the menu currently owns keyboard focus.
   *
   * @return whether the menu is focused
   */
  public boolean focused() {
    return focused;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return DropDownRenderer.INSTANCE;
  }

  /**
   * Returns the scrollbar used by the expanded popup.
   *
   * @return popup scrollbar, which may be hidden when all options fit
   */
  public ScrollBar scrollBar() {
    return popup.scrollBar;
  }

  /**
   * Expands or collapses the popup, subject to the enabled state.
   *
   * @param value whether the popup should be expanded
   */
  public void setExpanded(boolean value) {
    expanded = enabled && value;
    popup.setVisible(expanded);
    updatePopupLayout();
    if (expanded) {
      popup.ensureVisible(selectedIndex);
    }
  }

  /**
   * Reports whether this menu accepts pointer and keyboard input.
   *
   * @return whether the menu is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables the menu.
   *
   * @param value whether input should be accepted
   */
  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      setExpanded(false);
      hovered = false;
    }
  }

  /**
   * Installs or removes the selection callback.
   *
   * @param value the callback, or {@code null} to remove it
   */
  public void setOnSelectionChanged(@Nullable IntConsumer value) {
    onSelectionChanged = value;
  }

  /**
   * Returns the count of visible entries used to size the popup.
   *
   * @return visible entry count
   */
  public int dropCount() {
    return dropCount;
  }

  /**
   * Sets the count of visible entries in the popup.
   *
   * @param value the positive entry count
   * @throws IllegalArgumentException if {@code value} is less than one
   */
  public void setDropCount(int value) {
    if (value < 1) {
      throw new IllegalArgumentException("Drop count must be positive: " + value);
    }
    dropCount = value;
    updatePopupLayout();
  }

  /**
   * Returns the popup scrollbar thickness.
   *
   * @return scrollbar thickness in logical units
   */
  public float scrollbarThickness() {
    return scrollbarThickness;
  }

  /**
   * Sets popup scrollbar thickness.
   *
   * @param value the finite, non-negative thickness
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setScrollbarThickness(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Drop-down scrollbar thickness must be finite and non-negative: "
          + value);
    }
    scrollbarThickness = value;
    updatePopupLayout();
  }

  /**
   * Returns the gap between popup entries and the scrollbar.
   *
   * @return popup scrollbar gap in logical units
   */
  public float scrollbarGap() {
    return scrollbarGap;
  }

  /**
   * Sets the gap between popup entries and its scrollbar.
   *
   * @param value the finite, non-negative gap
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setScrollbarGap(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Drop-down scrollbar gap must be finite and non-negative: "
          + value);
    }
    scrollbarGap = value;
    updatePopupLayout();
  }

  @Override
  public void setBounds(Rectangle value) {
    super.setBounds(value);
    updatePopupLayout();
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button != KeyCode.MOUSE_LEFT) {
      return false;
    }
    if (action == KeyAction.RELEASE && containsLocal(x, y)) {
      setExpanded(!expanded);
    }
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled || action == KeyAction.RELEASE) {
      return false;
    }
    if (key == KeyCode.ENTER || key == KeyCode.KP_ENTER || key == KeyCode.SPACE) {
      if (action == KeyAction.PRESS) {
        setExpanded(!expanded);
      }
      return true;
    }
    if (key == KeyCode.ESCAPE && expanded) {
      setExpanded(false);
      return true;
    }
    if (key == KeyCode.UP) {
      select(Math.max(0, selectedIndex - 1), true);
      return true;
    }
    if (key == KeyCode.DOWN) {
      select(Math.min(options.size() - 1, selectedIndex + 1), true);
      return true;
    }
    return false;
  }

  @Override
  public void onPointerEnter() {
    hovered = true;
  }

  @Override
  public void onPointerExit() {
    hovered = false;
  }

  @Override
  public boolean acceptsFocus() {
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean focused) {
    this.focused = focused;
    if (!focused) {
      setExpanded(false);
    }
  }

  private void select(int index, boolean notify) {
    if (index < 0 || index >= options.size()) {
      throw new IndexOutOfBoundsException("Selected option index out of range: " + index);
    }
    if (selectedIndex == index) {
      return;
    }
    selectedIndex = index;
    if (expanded) {
      popup.ensureVisible(index);
    }
    if (notify && onSelectionChanged != null) {
      onSelectionChanged.accept(index);
    }
  }

  private void rebuildParts() {
    for (int i = 0; i < options.size(); i++) {
      OptionPart part = new OptionPart(i);
      optionParts.add(part);
      popup.addChild(part);
    }
  }

  private void updatePopupLayout() {
    if (popup != null) {
      popup.updateLayout();
    }
  }

  private int visibleEntryCount() {
    return Math.min(options.size(), dropCount);
  }

  private float rowHeight() {
    return Math.max(1.0F, bounds().height());
  }

  /**
   * Represents the popup viewport owned by one drop-down menu.
   *
   * <p>The popup contains option parts and a separate scrollbar, and is visible only while the
   * owning menu is expanded.
   */
  public final class Popup extends Element {
    private final ScrollBar scrollBar;

    private Popup() {
      super(Rectangle.ZERO);
      scrollBar = new ScrollBar(Rectangle.ZERO, ScrollBar.Orientation.VERTICAL);
      // The popup keeps the dropdown focused while its scrollbar captures the drag.
      scrollBar.setFocusable(false);
      scrollBar.setOnChanged(value -> updateLayout());
      addChild(scrollBar);
      setVisible(false);
    }

    @Override
    protected ElementRenderer defaultRenderer() {
      return DropDownPopupRenderer.INSTANCE;
    }

    /**
     * Returns the menu that owns this popup.
     *
     * @return owning drop-down menu
     */
    public DropDown owner() {
      return DropDown.this;
    }

    @Override
    protected Rectangle childrenClip(Rectangle area) {
      return area;
    }

    @Override
    public boolean onMouseMove(float x, float y) {
      return expanded;
    }

    @Override
    public boolean onScroll(float x, float y, double deltaX, double deltaY) {
      double delta = deltaY != 0.0 ? deltaY : deltaX;
      if (!scrollBar.visible() || delta == 0.0) {
        return false;
      }
      scrollBar.setValue(scrollBar.value() - Math.copySign(scrollBar.step(), delta));
      updateLayout();
      return true;
    }

    private void updateLayout() {
      float height = rowHeight();
      int entries = visibleEntryCount();
      float popupHeight = height * entries;
      boolean scrollable = options.size() > entries;
      float thickness = scrollbarThickness();
      float gap = scrollbarGap();
      float popupWidth = DropDown.this.bounds().width();
      float contentWidth = Math.max(0.0F, popupWidth
          - (scrollable ? thickness + gap : 0.0F));

      super.setBounds(Rectangle.of(0.0F, height, popupWidth, popupHeight));
      scrollBar.setVisible(expanded && scrollable);
      scrollBar.setBounds(Rectangle.of(contentWidth + gap, 0.0F, thickness, popupHeight));
      scrollBar.setRange(0.0, Math.max(0.0, height * options.size() - popupHeight), popupHeight);
      scrollBar.setStep(height);

      float offset = (float) scrollBar.value();
      for (int i = 0; i < optionParts.size(); i++) {
        float y = i * height - offset;
        OptionPart part = optionParts.get(i);
        part.setBounds(Rectangle.of(0.0F, y, contentWidth, height));
        part.setVisible(expanded && y + height > 0.0F && y < popupHeight);
      }
    }

    private void ensureVisible(int index) {
      if (!scrollBar.visible()) {
        return;
      }
      float height = rowHeight();
      float top = index * height;
      float bottom = top + height;
      float offset = (float) scrollBar.value();
      if (top < offset) {
        scrollBar.setValue(top);
      } else if (bottom > offset + bounds().height()) {
        scrollBar.setValue(bottom - bounds().height());
      }
    }
  }

  /**
   * Represents one visible option row inside {@link Popup}.
   *
   * <p>The part delegates selection changes to its owning menu while exposing row-local hover
   * state to the renderer.
   */
  public final class OptionPart extends Element {
    private final int index;
    private boolean optionHovered;
    private boolean pressed;

    private OptionPart(int index) {
      super(Rectangle.ZERO);
      this.index = index;
    }

    @Override
    protected ElementRenderer defaultRenderer() {
      return DropDownOptionRenderer.INSTANCE;
    }

    /**
     * Returns the menu that owns this option row.
     *
     * @return owning drop-down menu
     */
    public DropDown owner() {
      return DropDown.this;
    }

    /**
     * Returns this option's immutable index in the menu option list.
     *
     * @return option index
     */
    public int index() {
      return index;
    }

    /**
     * Reports whether the pointer is currently over this option row.
     *
     * @return whether this option is hovered
     */
    public boolean optionHovered() {
      return optionHovered;
    }

    @Override
    public boolean onMouseMove(float x, float y) {
      return enabled && expanded;
    }

    @Override
    public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action,
                                 int modifiers) {
      if (!enabled || !expanded || button != KeyCode.MOUSE_LEFT) {
        return false;
      }
      if (action == KeyAction.PRESS) {
        pressed = true;
      } else if (action == KeyAction.RELEASE && pressed) {
        pressed = false;
        if (containsLocal(x, y)) {
          select(index, true);
          setExpanded(false);
        }
      }
      return true;
    }

    @Override
    public void onPointerEnter() {
      optionHovered = true;
    }

    @Override
    public void onPointerExit() {
      optionHovered = false;
    }

    @Override
    public void onPointerCancel(KeyCode button) {
      pressed = false;
    }
  }
}
