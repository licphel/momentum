package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.input.InputModifiers;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Single-line command input with bounded submission history.
 *
 * <p>Up/down traverse commands and restore the draft when returning past the newest entry. Enter
 * remembers nonblank input, clears the editor, then invokes the submission consumer once per press.
 * Control-left/right are left to the host for completion paging rather than moving the caret.
 * This control does not parse commands, render output, or own a completion provider.
 */
public final class CommandBox extends TextBox {
  private static final int MAX_COMMAND_HISTORY = 128;
  private final List<String> commands = new ArrayList<>();
  private int historyIndex;
  private String draft = "";
  private @Nullable Consumer<String> onSubmit;

  /**
   * Creates an empty command line with the normal TextBox presentation.
   *
   * @param bounds initial bounds
   */
  public CommandBox(Rectangle bounds) {
    super(bounds, "");
    super.setMultiline(false);
    super.setWrapText(false);
    super.setOnSubmit(this::submit);
  }

  /** Command input is single-line; enabling multiline editing is rejected. */
  @Override
  public void setMultiline(boolean value) {
    if (value) {
      throw new IllegalArgumentException("CommandBox only supports single-line input");
    }
    super.setMultiline(false);
  }

  /** Command input scrolls horizontally instead of wrapping into additional visual lines. */
  @Override
  public void setWrapText(boolean value) {
    if (value) {
      throw new IllegalArgumentException("CommandBox does not wrap text");
    }
    super.setWrapText(false);
  }

  /** Installs the consumer invoked after a nonblank command is remembered and the editor is cleared. */
  @Override
  public void setOnSubmit(@Nullable Consumer<String> consumer) {
    onSubmit = consumer;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled() || action == KeyAction.RELEASE) {
      return false;
    }
    if ((modifiers & InputModifiers.CONTROL) != 0 && (key == KeyCode.LEFT || key == KeyCode.RIGHT)) {
      return false;
    }
    if (editable() && (key == KeyCode.UP || key == KeyCode.DOWN)) {
      navigate(key == KeyCode.UP ? -1 : 1);
      return true;
    }
    if (key == KeyCode.ENTER || key == KeyCode.KP_ENTER) {
      if (editable() && action == KeyAction.PRESS) {
        return super.onKey(key, action, modifiers);
      }
      return true;
    }
    return super.onKey(key, action, modifiers);
  }

  /**
   * Returns an immutable snapshot of submitted commands, oldest first.
   *
   * @return the history command list
   */
  public List<String> commandHistory() {
    return List.copyOf(commands);
  }

  /**
   * Restores a history snapshot, retaining its newest 128 entries and the current input as the draft.
   *
   * @param history the history to set
   */
  public void setCommandHistory(List<String> history) {
    List<String> retained = history.subList(Math.max(0, history.size() - MAX_COMMAND_HISTORY), history.size());
    commands.clear();
    commands.addAll(retained);
    historyIndex = commands.size();
    draft = text();
  }

  private void submit(String value) {
    if (value.isBlank()) {
      setText("");
      select(0, 0);
      return;
    }
    if (commands.isEmpty() || !commands.getLast().equals(value)) {
      if (commands.size() == MAX_COMMAND_HISTORY) {
        commands.removeFirst();
      }
      commands.add(value);
    }
    historyIndex = commands.size();
    draft = "";
    setText("");
    select(0, 0);
    if (onSubmit != null) {
      onSubmit.accept(value);
    }
  }

  private void navigate(int direction) {
    if (historyIndex == commands.size()) {
      draft = text();
    }
    historyIndex = Math.clamp(historyIndex + direction, 0, commands.size());
    String value = historyIndex == commands.size() ? draft : commands.get(historyIndex);
    setText(value);
    select(value.length(), value.length());
  }
}
