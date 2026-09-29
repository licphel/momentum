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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.MutableText;
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.text.raster.LayoutGlyph;
import io.viki.momentum.gfx.text.raster.LayoutRun;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.render.ButtonRenderer;
import io.viki.momentum.gfx.ui.render.CheckBoxRenderer;
import io.viki.momentum.gfx.ui.render.TextBoxRenderer;
import io.viki.momentum.gfx.ui.render.TextViewRenderer;
import io.viki.momentum.gfx.ui.element.*;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.InputModifiers;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.math.shape.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the behavior contracts of the standard UI controls.
 *
 * <p>The suite covers input state transitions, snapping, scrolling, text editing, tooltips,
 * window containment, and renderer exposure without requiring a live rendering loop.
 */
public final class StandardControlsTest {
  private static final TransformHandler HANDLER = new TransformHandler() {
    @Override
    public float u(Texture texture, float u) {
      return u / texture.width();
    }

    @Override
    public float v(Texture texture, float v) {
      return 1.0F - v / texture.height();
    }

    @Override
    public Matrix4x4 createOrthographic(float left, float right, float bottom, float top,
                                        float near, float far) {
      return Matrix4x4.createOrthographic(left, right, bottom, top, near, far);
    }

    @Override
    public Matrix4x4 createPerspective(float fovY, float aspect, float near, float far) {
      return Matrix4x4.createPerspective(fovY, aspect, near, far);
    }
  };

