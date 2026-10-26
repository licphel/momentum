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

package io.viki.momentum.codec.nbt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.viki.momentum.codec.nbt.primitives.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON serializer and deserializer for NBT structures.
 *
 * <p>Converts {@link CompoundNBT} to/from JSON text using Jackson.
 * Supports nested structures, arrays, and all NBT primitive types.
 *
 * <h2>Format mapping</h2>
 * <pre>
 * NBT type     -&gt; JSON type
 * BYTE         -&gt; number
 * SHORT        -&gt; number
 * INT          -&gt; number
 * LONG         -&gt; number (preserves all 64 bits when read back by this parser)
 * FLOAT        -&gt; number
 * DOUBLE       -&gt; number
 * BOOLEAN      -&gt; boolean
 * STRING       -&gt; string
 * BYTE_ARRAY   -&gt; array of numbers
 * LIST         -&gt; array
 * COMPOUND     -&gt; object
 * NULL         -&gt; null
 * </pre>
 */
public final class JsonUtil {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonUtil() {
  }

  /**
   * Serializes an NBT to a JSON string.
   *
   * @param value  the NBT to serialize
   * @param pretty whether to pretty-print with indentation
   * @return the JSON string representation
   */
  public static String dump(NBT value, boolean pretty) {
    JsonNode root = valueToJsonNode(value);
    try {
      if (pretty) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
      }
      return MAPPER.writeValueAsString(root);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize NBT to JSON", e);
    }
  }

  /**
   * Serializes an NBT to a pretty-printed JSON string.
   *
   * @param value the NBT to serialize
   * @return the formatted JSON string
   */
  public static String dumpPrettily(NBT value) {
    return dump(value, true);
  }

  /**
   * Parses a JSON string into an NBT compound.
   *
   * @param json the JSON string to parse
   * @return the parsed NBT compound
   * @throws IllegalArgumentException if the JSON is malformed or the root is not an object
   */
  public static CompoundNBT parse(String json) {
    NBT value = parseValue(json);
    if (!(value instanceof CompoundNBT compound)) {
      throw new IllegalArgumentException("Root must be a JSON object");
    }
    return compound;
  }

  /**
   * Parses any JSON value; JSON null is represented by NullNBT.
   *
   * @param json the JSON string to parse
   * @return an NBT value
   */
  public static NBT parseValue(String json) {
    try {
      JsonNode root = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readTree(json);
      if (root == null) {
        throw new IllegalArgumentException("Expected a JSON value");
      }
      return jsonNodeToValue(root);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
    }
  }

  private static ObjectNode toJsonNode(CompoundNBT compound) {
    ObjectNode node = MAPPER.createObjectNode();
    for (Map.Entry<String, NBT> entry : compound.entrySet()) {
      node.set(entry.getKey(), valueToJsonNode(entry.getValue()));
    }
    return node;
  }

  private static JsonNode valueToJsonNode(@Nullable NBT value) {
    if (value == null) {
      return MAPPER.nullNode();
    }

    return switch (value.dataType()) {
      case BYTE -> MAPPER.getNodeFactory().numberNode(((ByteNBT) value).get());
      case SHORT -> MAPPER.getNodeFactory().numberNode(((ShortNBT) value).get());
      case INT -> MAPPER.getNodeFactory().numberNode(((IntNBT) value).get());
      case LONG -> MAPPER.getNodeFactory().numberNode(((LongNBT) value).get());
      case FLOAT -> MAPPER.getNodeFactory().numberNode(((FloatNBT) value).get());
      case DOUBLE -> MAPPER.getNodeFactory().numberNode(((DoubleNBT) value).get());
      case BOOLEAN -> MAPPER.getNodeFactory().booleanNode(((BooleanNBT) value).get());
      case STRING -> MAPPER.getNodeFactory().textNode(((StringNBT) value).get());
      case BYTE_ARRAY -> {
        byte[] bytes = ((ByteArrayNBT) value).get();
        ArrayNode arr = MAPPER.createArrayNode();
        for (byte b : bytes) {
          arr.add(b & 0xFF);
        }
        yield arr;
      }
      case COMPOUND -> toJsonNode((CompoundNBT) value);
      case LIST -> {
        ArrayNode arr = MAPPER.createArrayNode();
        for (NBT elem : (ListNBT) value) {
          arr.add(valueToJsonNode(elem));
        }
        yield arr;
      }
      case NULL, END -> MAPPER.nullNode();
      default -> throw new IllegalArgumentException("Unsupported NBT type: " + value.dataType());
    };
  }

  private static CompoundNBT fromJsonNode(JsonNode node) {
    if (!node.isObject()) {
      throw new IllegalArgumentException("Expected JSON object, got: " + node.getNodeType());
    }
    CompoundNBT compound = new CompoundNBT();
    Iterator<String> names = node.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      // Insert directly into the map: JSON keys may legitimately start with '$'
      // (written via the "$$" escape), and put() would re-parse them as paths.
      compound.directPut(key, jsonNodeToValue(node.get(key)));
    }
    return compound;
  }

  private static NBT jsonNodeToValue(JsonNode node) {
    return switch (node.getNodeType()) {
      case NULL -> NullNBT.INSTANCE;
      case STRING -> new StringNBT(node.asText());
      case BOOLEAN -> new BooleanNBT(node.asBoolean());
      case NUMBER -> {
        if (node.isShort()) {
          yield new ShortNBT(node.shortValue());
        }
        if (node.isInt()) {
          yield new IntNBT(node.intValue());
        }
        if (node.isLong()) {
          yield new LongNBT(node.longValue());
        }
        if (node.isFloat()) {
          yield new FloatNBT(node.floatValue());
        }
        yield new DoubleNBT(node.doubleValue());
      }
      case ARRAY -> {
        ListNBT list = new ListNBT();
        for (JsonNode elem : node) {
          list.add(jsonNodeToValue(elem));
        }
        yield list;
      }
      case OBJECT -> fromJsonNode(node);
      default -> throw new IllegalArgumentException("Unsupported JSON node type: " + node.getNodeType());
    };
  }
}
