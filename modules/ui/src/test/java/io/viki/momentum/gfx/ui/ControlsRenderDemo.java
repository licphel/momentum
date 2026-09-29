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

import io.viki.momentum.ai.LanguageModel;
import io.viki.momentum.ai.LanguageModelInfo;
import io.viki.momentum.ai.Request;
import io.viki.momentum.ai.ResponseCallback;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.glfw.GlfwDesktopView;
import io.viki.momentum.gfx.opengl.OpenGLDevice;
import io.viki.momentum.gfx.pass.RenderPass;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.QuadGradient;
import io.viki.momentum.gfx.ui.element.*;
import io.viki.momentum.gfx.util.ZeroCopyVertexStore;
import io.viki.momentum.gfx.util.impl.BatchedGraphics;
import io.viki.momentum.gfx.ui.render.UiRenderDispatcher;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.util.Loop;

import java.util.List;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Launches an interactive rendering smoke test for the standard UI controls.
 *
 * <p>The demo creates several windows and exercises editing, selection, scrolling, discrete and
 * numeric sliders, menus, checkboxes, tooltips, and focus routing. It must be run on a desktop UI
 * thread with an available OpenGL device.
 */
public final class ControlsRenderDemo {
  private static final Color PAGE = new Color(0.018F, 0.020F, 0.026F, 1.0F);
  private static final Color BACKDROP_LOW = new Color(0.12F, 0.13F, 0.16F, 0.22F);
  private static final Color BACKDROP_MID = new Color(0.35F, 0.37F, 0.42F, 0.14F);
  private static final Color BACKDROP_HIGH = new Color(0.70F, 0.73F, 0.80F, 0.09F);

  private ControlsRenderDemo() {
  }

  /**
   * Opens the demo window and runs the control rendering loop until the window closes.
   *
   * @param args command-line arguments, currently ignored
   */
  public static void main(String[] args) {
    Optional<LanguageModel> aiModel = loadAiModel(args);
    Queue<Runnable> uiActions = new ConcurrentLinkedQueue<>();
    GlfwDesktopView view = new GlfwDesktopView();
    view.setTitle("Momentum UI controls rendering test");
    view.setSize(new Vector2(1000.0F, 620.0F));
    view.initialize();

    try (Device device = new OpenGLDevice()) {
      device.load(view);
      try (
          BatchedGraphics graphics = new BatchedGraphics(new ZeroCopyVertexStore(true), device);
          UiRenderDispatcher renderer = UiRenderDispatcher.create(device);
          Canvas canvas = Canvas.open(view, device.getTransformHandler(), 800.0F, 450.0F,
               false, renderer)) {
        populate(canvas, aiModel, uiActions);
        Loop.launch(20, () -> {
          drainUiActions(uiActions);
        }, () -> {
          view.pollEvents();
          graphics.begin(RenderPass.DEFAULT);
          canvas.draw(graphics);
          graphics.end();
          device.pollEvents();
          device.execute();
          view.present();
          view.setTitle("UI Test | FPS=" + Loop.fps());
          view.snapshot().clearFrameState();

          if (view.shouldClose()) {
            Loop.stop();
          }
        });
      }
    } finally {
      aiModel.ifPresent(LanguageModel::close);
      view.close();
      GlfwDesktopView.terminate();
    }
  }

  private static void drawBackground(BatchedGraphics graphics, Texture image) {
    float targetWidth = 800.0F;
    float targetHeight = 450.0F;
    float sourceWidth = image.width();
    float sourceHeight = image.height();
    float sourceAspect = sourceWidth / sourceHeight;
    float targetAspect = targetWidth / targetHeight;
    Rectangle source;
    if (sourceAspect > targetAspect) {
      float croppedWidth = sourceHeight * targetAspect;
      source = Rectangle.of((sourceWidth - croppedWidth) * 0.5F, 0.0F,
          croppedWidth, sourceHeight);
    } else {
      float croppedHeight = sourceWidth / targetAspect;
      source = Rectangle.of(0.0F, (sourceHeight - croppedHeight) * 0.5F,
          sourceWidth, croppedHeight);
    }
    graphics.setTint(new Color(1.0F, 1.0F, 1.0F, 0.42F));
    graphics.drawTexture(image, Rectangle.of(0.0F, 0.0F, targetWidth, targetHeight), source);
    graphics.setTint(Color.WHITE);
  }

