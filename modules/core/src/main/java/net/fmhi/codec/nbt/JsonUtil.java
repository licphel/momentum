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

package net.fmhi.codec.nbt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON serializer and deserializer for NBT structures.
 *
 * <p>Converts {@link CompoundTag} to/from JSON text using Jackson.
 * Supports nested structures, arrays, and all NBT primitive types.
 *
 * <h2>Format mapping</h2>
 * <pre>
 * NBT type     -&gt; JSON type
 * BYTE         -&gt; number
 * SHORT        -&gt; number
 * INT          -&gt; number
 * LONG         -&gt; number (string if &gt; 2&lt;sup&gt;53&lt;/sup&gt;-1)
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
  private static final long MAX_SAFE_INTEGER = 9007199254740991L;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final MappingJsonFactory JSON_FACTORY = new MappingJsonFactory();

  private JsonUtil() {
  }

  /**
   * Serializes an NBT compound to a JSON string.
   *
   * @param compound the NBT compound to serialize
   * @param pretty   whether to pretty-print with indentation
   * @return the JSON string representation
   */
  public static String dump(CompoundTag compound, boolean pretty) {
    ObjectNode root = toJsonNode(compound);
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
   * Serializes an NBT compound to a pretty-printed JSON string.
   *
   * @param compound the NBT compound to serialize
   * @return the formatted JSON string
   */
  public static String dumpPrettily(CompoundTag compound) {
    return dump(compound, true);
  }

  /**
   * Parses a JSON string into an NBT compound.
   *
   * @param json the JSON string to parse
   * @return the parsed NBT compound
   * @throws IllegalArgumentException if the JSON is malformed or the root is not an object
   */
  public static CompoundTag parse(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isObject()) {
        throw new IllegalArgumentException("Root must be a JSON object");
      }
      return fromJsonNode(root);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
    }
  }

  private static ObjectNode toJsonNode(CompoundTag compound) {
    ObjectNode node = MAPPER.createObjectNode();
    for (Map.Entry<String, @Nullable Object> entry : compound.entrySet()) {
      node.set(entry.getKey(), valueToJsonNode(entry.getValue()));
    }
    return node;
  }

  private static JsonNode valueToJsonNode(@Nullable Object value) {
    if (value == null) {
      return MAPPER.nullNode();
    }

    return switch (value) {
      case String s -> MAPPER.getNodeFactory().textNode(s);
      case Boolean b -> MAPPER.getNodeFactory().booleanNode(b);
      case Long l -> {
        if (l > MAX_SAFE_INTEGER || l < -MAX_SAFE_INTEGER) {
          yield MAPPER.getNodeFactory().textNode(l.toString());
        }
        yield MAPPER.getNodeFactory().numberNode((long) l);
      }
      case Integer i -> MAPPER.getNodeFactory().numberNode((int) i);
      case Double d -> MAPPER.getNodeFactory().numberNode((double) d);
      case Float f -> MAPPER.getNodeFactory().numberNode((float) f);
      case Short s -> MAPPER.getNodeFactory().numberNode((short) s);
      case Byte b -> MAPPER.getNodeFactory().numberNode((byte) b);
      case Number n -> MAPPER.getNodeFactory().numberNode(n.doubleValue());
      case byte[] bytes -> {
        ArrayNode arr = MAPPER.createArrayNode();
        for (byte b : bytes) {
          arr.add(b & 0xFF);
        }
        yield arr;
      }
      case CompoundTag compound -> toJsonNode(compound);
      case ListTag list -> {
        ArrayNode arr = MAPPER.createArrayNode();
        for (Object elem : list) {
          arr.add(valueToJsonNode(elem));
        }
        yield arr;
      }
      default -> throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    };
  }

  private static CompoundTag fromJsonNode(JsonNode node) {
    if (!node.isObject()) {
      throw new IllegalArgumentException("Expected JSON object, got: " + node.getNodeType());
    }
    CompoundTag compound = new CompoundTag();
    Iterator<String> names = node.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      compound.put(key, jsonNodeToValue(node.get(key)));
    }
    return compound;
  }

  private static @Nullable Object jsonNodeToValue(JsonNode node) {
    return switch (node.getNodeType()) {
      case NULL -> null;
      case STRING -> node.asText();
      case BOOLEAN -> node.asBoolean();
      case NUMBER -> {
        if (node.isInt()) {
          yield node.intValue();
        }
        if (node.isLong()) {
          yield node.longValue();
        }
        yield node.doubleValue();
      }
      case ARRAY -> {
        ListTag list = new ListTag();
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
