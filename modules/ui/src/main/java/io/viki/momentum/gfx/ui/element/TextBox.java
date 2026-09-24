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

import io.viki.momentum.gfx.ui.HeadlessTextEditor;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.TextBoxRenderer;
import io.viki.momentum.input.InputModifiers;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Provides an editable or read-only text control backed by {@link HeadlessTextEditor}.
 *
 * <p>The control supplies pointer selection, keyboard editing, clipboard integration, scrolling,
 * and cached text presentation while delegating text semantics to the headless editor. Mutable
 * state is intended for one owning UI thread.
 */
public final class TextBox extends Element {
  private final HeadlessTextEditor editor;
  private final TextBoxRenderer renderCache;
  private final float minimumContentWidth;
  private final float minimumContentHeight;
  private boolean enabled = true;
  private boolean focused;
  private boolean draggingSelection;
  private String placeholder = "Type here...";
  private @Nullable Consumer<String> onChanged;
  private @Nullable Consumer<String> onSubmit;

  /**
   * Creates a text box initialized with the supplied text.
   *
   * @param bounds the text box's local bounds
   * @param text the initial text
   */
  public TextBox(Rectangle bounds, String text) {
    super(bounds);
    minimumContentWidth = bounds.width();
    minimumContentHeight = bounds.height();
    editor = new HeadlessTextEditor(text);
    renderCache = new TextBoxRenderer();
    editor.setOnChanged(value -> {
      renderCache.invalidate();
      if (onChanged != null) {
        onChanged.accept(value);
      }
      renderCache.scrollCursorIntoView(this);
    });
    editor.setOnSubmit(value -> {
      if (onSubmit != null) {
        onSubmit.accept(value);
      }
    });
  }

  /**
   * Returns the rendering-independent editor state owned by this control.
   *
   * <p>The editor can be configured directly when a caller needs editing behavior without
   * replacing the text box's renderer.
   *
   * @return headless text editor
   */
  public HeadlessTextEditor editor() {
    return editor;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return renderCache;
  }

  /**
   * Returns the current editor text.
   *
   * @return current text
   */
  public String text() {
    return editor.text();
  }

  /**
   * Returns the configured undo/redo history limit.
   *
   * @return maximum retained history entries per direction
   */
  public int historyLimit() {
    return editor.historyLimit();
  }

  /**
   * Sets the undo/redo history limit.
   *
   * @param value the non-negative history limit
   * @throws IllegalArgumentException if {@code value} is negative
   */
  public void setHistoryLimit(int value) {
    editor.setHistoryLimit(value);
  }

  /**
   * Reports whether an undo state is available.
   *
   * @return whether undo can change the editor state
   */
  public boolean canUndo() {
    return editor.canUndo();
  }

  /**
   * Reports whether a redo state is available.
   *
   * @return whether redo can change the editor state
   */
  public boolean canRedo() {
    return editor.canRedo();
  }

  /**
   * Restores the most recent undo state.
   *
   * @return whether a state was restored
   */
  public boolean undo() {
    boolean changed = editor.undo();
    if (changed) {
      renderCache.scrollCursorIntoView(this);
    }
    return changed;
  }

  /**
   * Restores the most recently undone state.
   *
   * @return whether a state was restored
   */
  public boolean redo() {
    boolean changed = editor.redo();
    if (changed) {
      renderCache.scrollCursorIntoView(this);
    }
    return changed;
  }

  /**
   * Replaces the complete text.
   *
   * @param value the replacement text
   */
  public void setText(String value) {
    editor.setText(value);
  }

  /**
   * Reports whether newline characters are accepted.
   *
   * @return whether multiline editing is enabled
   */
  public boolean multiline() {
    return editor.multiline();
  }

