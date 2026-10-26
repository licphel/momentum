package io.viki.momentum.codec;

import io.viki.momentum.codec.nbt.ListNBT;
import io.viki.momentum.codec.nbt.NBT;
import io.viki.momentum.codec.nbt.primitives.ByteArrayNBT;

import java.util.Objects;

/** Primitive array conversion without intermediate boxed collections. Stateless and thread-safe. */
final class PrimitiveArrayCodecs {
  static final Codec<byte[]> BYTE = Codec.of(ByteArrayNBT::new, tag -> {
    if (tag instanceof ByteArrayNBT bytes) {
      return bytes.get();
    }
    ListNBT list = ListNBT.CODEC.deserialize(tag);
    byte[] result = new byte[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = read(Codec.BYTE, list, i);
    }
    return result;
  });
  static final Codec<int[]> INT = Codec.of(values -> {
    ListNBT list = new ListNBT();
    for (int value : values) {
      list.addInt(value);
    }
    return list;
  }, tag -> {
    ListNBT list = ListNBT.CODEC.deserialize(tag);
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = read(Codec.INT, list, i);
    }
    return result;
  });
  static final Codec<long[]> LONG = Codec.of(values -> {
    ListNBT list = new ListNBT();
    for (long value : values) {
      list.addLong(value);
    }
    return list;
  }, tag -> {
    ListNBT list = ListNBT.CODEC.deserialize(tag);
    long[] result = new long[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = read(Codec.LONG, list, i);
    }
    return result;
  });
  static final Codec<float[]> FLOAT = Codec.of(values -> {
    ListNBT list = new ListNBT();
    for (float value : values) {
      list.addFloat(value);
    }
    return list;
  }, tag -> {
    ListNBT list = ListNBT.CODEC.deserialize(tag);
    float[] result = new float[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = read(Codec.FLOAT, list, i);
    }
    return result;
  });
  static final Codec<double[]> DOUBLE = Codec.of(values -> {
    ListNBT list = new ListNBT();
    for (double value : values) {
      list.addDouble(value);
    }
    return list;
  }, tag -> {
    ListNBT list = ListNBT.CODEC.deserialize(tag);
    double[] result = new double[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = read(Codec.DOUBLE, list, i);
    }
    return result;
  });

  private PrimitiveArrayCodecs() {
  }

  private static <T> T read(Codec<T> codec, ListNBT list, int index) {
    try {
      NBT value = Objects.requireNonNull(list.get(index));
      return codec.deserialize(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Element [" + index + "]: " + exception.getMessage(), exception);
    }
  }
}

