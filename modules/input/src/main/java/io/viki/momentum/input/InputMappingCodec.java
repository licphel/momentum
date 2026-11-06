package io.viki.momentum.input;

import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.NBT;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.momentum.util.InternalApi;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable session schema. The action order must match at both endpoints. Thread-safe.
 */
@InternalApi
final class InputMappingCodec implements Codec<HeadlessInputMapping> {
  private static final int WHEEL = 16;
  private static final int VALUE = 32;
  private final Action[] actions;
  private final Map<Action, Integer> indices = new HashMap<>();

  /**
   * Creates a codec for the supplied ordered action schema.
   *
   * @param actions actions whose order defines the binary representation
   * @throws IllegalArgumentException if the schema contains a duplicate action
   */
  InputMappingCodec(Action[] actions) {
    this.actions = actions.clone();
    for (int i = 0; i < actions.length; i++) {
      if (indices.put(actions[i], i) != null) {
        throw new IllegalArgumentException("Duplicate action: " + actions[i]);
      }
    }
  }

  /**
   * Encodes the supported state kind and transient flags into one byte.
   *
   * @param input input state to encode
   * @return packed state flags
   * @throws IllegalArgumentException if {@code input} is not a key or wheel state
   */
  private static int flags(Input input) {
    if (input instanceof Key k) {
      return (k.isDown() ? 1 : 0) | (k.transitioned() ? 2 : 0)
          | (k.repeated() ? 4 : 0) | (k.released() ? 8 : 0) | (k.value() != 0 ? VALUE : 0);
    }
    if (input instanceof Wheel w) {
      return WHEEL | (w.persistent() ? 1 : 0)
          | (w.x() != 0 ? 2 : 0) | (w.y() != 0 ? 4 : 0)
          | (w.deltaX() != 0 ? 8 : 0) | (w.deltaY() != 0 ? VALUE : 0);
    }
    throw new IllegalArgumentException("Unsupported input: " + input);
  }

  /**
   * Reconstructs an immutable key state from packed flags.
   *
   * @param f packed key flags
   * @param value normalized key value
   * @return decoded key state
   */
  private static HeadlessInputMapping.KeyState key(int f, float value) {
    return new HeadlessInputMapping.KeyState(value, (f & 1) != 0, (f & 2) != 0,
        (f & 4) != 0, (f & 8) != 0);
  }

  @Override
  public NBT serialize(HeadlessInputMapping mapping) {
    CompoundNBT tag = new CompoundNBT();
    mapping.inputs.forEach((action, input) -> {
      CompoundNBT entry = new CompoundNBT();
      entry.putByte("flags", (byte) flags(input));
      if (input instanceof Key k) {
        entry.putFloat("value", k.value());
      } else if (input instanceof Wheel w) {
        entry.putDouble("x", w.x());
        entry.putDouble("y", w.y());
        entry.putDouble("dx", w.deltaX());
        entry.putDouble("dy", w.deltaY());
      }
      tag.putCompound(action.name(), entry);
    });
    return tag;
  }

  @Override
  public HeadlessInputMapping deserialize(NBT nbt) {
    if (!(nbt instanceof CompoundNBT tag)) {
      throw new IllegalArgumentException("Expected input compound");
    }
    HeadlessInputMapping result = new HeadlessInputMapping();
    for (Action action : actions) {
      tag.getCompound(action.name()).ifPresent(e -> {
        int f = e.getByte("flags", (byte) 0) & 255;
        result.set(action, (f & WHEEL) == 0 ? key(f, e.getFloat("value", 0F))
            : new HeadlessInputMapping.WheelState(e.getDouble("x", 0D), e.getDouble("y", 0D),
            e.getDouble("dx", 0D), e.getDouble("dy", 0D), (f & 1) != 0));
      });
    }
    return result;
  }

  @Override
  public void serialize(HeadlessInputMapping mapping, BinaryBuffer buffer) {
    buffer.writeVarInt(mapping.inputs.size());
    mapping.inputs.forEach((action, input) -> {
      Integer index = indices.get(action);
      if (index == null) {
        throw new IllegalArgumentException("Action outside input schema: " + action);
      }
      buffer.writeVarInt(index);
      int f = flags(input);
      buffer.write((byte) f);
      if (input instanceof Key k) {
        if ((f & VALUE) != 0) {
          buffer.write((byte) Math.round(Math.max(-1, Math.min(1, k.value())) * 127));
        }
      } else if (input instanceof Wheel w) {
        if ((f & 2) != 0) {
          buffer.writeDoubleAsFloat16(w.x());
        }
        if ((f & 4) != 0) {
          buffer.writeDoubleAsFloat16(w.y());
        }
        if ((f & 8) != 0) {
          buffer.writeDoubleAsFloat16(w.deltaX());
        }
        if ((f & VALUE) != 0) {
          buffer.writeDoubleAsFloat16(w.deltaY());
        }
      }
    });
  }

  @Override
  public HeadlessInputMapping deserialize(BinaryBuffer buffer) {
    int count = buffer.readVarInt();
    if (count < 0 || count > actions.length) {
      throw new IllegalArgumentException("Invalid input count: " + count);
    }
    HeadlessInputMapping result = new HeadlessInputMapping();
    for (int i = 0; i < count; i++) {
      int index = buffer.readVarInt();
      if (index < 0 || index >= actions.length || result.inputs.containsKey(actions[index])) {
        throw new IllegalArgumentException("Invalid or duplicate input index: " + index);
      }
      int f = buffer.read() & 255;
      Input input = (f & WHEEL) == 0 ? key(f, (f & VALUE) == 0 ? 0 : buffer.read() / 127F)
          : new HeadlessInputMapping.WheelState(
          (f & 2) == 0 ? 0 : buffer.readDoubleFromFloat16(),
          (f & 4) == 0 ? 0 : buffer.readDoubleFromFloat16(),
          (f & 8) == 0 ? 0 : buffer.readDoubleFromFloat16(),
          (f & VALUE) == 0 ? 0 : buffer.readDoubleFromFloat16(), (f & 1) != 0);
      result.set(actions[index], input);
    }
    return result;
  }
}
