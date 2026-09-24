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

import io.viki.momentum.input.InputModifiers;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Provides rendering-independent text editing state for a text control.
 *
 * <p>The editor owns the text, caret, selection, movement, editing, and bounded undo/redo history
 * while leaving presentation and clipboard integration to the host widget. Indices are UTF-16
 * offsets suitable for Java strings, but cursor movement avoids splitting a Unicode code point.
 * Instances are mutable and must be confined to their owning UI thread.
 */
@InternalApi
public final class HeadlessTextEditor {
  /** Default number of edit states retained for undo and redo. */
  private static final int DEFAULT_HISTORY_LIMIT = 128;

  private String text;
  private int cursor;
  private int selectionAnchor;
  private int maximumLength = Integer.MAX_VALUE;
  private int preferredColumn = -1;
  private boolean multiline = true;
  private boolean wrapText = true;
  private boolean automaticTrailingNewline;
  private boolean editable = true;
  private int historyLimit = DEFAULT_HISTORY_LIMIT;
  private final Deque<HistoryState> undoHistory = new ArrayDeque<>();
  private final Deque<HistoryState> redoHistory = new ArrayDeque<>();
  private boolean restoringHistory;
  private @Nullable Consumer<String> onChanged;
  private @Nullable Consumer<String> onSubmit;

  /** Creates an empty multiline editor with the default history limit. */
  public HeadlessTextEditor() {
    this("");
  }

  /**
   * Creates an editor initialized with the supplied text.
   *
   * @param text the initial text; line endings are normalized to {@code '\n'}
   */
  public HeadlessTextEditor(String text) {
    this.text = normalizeNewlines(text);
    cursor = this.text.length();
    selectionAnchor = cursor;
  }
  /**
   * Returns the complete normalized editor text.
   *
   * @return current text
   */
  public String text() {
    return text;
  }
  /**
   * Returns the maximum number of history entries retained in each direction.
   *
   * @return configured history limit
   */
  public int historyLimit() {
    return historyLimit;
  }