  private static void populate(Canvas canvas, Optional<LanguageModel> aiModel,
      Queue<Runnable> uiActions) {
    Window window = new Window(Rectangle.of(45.0F, 25.0F, 710.0F, 400.0F),
        Literal.of("Draggable simulated window"));
    canvas.add(window);

    Group controls = new Group(Rectangle.of(25.0F, 55.0F, 630.0F, 322.0F),
        LinearLayout.vertical());
    window.addContent(controls);

    Button button = new Button(Rectangle.of(0.0F, 0.0F, 180.0F, 20.0F));
    button.setTooltipDelayMillis(0);
    button.setDefaultTooltip(List.of(Literal.of("HELLO WORLD").with(TextFormat.of().tint(new QuadGradient(Color.WHITE, Color.WHITE, Color.RED, Color.RED)))));
    controls.add(button);

    Group textRow = new Group(Rectangle.of(0.0F, 0.0F, 630.0F, 50.0F),
        LinearLayout.horizontal());
    controls.add(textRow);

    TextBox editor = new TextBox(Rectangle.of(0.0F, 0.0F, 560.0F, 150.0F),
        "Multiline editor with trailing spaces    \nSelect, copy and paste text\n"
            + "This deliberately long line demonstrates horizontal scrolling without wrapping.");
    editor.setWrapText(true);
    editor.setAutomaticTrailingNewline(true);
    editor.setDefaultTooltip(Literal.of("Multiline editor with selection and clipboard shortcuts"));
    ScrollPane editorPane = new ScrollPane(Rectangle.of(0.0F, 0.0F, 300.0F, 50.0F), editor);
    textRow.add(editorPane);

    TextView display = new TextView(Rectangle.of(0.0F, 0.0F, 300.0F, 50.0F),
        Literal.of("Read-only text display\nRich text is rendered by TextView"));
    display.setWrapText(true);
    display.setDefaultTooltip(Literal.of("Rich text display rendered by TextView"));
    textRow.add(display);

    Group sliderRow = new Group(Rectangle.of(0.0F, 0.0F, 630.0F, 20.0F),
        LinearLayout.horizontal());
    controls.add(sliderRow);

    Slider numeric = new Slider(Rectangle.of(0.0F, 0.0F, 300.0F, 20.0F),
        0.0, 100.0, 40.0);
    numeric.setStep(5.0);
    numeric.setDefaultTooltip(Literal.of("Numeric slider: step 5; drag or use the wheel"));
    sliderRow.add(numeric);

    Slider quality = new Slider(Rectangle.of(0.0F, 0.0F, 300.0F, 20.0F),
        List.of(Literal.of("Low"), Literal.of("Medium"), Literal.of("High"), Literal.of("Ultra")), 1);
    quality.setDefaultTooltip(Literal.of("Discrete slider with text choices"));
    sliderRow.add(quality);

    Group choiceRow = new Group(Rectangle.of(0.0F, 0.0F, 630.0F, 20.0F),
        LinearLayout.horizontal());
    controls.add(choiceRow);

    CheckBox checkBox = new CheckBox(Rectangle.of(0.0F, 0.0F, 300.0F, 20.0F),
        Literal.of("Enable feature"), true);
    checkBox.setDefaultTooltip(Literal.of("Checkbox with keyboard activation"));
    choiceRow.add(checkBox);

    DropDown menu = new DropDown(Rectangle.of(0.0F, 0.0F, 300.0F, 20.0F),
        List.of(Literal.of("Balanced"), Literal.of("Performance"), Literal.of("Quality"),
            Literal.of("Cinematic"), Literal.of("Battery"), Literal.of("Experimental")),
        0);
    menu.setDropCount(3);
    menu.setDefaultTooltip(Literal.of("Drop-down menu rendered above sibling content"));
    choiceRow.add(menu);

    TextView status = new TextView(Rectangle.of(0.0F, 0.0F, 630.0F, 20.0F),
        Literal.of("Interact with every control; drag this window by its title bar."));
    controls.add(status);

    button.setOnClick(() -> status.setText(Literal.of("Button clicked")));
    editor.setOnChanged(value -> status.setText(Literal.of("Text: " + value)));
    numeric.setOnChanged(value -> status.setText(Literal.of("Numeric slider: " + value)));
    quality.setOnSelectionChanged(index ->
        status.setText(Literal.of("Quality: " + quality.selectedOption().text())));
    checkBox.setOnChanged(value -> status.setText(Literal.of("Checkbox: " + value)));
    menu.setOnSelectionChanged(index ->
        status.setText(Literal.of("Drop-down: " + menu.selectedOption().text())));

    addNestedWindow(window);
    addAiWindow(canvas, aiModel, uiActions);
  }

