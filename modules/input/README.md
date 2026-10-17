# Instance-owned input

Each view owns an `InputSnapshot`. Bind actions on that snapshot; the GLFW backend
forwards input changes and advances repeat timing once per poll. No global input
instance is required.

```java
InputBinding move = input.binding("player.move",
    BindingDefinition.of("player.move.keyboard", "<Keyboard>/d"),
    BindingDefinition.of("player.move.gamepad", "<Gamepad>/leftStick/x"));

boolean held = move.isDown();
boolean pressed = move.transitioned();
boolean released = move.released();
float value = move.value();
```

Digital controls produce 0 or 1; signed axes retain their value in [-1, 1].
Directional controls such as `<Gamepad>/leftStick/left` produce [0, 1]. Analog
press/release thresholds have hysteresis; values remain available below the
digital threshold. Queries do not consume transitions. A press followed by a
release within one frame retains both edges until `clearFrameState()`.

An alternative's first control supplies its value; subsequent controls are chord
requirements. Alternatives for one binding are OR-combined, retaining the value
with greatest absolute magnitude. Bindings never consume controls.
Use `binding.group("player")` and `input.setGroupEnabled("player", false)`
to disable a group, or `binding.setEnabled(false)` for one binding.
Groups belong to the snapshot, affect only logical bindings, and do not hide raw input.

Profiles store only overrides keyed by stable action IDs, not transient key
state or device-slot numbers. Use `loadBindings(path)`, `saveBindings(path)`,
`rebind(actionId, alternatives...)`, and `resetBinding(actionId)`. Overrides
replace the action's complete alternative list, allowing added or removed keys.
An empty list unbinds an action. Unknown action IDs survive unavailable mods.

`InputRebind` captures a chord, ignoring initially held controls until released.
Apply the returned definition explicitly. Labels are caller-owned translations
or glyphs via `BindingDefinition.displayText` and `ControlPath.translationKey`.

Mapped gamepads use semantic paths such as `<Gamepad>/buttonSouth`; unmapped
joysticks support `<Joystick>/button/0`, `<Joystick>/axis/0/negative`, and
`<Joystick>/hat/0/up`. Device selection is automatic by default or explicit via
`selectJoystick(slot)`. Slots are session-local.

All runtime objects are confined to the owning input thread. A custom backend
updates the devices directly: `keyboard().onKeyChange`, `mouse().onButtonChange`,
`mouse().onMove`, `mouse().onScroll`, and the gamepad/joystick setters.
Device changes update logical bindings immediately; call `updateBindings(deltaSeconds)`
once per frame to advance repeat timing. Finish the frame with
`clearFrameState()`. On focus loss, call `onFocusChange(false)`.

Raw polling is independent of bindings: `input.keyboard().isDown(key)`,
`input.keyboard().isTransitioned(key)`, `input.keyboard().isReleased(key)`, and
`input.keyboard().isRepeated(key)` inspect keyboard state directly.
Use `input.mouse()` with `MouseButton` for buttons, pointer position and wheel.
Gamepad buttons have down, transition and release queries on `gamepad()`. Unmapped
joysticks expose those queries by button index on `joystick()`; axes and hats
remain directly readable. Keyboard and mouse codes are separate types; old
Snapshot polling adapters and Key wrappers have been removed.

Headless regression entry point: `io.viki.momentum.input.InputDemo`.
