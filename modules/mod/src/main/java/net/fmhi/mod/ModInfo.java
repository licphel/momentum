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

package net.fmhi.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fmhi.util.SemanticVersion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed metadata from a {@code mod.json} file.
 *
 * <p>The mod.json is packaged at the root of the mod JAR:
 *
 * <pre>{@code
 * {
 *   "info": {
 *     "id": "mymod",
 *     "version": "1.0.0",
 *     "authors": "Author Name",
 *     "displayName": "My Mod",
 *     "description": "A sample mod"
 *   },
 *   "program": {
 *     "isCoreMod": false,
 *     "entrypoint": "com.example.MyMod"
 *   },
 *   "dependencies": [
 *     { "id": "othermod", "minVersion": "1.0.0", "maxVersion": "2.0.0" }
 *   ]
 * }
 * }</pre>
 *
 * @param modId        the unique mod identifier
 * @param version      the mod version
 * @param authors      the author string (comma-separated)
 * @param displayName  the human-readable mod name
 * @param description  a short description of the mod
 * @param dependencies the mod's dependencies
 * @param isCoreMod    whether this is a core/system mod
 * @param entrypoint   the fully-qualified class name of the mod entrypoint, or {@code null}
 * @see Mod
 */
public record ModInfo(String modId, SemanticVersion version, String authors, String displayName, String description,
                      Dependency[] dependencies, boolean isCoreMod, @Nullable String entrypoint) {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Parses a mod.json string into a {@code ModInfo}.
   *
   * @param json the raw JSON content of a mod.json file
   * @return the parsed metadata
   * @throws ModException if the JSON is malformed or required fields are missing
   */
  public static ModInfo fromJson(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);

      JsonNode info = root.get("info");
      if (info == null || !info.isObject()) {
        throw new ModException("mod.json must have an 'info' object");
      }

      String id = requiredString(info, "id", "info.id");
      String versionStr = requiredString(info, "version", "info.version");
      SemanticVersion version = SemanticVersion.parse(versionStr);

      String authors = info.has("authors") ? info.get("authors").asText() : "";
      String displayName = info.has("displayName") ? info.get("displayName").asText() : id;
      String description = info.has("description") ? info.get("description").asText() : "";

      boolean isCoreMod = false;
      String entrypoint = null;

      JsonNode program = root.get("program");
      if (program != null && program.isObject()) {
        isCoreMod = program.has("isCoreMod") && program.get("isCoreMod").asBoolean();
        entrypoint = program.has("entrypoint") ? program.get("entrypoint").asText() : null;
      }

      List<Dependency> deps = new ArrayList<>();
      JsonNode depsNode = root.get("dependencies");
      if (depsNode != null && depsNode.isArray()) {
        for (JsonNode depNode : depsNode) {
          String depId = requiredString(depNode, "id", "dependency.id");
          SemanticVersion minVer = depNode.has("minVersion") ?
              SemanticVersion.parse(depNode.get("minVersion").asText()) : null;
          SemanticVersion maxVer = depNode.has("maxVersion") ?
              SemanticVersion.parse(depNode.get("maxVersion").asText()) : null;
          deps.add(new Dependency(depId, minVer, maxVer));
        }
      }

      return new ModInfo(id, version, authors, displayName, description, deps.toArray(Dependency[]::new),
          isCoreMod, entrypoint);
    } catch (ModException e) {
      throw e;
    } catch (Exception e) {
      throw new ModException("Failed to parse mod.json: " + e.getMessage(), e);
    }
  }

  private static String requiredString(JsonNode parent, String field, String fullPath) {
    JsonNode node = parent.get(field);
    if (node == null || node.isNull() || node.asText().isBlank()) {
      throw new ModException("mod.json missing required field: " + fullPath);
    }
    return node.asText();
  }

  /**
   * Returns whether this mod declares an executable entrypoint.
   *
   * @return {@code true} if {@link #entrypoint()} is non-null
   */
  public boolean hasProgram() {
    return entrypoint != null;
  }
}