  private static void addNestedWindow(Window parent) {
    Window nested = new Window(Rectangle.of(382.0F, 218.0F, 250.0F, 132.0F),
        Literal.of("Nested window"));
    parent.addContent(nested);

    Group content = new Group(Rectangle.of(12.0F, 28.0F, 226.0F, 90.0F),
        LinearLayout.vertical());
    nested.addContent(content);

    Button action = new Button(Rectangle.of(0.0F, 0.0F, 226.0F, 20.0F));
    action.setDefaultTooltip(Literal.of("Button inside a nested window"));
    content.add(action);

    CheckBox option = new CheckBox(Rectangle.of(0.0F, 0.0F, 226.0F, 20.0F),
        Literal.of("Nested option"), true);
    content.add(option);

    TextView note = new TextView(Rectangle.of(0.0F, 0.0F, 226.0F, 20.0F),
        Literal.of("The child window has its own title bar"));
    content.add(note);
  }

  private static void addAiWindow(Canvas canvas, Optional<LanguageModel> aiModel,
      Queue<Runnable> uiActions) {
    Window second = new Window(Rectangle.of(410.0F, 45.0F, 350.0F, 335.0F), Literal.of("Local AI"));
    canvas.add(second);

    Group content = new Group(Rectangle.of(12.0F, 26.0F, 326.0F, 297.0F),
        LinearLayout.vertical());
    second.addContent(content);

    TextBox prompt = new TextBox(Rectangle.of(0.0F, 0.0F, 326.0F, 70.0F), "");
    prompt.setWrapText(true);
    prompt.setPlaceholder("Ask the local model...");
    ScrollPane promptPane = new ScrollPane(Rectangle.of(0.0F, 0.0F, 326.0F, 70.0F), prompt);
    content.add(promptPane);

    Button submit = new Button(Rectangle.of(0.0F, 0.0F, 326.0F, 20.0F));
    submit.setLabel(Literal.of("Send"));
    content.add(submit);

    TextView result = new TextView(Rectangle.of(0.0F, 0.0F, 326.0F, 195.0F),
        Literal.of(aiModel.isPresent()
            ? "Ready"
            : "Pass -PaiModel=<model.gguf> to runControlsDemo"));
    result.setWrapText(true);
    ScrollPane resultPane = new ScrollPane(Rectangle.of(0.0F, 0.0F, 326.0F, 195.0F), result);
    content.add(resultPane);

    submit.setOnClick(() -> submitPrompt(aiModel, uiActions, prompt, result, submit));
    prompt.setOnSubmit(value -> submitPrompt(aiModel, uiActions, prompt, result, submit));
  }

  private static Optional<LanguageModel> loadAiModel(String[] args) {
    return Optional.of(LanguageModel.open(new LanguageModelInfo(Path.of("C:\\Users\\licph\\Downloads\\Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"), 4)));
  }

  private static void submitPrompt(Optional<LanguageModel> aiModel, Queue<Runnable> uiActions,
                                   TextBox prompt, TextView result, Button submit) {
    String text = prompt.text().trim();
    if (text.isEmpty()) {
      result.setText(Literal.of("Enter a prompt first."));
      return;
    }
    if (aiModel.isEmpty()) {
      result.setText(Literal.of("No GGUF model configured."));
      return;
    }

    submit.setEnabled(false);
    result.setText(Literal.of("Thinking..."));
    StringBuilder response = new StringBuilder();
    aiModel.orElseThrow().stream(Request.of(text), new ResponseCallback() {
      @Override
      public void onToken(String token) {
        uiActions.add(() -> {
          response.append(token);
          result.setText(Literal.of(response.toString()));
        });
      }

      @Override
      public void onComplete() {
        uiActions.add(() -> submit.setEnabled(true));
      }

      @Override
      public void onError(Throwable failure) {
        uiActions.add(() -> {
          result.setText(Literal.of("Inference failed: " + failure));
          submit.setEnabled(true);
        });
      }
    });
  }

  private static void drainUiActions(Queue<Runnable> uiActions) {
    Runnable action;
    while ((action = uiActions.poll()) != null) {
      action.run();
    }
  }
}
