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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.viki.momentum.gfx.color.Color;
import io.viki.momentum.gfx.input.KeyAction;
import io.viki.momentum.gfx.input.KeyCode;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.math.Matrix4x4;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
    public Matrix4x4 createOrthographic(float left, float right, float bottom, float top, float near, float far) {
      return Matrix4x4.createOrthographic(left, right, bottom, top, near, far);
    }

    @Override
    public Matrix4x4 createPerspective(float fovY, float aspect, float near, float far) {
      return Matrix4x4.createPerspective(fovY, aspect, near, far);
    }

    @Override
    public void flipY(boolean flipY) {
    }

    @Override
    public boolean isYFlipped() {
      return false;
    }
  };

  @Test
  void resolvesScaleThresholds() {
    Resolution base = Resolution.auto(800, 450, HANDLER);
    assertEquals(1.0F, base.scale(), "800x450 reaches scale 1 at the inclusive threshold");
    assertEquals(1.0F, Resolution.auto(799, 449, true, HANDLER).scale(),
        "cover scale rounds up when the framebuffer is just below logical size");

    Resolution next = Resolution.auto(1000, 560, HANDLER);
    assertEquals(1.5F, next.scale(), "1000x560 rounds cover scale to 1.5");

    Resolution half = Resolution.auto(1200, 675, HANDLER);
    Resolution integer = Resolution.auto(1200, 675, true, HANDLER);
    assertEquals(1.5F, half.scale(), "half-step mode reaches 1.5");
    assertEquals(2.0F, integer.scale(), "integer mode rounds cover scale up to 2");
  }

  @Test
  void screenLogicalConversionRoundTrips() {
    Resolution mapping = Resolution.fixed(1920, 1080, 1.5F, HANDLER);
    var screen = new io.viki.momentum.math.Vector2(1234.5F, 765.25F);
    var logical = mapping.screenToLogical(screen);
    var roundTrip = mapping.logicalToScreen(logical);
    assertTrue(Math.abs(roundTrip.x() - screen.x()) < 0.001F && Math.abs(roundTrip.y() - screen.y()) < 0.001F,
        "screen/logical conversion round trips");

    var input = mapping.logicalToInput(logical, 1280.0, 720.0);
    var inputRoundTrip = mapping.inputToLogical(input.x(), input.y(), 1280.0, 720.0);
    assertEquals(logical.x(), inputRoundTrip.x(), 0.001F, "input X conversion round trips");
    assertEquals(logical.y(), inputRoundTrip.y(), 0.001F, "input Y conversion round trips");

    Resolution wide = Resolution.auto(2560, 1080, HANDLER);
    var camera = wide.camera();
    assertTrue(wide.viewport().minY() < 0.0F, "wide framebuffer uses a centered cover viewport");
    var cameraPoint = new io.viki.momentum.math.Vector2(321.5F, 201.25F);
    var cameraRoundTrip = wide.unproject(wide.project(cameraPoint));
    assertEquals(cameraPoint.x(), cameraRoundTrip.x(), 0.01F, "camera project/unproject X round trips");
    assertEquals(cameraPoint.y(), cameraRoundTrip.y(), 0.01F, "camera project/unproject Y round trips");
    wide.resize(1080, 2560);
    assertSame(camera, wide.camera(), "resize reuses the camera");
    assertEquals(800.0F, wide.logicalWidth(), 0.0F, "resize keeps logical width");
    assertEquals(450.0F, wide.logicalHeight(), 0.0F, "resize keeps logical height");
    assertTrue(wide.viewport().minX() < 0.0F, "narrow framebuffer uses a centered cover viewport");
  }

  @Test
  void lookResolvesSuppliersAtUseTimeAndButtonClicks() {
    AtomicInteger calls = new AtomicInteger();
    StyleKey<Color> dynamicKey = new StyleKey<>("dynamic", Color.class);
    StyleKey<Color> emptyKey = new StyleKey<>("empty", Color.class);
    StyleKey<Color> nullKey = new StyleKey<>("ordinary-null", Color.class);
    Look look = new Look();
    Supplier<Color> dynamic = () -> {
      calls.incrementAndGet();
      return Color.RED;
    };
    look.put(dynamicKey, dynamic);
    look.put(emptyKey, (Supplier<Color>) () -> null);
    look.put(nullKey, (Color) null);
    assertEquals(Color.RED, look.get(dynamicKey));
    assertEquals(Color.RED, look.get(dynamicKey));
    assertEquals(2, calls.get(), "supplier is resolved for each use");
    assertTrue(look.get(emptyKey) == null, "null supplier values are allowed");
    assertTrue(look.get(nullKey) == null, "ordinary null assets are allowed");
    look.put(new StyleKey<>("static", Color.class), Color.BLUE);
    assertSame(dynamic, look.assets().get(dynamicKey.name()), "dynamic supplier is stored without wrapping");

    Button button = new Button(io.viki.momentum.math.Box2D.create(10, 20, 100, 30), look);
    AtomicInteger clicks = new AtomicInteger();
    button.setOnClick(clicks::incrementAndGet);
    assertTrue(button.press(new io.viki.momentum.math.Vector2(20, 30)));
    assertTrue(button.release(new io.viki.momentum.math.Vector2(20, 30)));
    assertEquals(1, clicks.get(), "release inside after press invokes click");
    assertTrue(!button.press(new io.viki.momentum.math.Vector2(0, 0)));
  }

  @Test
  void faceCoordinatesAndTreeInputUseInjectedLook() {
    Look initial = new Look();
    UI ui = new UI(Resolution.auto(800, 450, HANDLER), initial);
    assertEquals(800.0F, ui.bounds().width(), 0.0F, "face bounds use logical width");
    assertEquals(450.0F, ui.bounds().height(), 0.0F, "face bounds use logical height");
    Button button = new Button(io.viki.momentum.math.Box2D.create(10, 20, 100, 30), new Look());
    ui.add(button);
    assertSame(initial, button.look(), "adding an element injects the face look");

    Look replacement = new Look();
    ui.setLook(replacement);
    assertSame(replacement, button.look(), "changing face look propagates to elements");
    AtomicInteger clicks = new AtomicInteger();
    button.setOnClick(clicks::incrementAndGet);
    assertTrue(ui.press(new io.viki.momentum.math.Vector2(20, 30)));
    assertTrue(ui.release(new io.viki.momentum.math.Vector2(20, 30)));
    assertEquals(1, clicks.get(), "face dispatches to the topmost button");

    var logical = ui.resolution.screenToLogical(400, 225);
    assertEquals(400.0F, logical.x(), 0.01F);
    assertEquals(225.0F, logical.y(), 0.01F);
    ui.resize(1600, 900);
    assertEquals(800.0F, ui.resolution().logicalWidth(), 0.0F);
    assertEquals(450.0F, ui.resolution().logicalHeight(), 0.0F);
  }

  @Test
  void elementContentAndInternalPartsHaveOneParentAndHitOrder() {
    Look look = new Look();
    PartHost host = new PartHost(io.viki.momentum.math.Box2D.create(100, 100, 200, 100), look);
    AtomicInteger partClicks = new AtomicInteger();
    AtomicInteger childClicks = new AtomicInteger();
    Button part = new Button(io.viki.momentum.math.Box2D.create(10, 10, 80, 30), look);
    part.setOnClick(partClicks::incrementAndGet);
    Button child = new Button(io.viki.momentum.math.Box2D.create(10, 10, 80, 30), look);
    child.setOnClick(childClicks::incrementAndGet);
    host.attachPart(part);
    host.addChild(child);
    EventProbe probe = new EventProbe(io.viki.momentum.math.Box2D.create(10, 10, 80, 30), look);
    host.addChild(probe);
    assertEquals(2, host.children().size());
    assertEquals(1, host.partCount());
    assertThrows(IllegalArgumentException.class, () -> host.addChild(part), "one parent is enforced");
    assertThrows(IllegalArgumentException.class, () -> part.addChild(host), "ancestor cycles are rejected");

    UI ui = new UI(Resolution.auto(800, 450, HANDLER), look);
    ui.add(host);
    assertTrue(ui.dispatch(new UiEvent.Scroll(new io.viki.momentum.math.Vector2(115, 115), 0, 1)));
    Vector2 localPosition = Objects.requireNonNull(probe.lastPosition);
    assertEquals(5.0F, localPosition.x(), 0.0F, "position events are translated to child local space");
    assertEquals(5.0F, localPosition.y(), 0.0F, "position events preserve local origin translation");
    assertTrue(ui.dispatch(new UiEvent.Key(KeyCode.A, KeyAction.PRESS, 0)));
    assertEquals(2, probe.events, "non-position events descend unchanged");
    assertEquals(110.0F, part.absoluteBounds().minX(), 0.0F, "part X is accumulated from parent bounds");
    assertEquals(110.0F, part.absoluteBounds().minY(), 0.0F, "part Y is accumulated from parent bounds");
    assertTrue(ui.press(new io.viki.momentum.math.Vector2(115, 115)));
    assertTrue(ui.release(new io.viki.momentum.math.Vector2(15, 15)), "pressed part captures release outside");
    assertEquals(0, partClicks.get(), "release outside does not click");
    assertTrue(ui.press(new io.viki.momentum.math.Vector2(115, 115)));
    assertTrue(ui.release(new io.viki.momentum.math.Vector2(115, 115)));
    assertEquals(1, partClicks.get(), "internal parts are hit before content children");
    assertEquals(0, childClicks.get());
    assertTrue(!ui.press(new io.viki.momentum.math.Vector2(15, 15)), "local coordinates do not hit globally");

    host.clearChildren();
    assertEquals(1, host.partCount(), "clearChildren retains internal parts");
  }

  private static final class PartHost extends Element {
    private PartHost(io.viki.momentum.math.Box2D bounds, Look look) {
      super(bounds, look);
    }

    private void attachPart(Element part) {
      addPart(part);
    }

    private int partCount() {
      return parts().size();
    }
  }

  private static final class EventProbe extends Element {
    private int events;
    private @Nullable Vector2 lastPosition;

    private EventProbe(io.viki.momentum.math.Box2D bounds, Look look) {
      super(bounds, look);
    }

    @Override
    protected boolean onEvent(UiEvent event) {
      events++;
      if (event instanceof UiEvent.Positioned positioned) {
        lastPosition = positioned.position();
      }
      return !(event instanceof UiEvent.MouseButton);
    }
  }
}