  /**
   * Enables or disables multiline editing.
   *
   * @param value whether newline input is enabled
   * @throws IllegalStateException if automatic trailing newlines conflict with the new mode
   */
  public void setMultiline(boolean value) {
    editor.setMultiline(value);
    renderCache.invalidate();
    ScrollPane scrollPane = enclosingScrollPaneForRender();
    if (scrollPane != null) {
      scrollPane.contentLayoutChanged();
    }
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Reports whether long visual lines are wrapped.
   *
   * @return whether wrapping is enabled
   */
  public boolean wrapText() {
    return editor.wrapText();
  }

  /**
   * Enables or disables visual line wrapping.
   *
   * @param value whether long lines should wrap
   */
  public void setWrapText(boolean value) {
    editor.setWrapText(value);
    renderCache.invalidate();
    ScrollPane scrollPane = enclosingScrollPaneForRender();
    if (scrollPane != null) {
      scrollPane.contentLayoutChanged();
    }
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Reports whether a trailing newline is maintained automatically.
   *
   * @return whether automatic trailing-newline mode is enabled
   */
  public boolean automaticTrailingNewline() {
    return editor.automaticTrailingNewline();
  }

  /**
   * Enables or disables automatic trailing newlines.
   *
   * @param value whether a multiline value should end in a newline
   * @throws IllegalStateException if enabled while multiline editing is disabled
   */
  public void setAutomaticTrailingNewline(boolean value) {
    editor.setAutomaticTrailingNewline(value);
    renderCache.invalidate();
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Reports whether text mutation is enabled.
   *
   * @return whether the editor is editable
   */
  public boolean editable() {
    return editor.editable();
  }

  /**
   * Enables or disables text mutation.
   *
   * @param value whether editing should be accepted
   */
  public void setEditable(boolean value) {
    editor.setEditable(value);
  }

  /**
   * Reports whether this control accepts pointer and keyboard input.
   *
   * @return whether the text box is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables input handling.
   *
   * @param value whether input should be accepted
   */
  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      draggingSelection = false;
    }
  }

  /**
   * Reports whether this text box currently owns keyboard focus.
   *
   * @return whether the text box is focused
   */
  public boolean focused() {
    return focused;
  }

  /**
   * Returns the maximum accepted text length.
   *
   * @return maximum length in Unicode code points
   */
  public int maximumLength() {
    return editor.maximumLength();
  }

  /**
   * Sets the maximum accepted text length.
   *
   * @param value the non-negative maximum length
   * @throws IllegalArgumentException if the value is negative
   * @throws IllegalStateException if existing text exceeds the value
   */
  public void setMaximumLength(int value) {
    editor.setMaximumLength(value);
  }

  /**
   * Returns the current caret index.
   *
   * @return caret position as a UTF-16 offset
   */
  public int cursorIndex() {
    return editor.cursorIndex();
  }

  /**
   * Moves the caret and clears the selection.
   *
   * @param value the target UTF-16 index
   * @throws IndexOutOfBoundsException if the index is invalid
   */
  public void setCursorIndex(int value) {
    editor.setCursorIndex(value);
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Returns the lower selection boundary.
   *
   * @return lower selection boundary as a UTF-16 offset
   */
  public int selectionStart() {
    return editor.selectionStart();
  }

  /**
   * Returns the upper selection boundary.
   *
   * @return upper selection boundary as a UTF-16 offset
   */
  public int selectionEnd() {
    return editor.selectionEnd();
  }

  /**
   * Returns the selected substring.
   *
   * @return selected text, or an empty string when no range is selected
   */
  public String selectedText() {
    return editor.selectedText();
  }

  /**
   * Selects a UTF-16 range.
   *
   * @param start the anchor index
   * @param end the active end index
   * @throws IndexOutOfBoundsException if either index is invalid
   */
  public void select(int start, int end) {
    editor.select(start, end);
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Selects all editable text and scrolls the caret into view.
   */
  public void selectAll() {
    editor.selectAll();
    renderCache.scrollCursorIntoView(this);
  }

  /**
   * Clears the current selection while retaining the caret position.
   */
  public void clearSelection() {
    editor.clearSelection();
  }

  /**
   * Installs or removes the text-change listener.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnChanged(@Nullable Consumer<String> value) {
    onChanged = value;
  }

  /**
   * Installs or removes the submit listener.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnSubmit(@Nullable Consumer<String> value) {
    onSubmit = value;
  }

  /**
   * Returns the placeholder displayed when the text is empty and unfocused.
   *
   * @return configured placeholder text
   */
  public String placeholder() {
    return placeholder;
  }

  /**
   * Sets the empty-text placeholder.
   *
   * @param value the placeholder text
   */
  public void setPlaceholder(String value) {
    placeholder = value;
    renderCache.invalidate();
  }

  @Override
  public void setBounds(Rectangle value) {
    Rectangle previous = bounds();
    super.setBounds(value);
    if (previous.width() != value.width() || previous.height() != value.height()) {
      renderCache.invalidate();
    }
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    if (enabled && draggingSelection) {
      editor.setCursorIndex(Math.min(renderCache.hitIndex(this, x, y), editor.text().length()), true);
      renderCache.scrollCursorIntoView(this);
    }
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button != KeyCode.MOUSE_LEFT || action == KeyAction.REPEAT) {
      return false;
    }
    int index = Math.min(renderCache.hitIndex(this, x, y), editor.text().length());
    if (action == KeyAction.PRESS) {
      editor.setCursorIndex(index, (modifiers & InputModifiers.SHIFT) != 0);
      draggingSelection = true;
    } else {
      editor.setCursorIndex(index, true);
      draggingSelection = false;
    }
    renderCache.scrollCursorIntoView(this);
    return true;
  }

  @Override
  public boolean onCharacter(int codepoint) {
    if (!enabled) {
      return false;
    }
    return editor.onCharacter(codepoint);
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled || action == KeyAction.RELEASE) {
      return false;
    }
    boolean command = (modifiers & (InputModifiers.CONTROL | InputModifiers.SUPER)) != 0;
    if (command && key == KeyCode.C) {
      copySelection();
      return true;
    }
    if (command && key == KeyCode.X) {
      copySelection();
      if (editor.editable()) {
        editor.deleteSelection();
      }
      return true;
    }
    if (command && key == KeyCode.V && editor.editable()) {
      Canvas canvas = owningCanvas();
      return canvas != null && editor.insertText(canvas.pasteFromClipboard());
    }
    boolean handled = editor.onKey(key, action, modifiers);
    if (handled) {
      renderCache.scrollCursorIntoView(this);
    }
    return handled;
  }

  @Override
  public void onPointerCancel(KeyCode button) {
    draggingSelection = false;
  }

  @Override
  public boolean acceptsFocus() {
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean value) {
    focused = value;
    if (!value) {
      draggingSelection = false;
    }
  }

  /**
   * Returns the initial width retained as the minimum scroll-content width.
   *
   * @return minimum content width in logical units
   */
  public float minimumContentWidthForRender() {
    return minimumContentWidth;
  }

  /**
   * Returns the initial height retained as the minimum scroll-content height.
   *
   * @return minimum content height in logical units
   */
  public float minimumContentHeightForRender() {
    return minimumContentHeight;
  }

  /**
   * Returns the placeholder used by the renderer while the editor is empty and unfocused.
   *
   * @return placeholder text
   */
  public String placeholderForRender() {
    return placeholder;
  }

  /**
   * Finds the nearest scrolling ancestor.
   *
   * @return the nearest enclosing scroll pane, or {@code null} when unattached to one
   */
  public @Nullable ScrollPane enclosingScrollPaneForRender() {
    @Nullable Element current = parent();
    while (current != null) {
      if (current instanceof ScrollPane scrollPane) {
        return scrollPane;
      }
      current = current.parent();
    }
    return null;
  }

  private void copySelection() {
    if (editor.selectionStart() == editor.selectionEnd()) {
      return;
    }
    Canvas canvas = owningCanvas();
    if (canvas != null) {
      canvas.copyToClipboard(editor.selectedText());
    }
  }
}