  /**
   * Sets the maximum number of undo and redo states retained by this editor.
   *
   * @param value the non-negative history limit
   * @throws IllegalArgumentException if {@code value} is negative
   */
  public void setHistoryLimit(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Text history limit must not be negative: " + value);
    }
    historyLimit = value;
    trimHistory(undoHistory);
    trimHistory(redoHistory);
  }

  /**
   * Reports whether an undo operation is currently available.
   *
   * @return whether undo can change the editor state
   */
  public boolean canUndo() {
    return !undoHistory.isEmpty();
  }

  /**
   * Reports whether a redo operation is currently available.
   *
   * @return whether redo can change the editor state
   */
  public boolean canRedo() {
    return !redoHistory.isEmpty();
  }

  /**
   * Restores the most recent undo state.
   *
   * @return whether an undo state was restored
   */
  public boolean undo() {
    if (undoHistory.isEmpty()) {
      return false;
    }
    redoHistory.addLast(snapshot());
    restore(undoHistory.removeLast());
    trimHistory(redoHistory);
    return true;
  }

  /**
   * Restores the most recently undone state.
   *
   * @return whether a redo state was restored
   */
  public boolean redo() {
    if (redoHistory.isEmpty()) {
      return false;
    }
    undoHistory.addLast(snapshot());
    restore(redoHistory.removeLast());
    trimHistory(undoHistory);
    return true;
  }

  /**
   * Replaces the complete text and resets the selection to the retained caret position.
   *
   * @param value the replacement text
   * @throws IllegalArgumentException if the replacement exceeds the configured maximum length
   */
  public void setText(String value) {
    String normalized = normalizeNewlines(value);
    if (automaticTrailingNewline) {
      normalized = withTrailingNewline(normalized);
    }
    if (normalized.codePointCount(0, normalized.length()) > maximumLength) {
      throw new IllegalArgumentException("Text exceeds maximum length of " + maximumLength);
    }
    String replacement = multiline ? normalized : withoutNewlines(normalized);
    if (text.equals(replacement)) {
      return;
    }
    recordEdit();
    text = replacement;
    cursor = Math.min(cursor, editableEnd());
    selectionAnchor = cursor;
    redoHistory.clear();
    notifyChanged();
  }

  /**
   * Reports whether newline characters are accepted.
   *
   * @return whether multiline input is enabled
   */
  public boolean multiline() {
    return multiline;
  }

  /**
   * Enables or disables newline input.
   *
   * @param value whether multiline input should be enabled
   * @throws IllegalStateException if disabling multiline mode conflicts with automatic trailing newlines
   */
  public void setMultiline(boolean value) {
    if (!value && automaticTrailingNewline) {
      throw new IllegalStateException(
          "Automatic trailing newline requires multiline editing");
    }
    multiline = value;
    if (!value && text.indexOf('\n') >= 0) {
      recordEdit();
      text = withoutNewlines(text);
      cursor = Math.min(cursor, text.length());
      selectionAnchor = cursor;
      redoHistory.clear();
      notifyChanged();
    }
  }

  /**
   * Reports whether the presentation should wrap long lines.
   *
   * @return whether wrapping is enabled
   */
  public boolean wrapText() {
    return wrapText;
  }

  /**
   * Enables or disables visual line wrapping.
   *
   * @param value whether long lines should wrap
   */
  public void setWrapText(boolean value) {
    wrapText = value;
  }

  /**
   * Reports whether the editor maintains a trailing newline.
   *
   * @return whether automatic trailing newlines are enabled
   */
  public boolean automaticTrailingNewline() {
    return automaticTrailingNewline;
  }

  /**
   * Enables or disables automatic trailing newline maintenance.
   *
   * @param value whether a multiline text value should end with a newline
   * @throws IllegalStateException if enabled while multiline mode is disabled
   */
  public void setAutomaticTrailingNewline(boolean value) {
    if (automaticTrailingNewline == value) {
      return;
    }
    if (value && !multiline) {
      throw new IllegalStateException(
          "Automatic trailing newline requires multiline editing");
    }
    automaticTrailingNewline = value;
    if (value && !text.endsWith("\n")) {
      text += "\n";
      cursor = Math.min(cursor, editableEnd());
      selectionAnchor = Math.min(selectionAnchor, editableEnd());
      notifyChanged();
    }
  }

  /**
   * Reports whether editing operations are accepted.
   *
   * @return whether the editor is editable
   */
  public boolean editable() {
    return editable;
  }

  /**
   * Enables or disables text mutation while retaining display and selection behavior.
   *
   * @param value whether editing should be enabled
   */
  public void setEditable(boolean value) {
    editable = value;
  }
  /**
   * Returns the maximum accepted text length.
   *
   * @return maximum length in Unicode code points
   */
  public int maximumLength() {
    return maximumLength;
  }

  /**
   * Sets the maximum number of Unicode code points accepted by future edits.
   *
   * @param value the non-negative maximum length
   * @throws IllegalArgumentException if {@code value} is negative
   * @throws IllegalStateException if the current text is longer than {@code value}
   */
  public void setMaximumLength(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Maximum text length must not be negative: " + value);
    }
    if (text.codePointCount(0, text.length()) > value) {
      throw new IllegalStateException("Existing text exceeds new maximum length of " + value);
    }
    maximumLength = value;
  }
  /**
   * Returns the current caret position.
   *
   * @return caret position as a UTF-16 offset
   */
  public int cursorIndex() {
    return cursor;
  }

  /**
   * Moves the caret to a UTF-16 boundary and clears the selection.
   *
   * @param value the target caret index
   * @throws IndexOutOfBoundsException if the index is outside the editable text or splits a code point
   */
  public void setCursorIndex(int value) {
    setCursorIndex(value, false);
  }

  /**
   * Moves the caret, optionally retaining the current selection anchor.
   *
   * @param value the target caret index
   * @param preserveSelection whether the existing anchor should be retained
   * @throws IndexOutOfBoundsException if the index is outside the editable text or splits a code point
   */
  public void setCursorIndex(int value, boolean preserveSelection) {
    validateIndex(value);
    cursor = value;
    if (!preserveSelection) {
      selectionAnchor = value;
    }
    preferredColumn = -1;
  }

  /**
   * Returns the lower UTF-16 offset of the current selection.
   *
   * @return selection start offset
   */
  public int selectionStart() {
    return Math.min(cursor, selectionAnchor);
  }

  /**
   * Returns the upper UTF-16 offset of the current selection.
   *
   * @return selection end offset
   */
  public int selectionEnd() {
    return Math.max(cursor, selectionAnchor);
  }

  /**
   * Returns the selected substring.
   *
   * @return selected text, or an empty string when no range is selected
   */
  public String selectedText() {
    return text.substring(selectionStart(), selectionEnd());
  }

  /**
   * Selects a UTF-16 range.
   *
   * @param start the selection anchor index
   * @param end the active selection end index
   * @throws IndexOutOfBoundsException if either index is invalid or splits a code point
   */
  public void select(int start, int end) {
    validateIndex(start);
    validateIndex(end);
    selectionAnchor = start;
    cursor = end;
  }

  /**
   * Selects all editable text.
   */
  public void selectAll() {
    selectionAnchor = 0;
    cursor = editableEnd();
  }

  /**
   * Clears the selection while retaining the caret position.
   */
  public void clearSelection() {
    selectionAnchor = cursor;
  }

  /**
   * Installs or removes the listener notified after text changes.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnChanged(@Nullable Consumer<String> value) {
    onChanged = value;
  }

  /**
   * Installs or removes the listener notified when a non-multiline Enter is submitted.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnSubmit(@Nullable Consumer<String> value) {
    onSubmit = value;
  }

  /**
   * Inserts a typed Unicode code point when editing is enabled.
   *
   * @param codepoint the Unicode code point to insert
   * @return whether the code point was inserted
   */
  public boolean onCharacter(int codepoint) {
    if (!editable || Character.isISOControl(codepoint)) {
      return false;
    }
    return insertText(Character.toString(codepoint));
  }

  /**
   * Handles navigation, editing, undo, and redo key actions.
   *
   * <p>Clipboard shortcuts remain the responsibility of the host control because clipboard
   * ownership belongs to the surrounding UI context.
   *
   * @param key the key that changed
   * @param action the key action
   * @param modifiers the active modifier mask
   * @return whether the key was handled
   */
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (action == KeyAction.RELEASE) {
      return false;
    }
    boolean selecting = (modifiers & InputModifiers.SHIFT) != 0;
    boolean command = (modifiers & (InputModifiers.CONTROL | InputModifiers.SUPER)) != 0;
    if (command && key == KeyCode.A) {
      selectAll();
      return true;
    }
    if (command && key == KeyCode.Z) {
      if (selecting) {
        redo();
      } else {
        undo();
      }
      return true;
    }
    if (command && key == KeyCode.Y) {
      redo();
      return true;
    }
    if (key == KeyCode.LEFT) {
      int target = hasSelection() && !selecting ? selectionStart() : previousIndex(cursor);
      moveCursor(target, selecting);
      return true;
    }
    if (key == KeyCode.RIGHT) {
      int target = hasSelection() && !selecting ? selectionEnd() : nextIndex(cursor);
      moveCursor(target, selecting);
      return true;
    }
    if (key == KeyCode.UP) {
      moveVertical(-1, selecting);
      return true;
    }
    if (key == KeyCode.DOWN) {
      moveVertical(1, selecting);
      return true;
    }
    if (key == KeyCode.HOME) {
      moveCursor(lineStart(cursor), selecting);
      return true;
    }
    if (key == KeyCode.END) {
      moveCursor(lineEnd(cursor), selecting);
      return true;
    }
    if (key == KeyCode.ENTER || key == KeyCode.KP_ENTER) {
      if (editable && multiline) {
        return insertText("\n");
      }
      if (onSubmit != null) {
        onSubmit.accept(text);
      }
      return true;
    }
    if (!editable) {
      return false;
    }
    if (key == KeyCode.BACKSPACE) {
      if (!deleteSelection() && cursor > 0) {
        replaceRange(previousIndex(cursor), cursor, "");
      }
      return true;
    }
    if (key == KeyCode.DELETE) {
      if (!deleteSelection() && cursor < text.length()) {
        replaceRange(cursor, nextIndex(cursor), "");
      }
      return true;
    }
    return false;
  }

  /**
   * Inserts text after normalizing newlines and applying selection and length rules.
   *
   * @param value the text to insert
   * @return whether any text was inserted
   */
  public boolean insertText(String value) {
    if (!editable) {
      return false;
    }
    String normalized = normalizeNewlines(value);
    if (!multiline) {
      normalized = withoutNewlines(normalized);
    }
    String selected = selectedText();
    int retainedCodePoints = text.codePointCount(0, text.length())
        - selected.codePointCount(0, selected.length());
    int available = maximumLength - retainedCodePoints;
    int codePoints = normalized.codePointCount(0, normalized.length());
    if (codePoints > available) {
      normalized = normalized.substring(0,
          normalized.offsetByCodePoints(0, Math.max(0, available)));
    }
    if (normalized.isEmpty()) {
      return false;
    }
    replaceRange(selectionStart(), selectionEnd(), normalized);
    return true;
  }

  /**
   * Deletes the current selection.
   *
   * @return whether a selection was deleted
   */
  public boolean deleteSelection() {
    if (!hasSelection()) {
      return false;
    }
    replaceRange(selectionStart(), selectionEnd(), "");
    return true;
  }

  private void moveCursor(int target, boolean selecting) {
    cursor = Math.min(target, editableEnd());
    if (!selecting) {
      selectionAnchor = cursor;
    }
    preferredColumn = -1;
  }

  private void moveVertical(int direction, boolean selecting) {
    int currentStart = lineStart(cursor);
    if (preferredColumn < 0) {
      preferredColumn = text.codePointCount(currentStart, cursor);
    }
    int targetStart;
    int targetEnd;
    if (direction < 0) {
      if (currentStart == 0) {
        cursor = 0;
        if (!selecting) {
          selectionAnchor = cursor;
        }
        return;
      }
      targetEnd = currentStart - 1;
      targetStart = lineStart(targetEnd);
    } else {
      int currentEnd = lineEnd(cursor);
      if (currentEnd == text.length()) {
        cursor = editableEnd();
        if (!selecting) {
          selectionAnchor = cursor;
        }
        return;
      }
      targetStart = currentEnd + 1;
      targetEnd = lineEnd(targetStart);
    }
    int count = text.codePointCount(targetStart, targetEnd);
    cursor = text.offsetByCodePoints(targetStart, Math.min(preferredColumn, count));
    if (!selecting) {
      selectionAnchor = cursor;
    }
    cursor = Math.min(cursor, editableEnd());
    selectionAnchor = Math.min(selectionAnchor, editableEnd());
  }

  private void replaceRange(int start, int end, String replacement) {
    recordEdit();
    text = text.substring(0, start) + replacement + text.substring(end);
    if (automaticTrailingNewline) {
      text = withTrailingNewline(text);
    }
    cursor = Math.min(start + replacement.length(), editableEnd());
    selectionAnchor = cursor;
    preferredColumn = -1;
    redoHistory.clear();
    notifyChanged();
  }

  private boolean hasSelection() {
    return cursor != selectionAnchor;
  }

  private void recordEdit() {
    if (restoringHistory || historyLimit == 0) {
      return;
    }
    undoHistory.addLast(snapshot());
    trimHistory(undoHistory);
    redoHistory.clear();
  }

  private HistoryState snapshot() {
    return new HistoryState(text, cursor, selectionAnchor);
  }

  private void restore(HistoryState state) {
    restoringHistory = true;
    try {
      text = state.text();
      cursor = state.cursor();
      selectionAnchor = state.selectionAnchor();
      preferredColumn = -1;
      notifyChanged();
    } finally {
      restoringHistory = false;
    }
  }

  private void trimHistory(Deque<HistoryState> history) {
    while (history.size() > historyLimit) {
      history.removeFirst();
    }
  }

  private void notifyChanged() {
    if (onChanged != null) {
      onChanged.accept(text);
    }
  }

  private void validateIndex(int value) {
    if (value < 0 || value > editableEnd()
        || value > 0 && value < text.length() && Character.isLowSurrogate(text.charAt(value))) {
      throw new IndexOutOfBoundsException("Invalid text cursor index: " + value);
    }
  }

  private int editableEnd() {
    return text.length();
  }

  private int lineStart(int index) {
    return index == 0 ? 0 : text.lastIndexOf('\n', index - 1) + 1;
  }

  private int lineEnd(int index) {
    int end = text.indexOf('\n', index);
    return end < 0 ? text.length() : end;
  }

  private int previousIndex(int index) {
    return index == 0 ? 0 : text.offsetByCodePoints(index, -1);
  }

  private int nextIndex(int index) {
    return index == text.length() ? index : text.offsetByCodePoints(index, 1);
  }

  private static String normalizeNewlines(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String withoutNewlines(String value) {
    return value.replace('\n', ' ');
  }

  private static String withTrailingNewline(String value) {
    return value.endsWith("\n") ? value : value + "\n";
  }

  private record HistoryState(String text, int cursor, int selectionAnchor) {
    private HistoryState {
      if (cursor < 0 || cursor > text.length() || selectionAnchor < 0 || selectionAnchor > text.length()) {
        throw new IllegalArgumentException("History cursor indices must be within the text");
      }
    }
  }
}
