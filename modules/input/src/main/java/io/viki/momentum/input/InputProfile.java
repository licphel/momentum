package io.viki.momentum.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.HashSet;

/**
 * Immutable, thread-safe user overrides, keyed by stable action IDs.
 * No runtime state is serialized.
 *
 * @param bindings binding overrides keyed by their stable identifiers
 */
public record InputProfile(Map<String, List<BindingDefinition>> bindings) {
  /** Profile containing no user overrides. */
  public static final InputProfile EMPTY = new InputProfile(Map.of());
  private static final int VERSION = 1;
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Copies and validates profile overrides.
   *
   * @param bindings binding overrides keyed by their stable identifiers
   */
  public InputProfile {
    Map<String, List<BindingDefinition>> copy = new LinkedHashMap<>();
    bindings.forEach((id, definitions) -> {
      if (id.isBlank()) throw new IllegalArgumentException("Action ID must not be blank");
      var ids = new HashSet<String>();
      for (BindingDefinition definition : definitions) {
        if (!ids.add(definition.id())) throw new IllegalArgumentException("Duplicate alternative in action " + id + ": " + definition.id());
      }
      copy.put(id, List.copyOf(definitions));
    });
    bindings = Map.copyOf(copy);
  }

  /**
   * Reads an input profile from its JSON representation.
   *
   * @param json JSON profile text
   * @return parsed profile
   * @throws IOException if the text is malformed or uses an unsupported format
   */
  public static InputProfile fromJson(String json) throws IOException {
    JsonNode root = JSON.readTree(json);
    if (root == null || !root.isObject() || root.path("version").asInt() != VERSION) {
      throw new IOException("Unsupported input profile format/version");
    }
    JsonNode entries = root.path("bindings");
    if (!entries.isObject()) {
      throw new IOException("Input profile bindings must be an object");
    }
    Map<String, List<BindingDefinition>> overrides = new LinkedHashMap<>();
    for (var field : entries.properties()) {
      String id = field.getKey();
      if (!field.getValue().isArray()) throw new IOException("Input action alternatives must be an array: " + id);
      List<BindingDefinition> alternatives = new ArrayList<>();
      for (JsonNode node : field.getValue()) {
        JsonNode controls = node.path("controls");
        if (!controls.isArray()) throw new IOException("Input binding controls must be an array: " + id);
        var paths = new ArrayList<String>();
        for (JsonNode control : controls) {
          if (!control.isTextual()) throw new IOException("Input control must be a string: " + id);
          paths.add(control.textValue());
        }
        if (!node.path("id").isTextual()) throw new IOException("Input alternative needs an ID: " + id);
        for (String key : new String[]{"pressPoint", "releasePoint", "modifiers"}) {
          if (node.has(key) && !node.get(key).isNumber()) throw new IOException("Binding " + key + " must be numeric: " + id);
        }
        try {
          alternatives.add(new BindingDefinition(node.path("id").textValue(), paths,
              (float) node.path("pressPoint").asDouble(0.5),
              (float) node.path("releasePoint").asDouble(0.4), node.path("modifiers").asInt(InputModifiers.ANY)));
        } catch (IllegalArgumentException exception) {
          throw new IOException("Invalid input action " + id, exception);
        }
      }
      overrides.put(id, alternatives);
    }
    try {
      return new InputProfile(overrides);
    } catch (IllegalArgumentException exception) {
      throw new IOException("Invalid input profile", exception);
    }
  }

  /**
   * Loads an input profile from a file, returning {@link #EMPTY} when it does not exist.
   *
   * @param file profile file
   * @return loaded profile or {@link #EMPTY}
   * @throws IOException if the file cannot be read or parsed
   */
  public static InputProfile load(Path file) throws IOException {
    try {
      return fromJson(Files.readString(file));
    } catch (NoSuchFileException missing) {
      return EMPTY;
    }
  }

  /**
   * Serializes this profile as formatted JSON.
   *
   * @return JSON profile text
   * @throws IOException if serialization fails
   */
  public String toJson() throws IOException {
    ObjectNode root = JSON.createObjectNode();
    root.put("version", VERSION);
    ObjectNode entries = root.putObject("bindings");
    bindings.keySet().stream().sorted().forEach(id -> {
      var alternatives = entries.putArray(id);
      for (BindingDefinition binding : bindings.get(id)) {
        ObjectNode node = alternatives.addObject();
        node.put("id", binding.id());
        var controls = node.putArray("controls");
        binding.controls().forEach(controls::add);
        node.put("pressPoint", binding.pressPoint());
        node.put("releasePoint", binding.releasePoint());
        node.put("modifiers", binding.modifiers());
      }
    });
    return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
  }

  /**
   * Atomically saves this profile where the platform supports atomic moves.
   *
   * @param file destination profile file
   * @throws IOException if the directory cannot be created or the profile cannot be written
   */
  public void save(Path file) throws IOException {
    Path target = file.toAbsolutePath();
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
    try {
      Files.writeString(temporary, toJson());
      try {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
