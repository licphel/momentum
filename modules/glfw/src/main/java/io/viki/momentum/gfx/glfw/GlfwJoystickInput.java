package io.viki.momentum.gfx.glfw;

import io.viki.momentum.input.GamepadButton;
import io.viki.momentum.input.GamepadState;
import io.viki.momentum.input.InputSnapshot;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import static org.lwjgl.glfw.GLFW.*;

/** GLFW-thread polling for raw joysticks and one selected standard-mapped gamepad. */
final class GlfwJoystickInput {
  private static final GamepadButton[] BUTTONS = GamepadButton.values();
  private static final int[] NATIVE_BUTTONS = {
      GLFW_GAMEPAD_BUTTON_A, GLFW_GAMEPAD_BUTTON_B, GLFW_GAMEPAD_BUTTON_X, GLFW_GAMEPAD_BUTTON_Y,
      GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, GLFW_GAMEPAD_BUTTON_BACK,
      GLFW_GAMEPAD_BUTTON_START, GLFW_GAMEPAD_BUTTON_GUIDE, GLFW_GAMEPAD_BUTTON_LEFT_THUMB,
      GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, GLFW_GAMEPAD_BUTTON_DPAD_UP, GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,
      GLFW_GAMEPAD_BUTTON_DPAD_DOWN, GLFW_GAMEPAD_BUTTON_DPAD_LEFT
  };

  void poll(InputSnapshot input, boolean focused) {
    if (!focused) {
      input.gamepad().reset();
      for (int i = 0; i < InputSnapshot.JOYSTICK_SLOTS; i++) input.joystick(i).reset();
      input.setActiveJoystick(-1);
      return;
    }
    int first = -1;
    int mapped = -1;
    for (int slot = 0; slot < InputSnapshot.JOYSTICK_SLOTS; slot++) {
      if (!glfwJoystickPresent(slot)) { input.joystick(slot).reset(); continue; }
      readRaw(input, slot);
      if (first < 0) first = slot;
      if (mapped < 0 && glfwJoystickIsGamepad(slot)) mapped = slot;
    }
    int selected = input.selectedJoystick();
    if (selected < 0) {
      int active = input.activeJoystick();
      selected = active >= 0 && input.joystick(active).connected() ? active : mapped >= 0 ? mapped : first;
    }
    input.setActiveJoystick(selected);
    if (selected < 0 || !input.joystick(selected).connected() || !glfwJoystickIsGamepad(selected)) {
      input.gamepad().reset();
      return;
    }
    try (MemoryStack stack = MemoryStack.stackPush()) {
      GLFWGamepadState state = GLFWGamepadState.malloc(stack);
      if (!glfwGetGamepadState(selected, state)) { input.gamepad().reset(); return; }
      apply(state, input.gamepad());
    }
  }

  private void readRaw(InputSnapshot input, int slot) {
    FloatBuffer axes = glfwGetJoystickAxes(slot);
    ByteBuffer buttons = glfwGetJoystickButtons(slot);
    ByteBuffer hats = glfwGetJoystickHats(slot);
    int axisCount = axes == null ? 0 : axes.remaining();
    int buttonCount = buttons == null ? 0 : buttons.remaining();
    int hatCount = hats == null ? 0 : hats.remaining();
    var raw = input.joystick(slot);
    if (!raw.connected() || raw.axisCount() != axisCount || raw.buttonCount() != buttonCount || raw.hatCount() != hatCount) {
      String name = glfwGetJoystickName(slot);
      String guid = glfwGetJoystickGUID(slot);
      raw.connect(name == null ? "" : name, guid == null ? "" : guid, axisCount, buttonCount, hatCount);
    }
    if (axes != null) for (int i = 0; i < axisCount; i++) raw.setAxis(i, axes.get(i));
    if (buttons != null) for (int i = 0; i < buttonCount; i++) raw.setButton(i, buttons.get(i) == GLFW_PRESS);
    if (hats != null) for (int i = 0; i < hatCount; i++) raw.setHat(i, hats.get(i));
  }

  static void apply(GLFWGamepadState source, GamepadState target) {
    target.setConnected(true);
    for (int i = 0; i < BUTTONS.length; i++) target.setButton(BUTTONS[i], source.buttons(NATIVE_BUTTONS[i]) == GLFW_PRESS);
    target.setLeftStick(source.axes(GLFW_GAMEPAD_AXIS_LEFT_X), -source.axes(GLFW_GAMEPAD_AXIS_LEFT_Y));
    target.setRightStick(source.axes(GLFW_GAMEPAD_AXIS_RIGHT_X), -source.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y));
    target.setTriggers((source.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) + 1) * 0.5F,
        (source.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) + 1) * 0.5F);
  }
}