  @Test
  void numericSliderSnapsDragAndMovesOneStepPerWheelEvent() {
    Slider slider = new Slider(Rectangle.of(0, 0, 100, 20), 0.0, 10.0, 0.0);
    slider.setStep(2.0);

    slider.onMouseButton(34.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    assertEquals(4.0, slider.value());

    slider.onScroll(0.0F, 0.0F, 0.0, -12.0);
    assertEquals(2.0, slider.value());
    assertThrows(IllegalArgumentException.class, () -> slider.setStep(0.0));
  }

  @Test
  void optionSliderUsesDiscreteLabels() {
    Slider slider = new Slider(Rectangle.of(0, 0, 100, 20),
        List.of(Literal.of("Low"), Literal.of("Medium"), Literal.of("High")), 0);
    AtomicInteger selected = new AtomicInteger(-1);
    slider.setOnSelectionChanged(selected::set);

    slider.onScroll(0.0F, 0.0F, 0.0, 1.0);
    assertEquals("Medium", slider.selectedOption().text());
    assertEquals(1, selected.get());

    slider.onMouseButton(99.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    assertEquals("High", slider.selectedOption().text());
    assertThrows(IllegalStateException.class, () -> slider.setStep(2.0));
  }

  @Test
  void optionSliderClickWithoutDragKeepsItsSelectedLabelOnRelease() {
    Slider slider = new Slider(Rectangle.of(0, 0, 100, 20),
        List.of(Literal.of("Low"), Literal.of("Medium"), Literal.of("High")), 1);

    slider.onMouseButton(50.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    slider.onMouseButton(50.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 0);

    assertEquals("Medium", slider.selectedOption().text());
  }

  @Test
  void sliderFormatsBothNumericAndOptionValuesForItsInternalLabel() {
    Slider numeric = new Slider(Rectangle.of(0, 0, 100, 20), 0.0, 10.0, 5.0);
    Slider options = new Slider(Rectangle.of(0, 0, 100, 20),
        List.of(Literal.of("Low"), Literal.of("High")), 1);
    numeric.setValueFormatter(value -> Literal.of("Value: ").append(value));
    options.setValueFormatter(value -> Literal.of("Value: ").append(value));

    assertEquals("Value: 5.0", numeric.displayValue().text());
    assertEquals("Value: High", options.displayValue().text());
  }

  @Test
  void scrollPaneMovesArbitraryContentByStepAndPage() {
    Panel content = new Panel(Rectangle.of(0, 0, 400, 500));
    ScrollPane pane = new ScrollPane(Rectangle.of(0, 0, 100, 100), content);
    pane.verticalBar().setStep(20.0);

    assertTrue(pane.horizontalBar().visible());
    assertTrue(pane.verticalBar().visible());
    pane.onScroll(10.0F, 10.0F, 0.0, -4.0);
    assertEquals(-20.0F, content.bounds().minY());

    pane.onKey(KeyCode.PAGE_DOWN, KeyAction.PRESS, 0);
    assertEquals(-(20.0F + pane.viewportHeight()), content.bounds().minY(), 0.001F);

    pane.scrollToVisible(content, Rectangle.of(350, 450, 10, 10));
    assertTrue(pane.horizontalBar().value() > 0.0);
    assertTrue(pane.verticalBar().value() > 0.0);
  }

  @Test
  void scrollPaneUsesConfiguredGapBetweenViewportAndBars() {
    Panel content = new Panel(Rectangle.of(0, 0, 200, 200));
    ScrollPane pane = new ScrollPane(Rectangle.of(0, 0, 100, 100), content);
    pane.setBarThickness(10.0F);
    pane.setBarGap(3.0F);

    assertEquals(87.0F, pane.viewportWidth());
    assertEquals(87.0F, pane.viewportHeight());
    assertEquals(90.0F, pane.verticalBar().bounds().minX());
    assertEquals(90.0F, pane.horizontalBar().bounds().minY());
  }

  @Test
  void groupArrangesChildrenWithConfiguredPaddingAndGap() {
    Group group = new Group(Rectangle.of(0, 0, 100, 80), LinearLayout.vertical());
    group.setPadding(4.0F);
    group.setGap(3.0F);
    Panel first = new Panel(Rectangle.of(20, 20, 10, 12));
    Panel second = new Panel(Rectangle.of(30, 30, 15, 16));

    group.add(first);
    group.add(second);

    assertEquals(Rectangle.of(4, 4, 92, 12), first.bounds());
    assertEquals(Rectangle.of(4, 19, 92, 16), second.bounds());
    assertTrue(group instanceof Element);
  }

  @Test
  void wrappingTextBoxDisablesItsHorizontalScrollbar() {
    TextBox textBox = new TextBox(Rectangle.of(0, 0, 400, 500), "wrapped");
    ScrollPane pane = new ScrollPane(Rectangle.of(0, 0, 100, 100), textBox);

    assertFalse(pane.horizontalBar().visible());
    assertEquals(pane.viewportWidth(), textBox.bounds().width());

    textBox.setWrapText(false);
    assertTrue(pane.horizontalBar().visible());
    assertEquals(400.0F, textBox.bounds().width());
  }

  @Test
  void textRasterWrapsAndRepresentsTheTrailingEmptyLine() {
    TextFormat format = TextFormat.of().size(14.0F);
    Raster raster = new MutableText()
        .append(Literal.of("one two three four\n").with(format))
        .maxWidth(45.0F)
        .flipY(true)
        .raster();
    LayoutRun[] runs = raster.runs();

    assertTrue(runs.length > 2);
    assertEquals(raster.mergedText().length(), runs[runs.length - 1].textStart());
    assertEquals("", runs[runs.length - 1].text());
  }

  @Test
  void textRasterHitTestReturnsTheInsertionPointAtLineEnd() {
    LayoutGlyph[] glyphs = {
        new LayoutGlyph(0, 1, 0.0F, 8.0F, 10.0F, 0.0F, 0.0F,
            8.0F, 1.0F, 1, Color.WHITE, 0),
        new LayoutGlyph(1, 2, 10.0F, 8.0F, 10.0F, 0.0F, 0.0F,
            8.0F, 1.0F, 2, Color.WHITE, 0)
    };
    LayoutRun run = new LayoutRun("ab", 0, glyphs, 0.0F, 10.0F, 8.0F);
    Raster raster = new Raster(new Raster.Entry[0], new Raster.Stroke[0],
        Rectangle.of(0.0F, 0.0F, 20.0F, 10.0F), 20.0F, true,
        new LayoutRun[]{run}, "ab");

    assertEquals(1, raster.hitTest(11.0F, 5.0F));
    assertEquals(2, raster.hitTest(19.0F, 5.0F));
    assertEquals(2, raster.hitTest(25.0F, 5.0F));
  }

  @Test
  void textBoxMaintainsAutomaticTrailingNewline() {
    TextBox box = new TextBox(Rectangle.of(0, 0, 100, 40), "A");
    box.setAutomaticTrailingNewline(true);
    assertEquals("A\n", box.text());

    box.onCharacter('B');
    assertEquals("AB\n", box.text());
    box.onKey(KeyCode.BACKSPACE, KeyAction.PRESS, 0);
    assertEquals("A\n", box.text());
    box.setCursorIndex(1);
    box.onKey(KeyCode.DOWN, KeyAction.PRESS, 0);
    assertEquals(box.text().length(), box.cursorIndex());
    box.onCharacter('C');
    assertEquals("A\nC\n", box.text());
    assertThrows(IllegalStateException.class, () -> box.setMultiline(false));
  }

  @Test
  void textBoxSupportsBoundedUndoAndRedo() {
    TextBox box = new TextBox(Rectangle.of(0, 0, 100, 40), "A");
    assertEquals(128, box.historyLimit());

    box.onCharacter('B');
    box.onCharacter('C');
    assertEquals("ABC", box.text());
    assertTrue(box.undo());
    assertEquals("AB", box.text());
    assertTrue(box.undo());
    assertEquals("A", box.text());
    assertTrue(box.redo());
    assertEquals("AB", box.text());

    box.onKey(KeyCode.Z, KeyAction.PRESS, InputModifiers.CONTROL);
    assertEquals("A", box.text());
    box.onKey(KeyCode.Y, KeyAction.PRESS, InputModifiers.CONTROL);
    assertEquals("AB", box.text());

    box.setHistoryLimit(1);
    box.onCharacter('D');
    box.onCharacter('E');
    assertTrue(box.canUndo());
    assertTrue(box.undo());
    assertEquals("ABD", box.text());
    assertFalse(box.undo());
  }

  @Test
  void textBoxEditsUnicodeAndSupportsReadOnlyMode() {
    TextBox box = new TextBox(Rectangle.of(0, 0, 100, 20), "A");
    box.onCharacter(0x1F642);
    assertEquals("A🙂", box.text());

    box.onKey(KeyCode.BACKSPACE, KeyAction.PRESS, 0);
    assertEquals("A", box.text());

    box.setEditable(false);
    assertFalse(box.onCharacter('B'));
    assertEquals("A", box.text());
  }

  @Test
  void textBoxSupportsMultilineSelectionClipboardAndVerticalMovement() {
    DpiContext context = new DpiContext(800, 450, HANDLER);
    try (Canvas canvas = new Canvas(context)) {
      TextBox box = new TextBox(Rectangle.of(0, 0, 200, 100), "one\ntwo\nthree");
      canvas.add(box);
      box.setCursorIndex(6);

      box.onKey(KeyCode.UP, KeyAction.PRESS, 0);
      assertEquals(2, box.cursorIndex());
      box.onKey(KeyCode.DOWN, KeyAction.PRESS, 0);
      assertEquals(6, box.cursorIndex());

      box.select(0, 3);
      box.onKey(KeyCode.C, KeyAction.PRESS, InputModifiers.CONTROL);
      assertEquals("one", canvas.getClipboardText());
      box.select(4, 7);
      box.onKey(KeyCode.X, KeyAction.PRESS, InputModifiers.CONTROL);
      assertEquals("two", canvas.getClipboardText());
      assertEquals("one\n\nthree", box.text());

      canvas.setClipboardText("second");
      box.setCursorIndex(4);
      box.onKey(KeyCode.V, KeyAction.PRESS, InputModifiers.CONTROL);
      assertEquals("one\nsecond\nthree", box.text());
      box.onKey(KeyCode.ENTER, KeyAction.PRESS, 0);
      assertTrue(box.text().contains("\n"));
    }
  }

  @Test
  void checkboxNotifiesOnlyForUserChanges() {
    CheckBox box = new CheckBox(Rectangle.of(0, 0, 100, 20), Literal.of("Enabled"), false);
    AtomicInteger changes = new AtomicInteger();
    box.setOnChanged(value -> changes.incrementAndGet());

    box.setChecked(true);
    assertEquals(0, changes.get());
    box.onKey(KeyCode.SPACE, KeyAction.PRESS, 0);
    box.onKey(KeyCode.SPACE, KeyAction.RELEASE, 0);
    assertFalse(box.checked());
    assertEquals(1, changes.get());
  }

  @Test
  void dropdownPopupReceivesInputOutsideCollapsedBounds() {
    DpiContext context = new DpiContext(800, 450, HANDLER);
    try (Canvas canvas = new Canvas(context)) {
      DropDown menu = new DropDown(Rectangle.of(10, 10, 100, 20),
          List.of(Literal.of("First"), Literal.of("Second")), 0);
      canvas.add(menu);
      canvas.add(new Element(Rectangle.of(10, 45, 100, 20)) {
        @Override
        public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action,
                                     int modifiers) {
          return true;
        }
      });

      click(canvas, 20.0, 20.0);
      assertTrue(menu.expanded());
      click(canvas, 20.0, 55.0);
      assertEquals(1, menu.selectedIndex());
      assertFalse(menu.expanded());
    }
  }

  @Test
  void dropdownUsesConfiguredEntryThresholdForItsScrollbar() {
    DropDown menu = new DropDown(Rectangle.of(0, 0, 120, 20),
        List.of(Literal.of("One"), Literal.of("Two"), Literal.of("Three"), Literal.of("Four")), 0);
    menu.setDropCount(3);

    assertFalse(menu.scrollBar().visible());
    menu.setExpanded(true);
    assertTrue(menu.scrollBar().visible());
    assertEquals(20.0, menu.scrollBar().step());
    menu.setSelectedIndex(3);
    assertTrue(menu.scrollBar().value() > 0.0);
  }

  @Test
  void elementOwnsConfigurableTooltip() {
    CheckBox box = new CheckBox(Rectangle.of(0, 0, 20, 20), Literal.of(""), false);
    box.setDefaultTooltip(Literal.of("More information"));
    box.setTooltipDelayMillis(0L);

    assertEquals(0L, box.tooltipDelayMillis());
    assertThrows(IllegalArgumentException.class, () -> box.setTooltipDelayMillis(-1L));
    box.setDefaultTooltip(Literal.of(""));
  }

  @Test
  void elementUsesConfiguredTooltipDelayUnlessExplicitlyOverridden() {
    CheckBox box = new CheckBox(Rectangle.of(0, 0, 20, 20), Literal.of(""), false);
    box.setTooltipDelayMillis(250L);

    assertEquals(250L, box.tooltipDelayMillis());
    box.setTooltipDelayMillis(10L);
    assertEquals(10L, box.tooltipDelayMillis());
  }

  @Test
  void controlsExposeRenderersThroughTheUiRenderPackage() {
    assertInstanceOf(ButtonRenderer.class,
        new Button(Rectangle.of(0, 0, 20, 20)).renderer());
    assertInstanceOf(CheckBoxRenderer.class,
        new CheckBox(Rectangle.of(0, 0, 20, 20), Literal.of(""), false).renderer());
    TextBox textBox = new TextBox(Rectangle.of(0, 0, 20, 20), "text");
    assertInstanceOf(TextBoxRenderer.class, textBox.renderer());
    TextView textView = new TextView(Rectangle.of(0, 0, 20, 20), Literal.of("text"));
    assertInstanceOf(TextViewRenderer.class, textView.renderer());
  }

  @Test
  void windowCloseButtonHidesWindowAndNotifiesOnce() {
    Window window = new Window(Rectangle.of(10, 10, 200, 100), Literal.of("Settings"));
    AtomicInteger closes = new AtomicInteger();
    window.setOnClose(closes::incrementAndGet);

    window.onMouseButton(195.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    window.onMouseButton(195.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 0);
    window.close();

    assertTrue(window.closed());
    assertFalse(window.visible());
    assertEquals(1, closes.get());
    window.open();
    assertTrue(window.visible());
  }

  @Test
  void windowMinimizeButtonKeepsTitleBarAndRestoresClientBounds() {
    Window window = new Window(Rectangle.of(10, 10, 200, 100), Literal.of("Settings"));

    window.onMouseButton(172.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    window.onMouseButton(172.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 0);

    assertTrue(window.minimized());
    assertEquals(18.0F, window.bounds().height());
    assertEquals(200.0F, window.bounds().width());

    window.onMouseButton(172.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.PRESS, 0);
    window.onMouseButton(172.0F, 10.0F, KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 0);

    assertFalse(window.minimized());
    assertEquals(100.0F, window.bounds().height());
  }

  @Test
  void windowPresentationOptionsCanBeDisabledIndependently() {
    Window window = new Window(Rectangle.of(10, 10, 200, 100), Literal.of("Settings"));

    window.setMovable(false);
    window.setMinimizable(false);
    window.setClosable(false);
    window.setBackdropBlurEnabled(true);
    window.setShadowEnabled(false);

    assertFalse(window.movable());
    assertFalse(window.minimizable());
    assertFalse(window.closable());
    assertTrue(window.backdropBlurEnabled());
    assertFalse(window.shadowEnabled());
  }

  @Test
  void windowStaysInsideItsParentBounds() {
    Panel parent = new Panel(Rectangle.of(0.0F, 0.0F, 100.0F, 80.0F));
    Window window = new Window(Rectangle.of(-20.0F, -10.0F, 120.0F, 90.0F), Literal.of("Child"));
    parent.add(window);

    assertEquals(0.0F, window.bounds().minX());
    assertEquals(0.0F, window.bounds().minY());
    assertEquals(100.0F, window.bounds().width());
    assertEquals(80.0F, window.bounds().height());

    window.setBounds(Rectangle.of(80.0F, 70.0F, 30.0F, 30.0F));

    assertEquals(70.0F, window.bounds().minX());
    assertEquals(50.0F, window.bounds().minY());
  }

  @Test
  void nestedWindowStaysInsideParentClientArea() {
    Window parent = new Window(Rectangle.of(0.0F, 0.0F, 100.0F, 80.0F), Literal.of("Parent"));
    Window child = new Window(Rectangle.of(-20.0F, -10.0F, 120.0F, 90.0F), Literal.of("Child"));
    parent.addContent(child);

    assertEquals(0.0F, child.bounds().minX());
    assertEquals(parent.titleHeight(), child.bounds().minY());
    assertEquals(100.0F, child.bounds().width());
    assertEquals(80.0F - parent.titleHeight(), child.bounds().height());

    child.setBounds(Rectangle.of(80.0F, 0.0F, 30.0F, 30.0F));
    assertEquals(70.0F, child.bounds().minX());
    assertEquals(parent.titleHeight(), child.bounds().minY());

    child.setBounds(Rectangle.of(80.0F, 70.0F, 30.0F, 30.0F));
    assertEquals(70.0F, child.bounds().minX());
    assertEquals(50.0F, child.bounds().minY());
  }

  @Test
  void canvasWindowUsesCanvasBoundsWithoutTitleInset() {
    DpiContext context = new DpiContext(800, 450, HANDLER);
    try (Canvas canvas = new Canvas(context)) {
      Window window = new Window(Rectangle.of(-20.0F, -10.0F, 120.0F, 90.0F), Literal.of("Top"));
      canvas.add(window);

      assertEquals(0.0F, window.bounds().minX());
      assertEquals(0.0F, window.bounds().minY());
      assertEquals(120.0F, window.bounds().width());
      assertEquals(90.0F, window.bounds().height());
    }
  }

  @Test
  void clickingAWindowBringsItAboveItsSiblingWindows() {
    DpiContext context = new DpiContext(800, 450, HANDLER);
    try (Canvas canvas = new Canvas(context)) {
      Window first = new Window(Rectangle.of(10, 10, 100, 100), Literal.of("First"));
      Window second = new Window(Rectangle.of(80, 80, 100, 100), Literal.of("Second"));
      canvas.add(first);
      canvas.add(second);

      click(canvas, 20.0, 20.0);

      assertSame(first, canvas.children().get(canvas.children().size() - 1));
      assertSame(second, canvas.children().get(0));
    }
  }

  @Test
  void aWindowConsumesPointerEventsInItsClientArea() {
    DpiContext context = new DpiContext(800, 450, HANDLER);
    AtomicInteger clicks = new AtomicInteger();
    try (Canvas canvas = new Canvas(context)) {
      Button underneath = new Button(Rectangle.of(0, 0, 140, 120));
      underneath.setOnClick(clicks::incrementAndGet);
      Window blocker = new Window(Rectangle.of(10, 10, 100, 100), Literal.of("Blocking window"));
      canvas.add(underneath);
      canvas.add(blocker);

      click(canvas, 30.0, 70.0);

      assertEquals(0, clicks.get());
    }
  }

  private static void click(Canvas canvas, double x, double y) {
    canvas.dispatchMouseButton(KeyCode.MOUSE_LEFT, KeyAction.PRESS, x, y, 0);
    canvas.dispatchMouseButton(KeyCode.MOUSE_LEFT, KeyAction.RELEASE, x, y, 0);
  }

}
