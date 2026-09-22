/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyBinding;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.KeyMatch;
import io.viki.momentum.input.event.FocusEvent;
import io.viki.momentum.input.event.KeyEvent;
import io.viki.momentum.input.event.MouseButtonEvent;
import io.viki.momentum.input.event.MouseMoveEvent;
import io.viki.momentum.input.event.ResizeEvent;
import io.viki.momentum.input.event.ScrollEvent;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ResolutionTest {
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
  void resolvesScaleThresholds() {
    assertEquals(1.0F, Resolution.auto(800, 450, false, HANDLER).scale());
    assertEquals(1.0F, Resolution.auto(799, 449, true, HANDLER).scale());
    assertEquals(1.5F, Resolution.auto(1000, 560, false, HANDLER).scale());
    assertEquals(1.5F, Resolution.auto(1200, 675, false, HANDLER).scale());
    assertEquals(2.0F, Resolution.auto(1200, 675, true, HANDLER).scale());
  }

  @Test
  void screenLogicalConversionUsesTopLeftCoordinatesAndRoundTrips() {
    Resolution mapping = Resolution.fixed(1920, 1080, 1.5F, HANDLER);
    Vector2 screen = new Vector2(1234.5F, 765.25F);
    Vector2 logical = mapping.screenToLogical(screen);
    Vector2 roundTrip = mapping.logicalToScreen(logical);
    assertEquals(screen.x(), roundTrip.x(), 0.001F);
    assertEquals(screen.y(), roundTrip.y(), 0.001F);

    Vector2 input = mapping.logicalToInput(logical, 1280.0, 720.0);
    Vector2 inputRoundTrip = mapping.inputToLogical(input.x(), input.y(), 1280.0, 720.0);
    assertEquals(logical.x(), inputRoundTrip.x(), 0.001F);
    assertEquals(logical.y(), inputRoundTrip.y(), 0.001F);
    assertThrows(IllegalArgumentException.class,
        () -> mapping.inputToLogical(1.0, 1.0, 0.0, 720.0));

    Resolution exact = Resolution.fixed(800, 450, 1.0F, HANDLER);
    assertEquals(Vector2.ZERO, exact.screenToLogical(0.0, 0.0));
    assertEquals(new Vector2(800.0F, 450.0F), exact.screenToLogical(800.0, 450.0));
    assertTrue(exact.camera().isYFlipped());
  }

  @Test
  void resizeKeepsLogicalSizeAndCamera() {
    Resolution resolution = Resolution.auto(2560, 1080, false, HANDLER);
    var camera = resolution.camera();
    assertTrue(resolution.viewport().minY() < 0.0F);

    resolution.resize(1080, 2560);

    assertSame(camera, resolution.camera());
    assertEquals(new Vector2(800.0F, 450.0F), resolution.logicalSize());
    assertTrue(resolution.viewport().minX() < 0.0F);
  }

  @Test
  void viewEventBusConvertsInputAndDispatchesLocalCallbacks() {
    TestView view = new TestView(1600, 900, 800, 450);
    try (Canvas canvas = Canvas.open(view, HANDLER)) {
      PartHost host = new PartHost(Rectangle.of(100, 100, 200, 100));
      Probe probe = new Probe(Rectangle.of(10, 10, 80, 30));
      host.addChild(probe);
      canvas.add(host);

      view.eventBus().post(new MouseMoveEvent(115.0, 116.0));
      view.eventBus().post(new ScrollEvent(2.0, -3.0, 115.0, 116.0));

      assertEquals(5.0F, probe.mouseX, 0.001F);
      assertEquals(6.0F, probe.mouseY, 0.001F);
      assertEquals(2.0, probe.scrollX);
      assertEquals(-3.0, probe.scrollY);
      assertSame(canvas.resolution(), canvas.context().resolution());
      assertEquals(new Vector2(800.0F, 450.0F), canvas.context().getInputSize());

      view.eventBus().post(new ResizeEvent(1200, 675));
      assertEquals(1200, canvas.resolution().width());
      assertEquals(675, canvas.resolution().height());
    }
  }

  @Test
  void barePrimaryContextCanBeDrivenWithoutView() {
    PrimaryContext context = new PrimaryContext(800, 450, HANDLER);
    AtomicInteger clicks = new AtomicInteger();
    try (Canvas canvas = new Canvas(context)) {
      Button button = new Button(Rectangle.of(10, 20, 100, 30));
      button.setOnClick(clicks::incrementAndGet);
      canvas.add(button);

      context.dispatchMouseMove(20.0, 30.0);
      context.dispatchMouseButton(KeyCode.MOUSE_LEFT, KeyAction.PRESS, 20.0, 30.0, 0);
      context.dispatchMouseButton(KeyCode.MOUSE_LEFT, KeyAction.RELEASE, 20.0, 30.0, 0);

      assertEquals(1, clicks.get());
      assertSame(button, canvas.focusedElement());
      assertEquals(new Vector2(800.0F, 450.0F), context.getInputSize());
    }
  }

  @Test
  void buttonCapturesPointerAndReceivesFocusedKeyboardInput() {
    TestView view = new TestView(800, 450, 800, 450);
    AtomicInteger clicks = new AtomicInteger();
    PrimaryContext context = new PrimaryContext(view, HANDLER);
    try (Canvas canvas = new Canvas(context)) {
      Button button = new Button(Rectangle.of(10, 20, 100, 30));
      button.setOnClick(clicks::incrementAndGet);
      canvas.add(button);

      postMouse(view, KeyAction.PRESS, 20.0, 30.0);
      postMouse(view, KeyAction.RELEASE, 200.0, 200.0);
      assertEquals(0, clicks.get());

      postMouse(view, KeyAction.PRESS, 20.0, 30.0);
      postMouse(view, KeyAction.RELEASE, 20.0, 30.0);
      assertEquals(1, clicks.get());
      assertSame(button, canvas.focusedElement());

      view.eventBus().post(new KeyEvent(KeyCode.ENTER, KeyAction.PRESS, 0));
      view.eventBus().post(new KeyEvent(KeyCode.ENTER, KeyAction.RELEASE, 0));
      assertEquals(2, clicks.get());

      view.eventBus().post(new FocusEvent(false));
      assertTrue(canvas.focusedElement() == null);
      assertEquals(Button.State.IDLE, button.state());
    }
  }

  @Test
  void buttonUsesConfiguredKeyBindingAndFollowsRuntimeRebinding() {
    PrimaryContext context = new PrimaryContext(800, 450, HANDLER);
    KeyBinding activation = new KeyBinding("test.button.activate",
        KeyMatch.of(context.snapshot().key(KeyCode.F1)));
    AtomicInteger clicks = new AtomicInteger();
    try (Canvas canvas = new Canvas(context)) {
      Button button = new Button(Rectangle.of(10, 20, 100, 30));
      button.setActivationBinding(activation);
      button.setOnClick(clicks::incrementAndGet);
      canvas.add(button);
      canvas.requestFocus(button);

      context.dispatchKey(KeyCode.ENTER, KeyAction.PRESS, 0);
      context.dispatchKey(KeyCode.ENTER, KeyAction.RELEASE, 0);
      assertEquals(0, clicks.get());

      context.dispatchKey(KeyCode.F1, KeyAction.PRESS, 0);
      context.dispatchKey(KeyCode.F1, KeyAction.RELEASE, 0);
      assertEquals(1, clicks.get());

      activation.rebind(KeyMatch.of(context.snapshot().key(KeyCode.SPACE)));
      context.dispatchKey(KeyCode.F1, KeyAction.PRESS, 0);
      context.dispatchKey(KeyCode.F1, KeyAction.RELEASE, 0);
      context.dispatchKey(KeyCode.SPACE, KeyAction.PRESS, 0);
      context.dispatchKey(KeyCode.SPACE, KeyAction.RELEASE, 0);
      assertEquals(2, clicks.get());
    }
  }

  @Test
  void keyboardReleaseStaysCapturedWhenFocusChanges() {
    PrimaryContext context = new PrimaryContext(800, 450, HANDLER);
    KeyBinding activation = new KeyBinding("test.button.activate",
        KeyMatch.of(context.snapshot().key(KeyCode.F1)));
    AtomicInteger firstClicks = new AtomicInteger();
    AtomicInteger secondClicks = new AtomicInteger();
    try (Canvas canvas = new Canvas(context)) {
      Button first = new Button(Rectangle.of(10, 20, 100, 30));
      Button second = new Button(Rectangle.of(120, 20, 100, 30));
      first.setActivationBinding(activation);
      second.setActivationBinding(activation);
      first.setOnClick(firstClicks::incrementAndGet);
      second.setOnClick(secondClicks::incrementAndGet);
      canvas.add(first);
      canvas.add(second);
      canvas.requestFocus(first);

      context.dispatchKey(KeyCode.F1, KeyAction.PRESS, 0);
      canvas.requestFocus(second);
      context.dispatchKey(KeyCode.F1, KeyAction.RELEASE, 0);

      assertEquals(1, firstClicks.get());
      assertEquals(0, secondClicks.get());
    }
  }

  @Test
  void internalPartsWinHitTestingAndCloseDeregistersCallbacks() {
    TestView view = new TestView(800, 450, 800, 450);
    PrimaryContext context = new PrimaryContext(view, HANDLER);
    AtomicInteger partClicks = new AtomicInteger();
    AtomicInteger childClicks = new AtomicInteger();
    PartHost host = new PartHost(Rectangle.of(100, 100, 200, 100));
    Button part = new Button(Rectangle.of(10, 10, 80, 30));
    Button child = new Button(Rectangle.of(10, 10, 80, 30));
    part.setOnClick(partClicks::incrementAndGet);
    child.setOnClick(childClicks::incrementAndGet);
    host.attachPart(part);
    host.addChild(child);
    Canvas canvas = new Canvas(context);
    canvas.add(host);

    postMouse(view, KeyAction.PRESS, 115.0, 115.0);
    postMouse(view, KeyAction.RELEASE, 115.0, 115.0);
    assertEquals(1, partClicks.get());
    assertEquals(0, childClicks.get());
    assertEquals(110.0F, part.absoluteBounds().minX(), 0.0F);
    assertThrows(IllegalArgumentException.class, () -> host.addChild(part));
    assertThrows(IllegalArgumentException.class, () -> part.addChild(host));

    canvas.close();
    view.eventBus().post(new ResizeEvent(320, 180));
    postMouse(view, KeyAction.PRESS, 115.0, 115.0);
    postMouse(view, KeyAction.RELEASE, 115.0, 115.0);
    assertEquals(800, canvas.resolution().width());
    assertEquals(1, partClicks.get());
    assertThrows(IllegalStateException.class,
        () -> canvas.context().dispatchMouseMove(1.0, 1.0));
  }

  private static void postMouse(TestView view, KeyAction action, double x, double y) {
    view.eventBus().post(new MouseButtonEvent(KeyCode.MOUSE_LEFT, action, x, y, 0));
  }

  private static final class PartHost extends Element {
    private PartHost(Rectangle bounds) {
      super(bounds);
    }

    private void attachPart(Element part) {
      addPart(part);
    }
  }

  private static final class Probe extends Element {
    private float mouseX;
    private float mouseY;
    private double scrollX;
    private double scrollY;

    private Probe(Rectangle bounds) {
      super(bounds);
    }

    @Override
    public boolean onMouseMove(float x, float y) {
      mouseX = x;
      mouseY = y;
      return true;
    }

    @Override
    public boolean onScroll(float x, float y, double deltaX, double deltaY) {
      mouseX = x;
      mouseY = y;
      scrollX = deltaX;
      scrollY = deltaY;
      return true;
    }
  }

  private static final class TestView extends View {
    private final Vector2 inputSize;

    private TestView(int framebufferWidth, int framebufferHeight, int inputWidth, int inputHeight) {
      setSize(new Vector2(framebufferWidth, framebufferHeight));
      inputSize = new Vector2(inputWidth, inputHeight);
    }

    @Override
    public Vector2 getInputSize() {
      return inputSize;
    }

    @Override
    public Object procAddress() {
      return this;
    }

    @Override
    public boolean shouldClose() {
      return false;
    }

    @Override
    public void present() {
    }

    @Override
    protected void onInitialize() {
    }

    @Override
    protected void onClose() {
    }

    @Override
    protected void onPollEvents() {
    }

    @Override
    protected void applyPlatformSize(int width, int height) {
    }

    @Override
    protected void applyPlatformVsync(boolean vsync) {
    }
  }
}
