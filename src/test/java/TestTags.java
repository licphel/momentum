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

import net.fmhi.codec.tag.CompoundTag;
import net.fmhi.codec.tag.JsonUtil;
import net.fmhi.fml.Identifier;
import net.fmhi.fml.config.Config;
import net.fmhi.fml.config.ConfigSpec;
import net.fmhi.fml.config.Validator;
import net.fmhi.fml.registry.Registry;
import net.fmhi.fml.tag.Tag;
import net.fmhi.fml.tag.TagManager;
import net.fmhi.fml.tag.TagMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manual tests for the tag system: plain tags, tag references, tag maps,
 * and resolution.
 */
@NullMarked
public class TestTags {

  // ── Simple in-memory registry for testing ──────────

  private static Registry<TestEntry> makeRegistry() {
    return new Registry<>() {
      private final Map<Identifier, TestEntry> byId = new LinkedHashMap<>();
      private final Map<TestEntry, Identifier> byValue = new IdentityHashMap<>();

      {
        register(Identifier.of("test:stone"), new TestEntry("stone"));
        register(Identifier.of("test:dirt"), new TestEntry("dirt"));
        register(Identifier.of("test:grass"), new TestEntry("grass"));
        register(Identifier.of("test:sand"), new TestEntry("sand"));
        register(Identifier.of("test:gravel"), new TestEntry("gravel"));
        register(Identifier.of("test:oak_log"), new TestEntry("oak_log"));
        register(Identifier.of("test:birch_log"), new TestEntry("birch_log"));
        register(Identifier.of("test:spruce_log"), new TestEntry("spruce_log"));
        register(Identifier.of("test:diamond"), new TestEntry("diamond"));
        register(Identifier.of("test:iron"), new TestEntry("iron"));
        register(Identifier.of("test:gold"), new TestEntry("gold"));
        register(Identifier.of("test:coal"), new TestEntry("coal"));
        freeze();
      }

      @Override
      public Identifier key() { return Identifier.of("test:entries"); }

      @Override
      public TestEntry register(Identifier id, TestEntry value) {
        if (isFrozen()) throw new IllegalStateException("frozen");
        byId.put(id, value);
        byValue.put(value, id);
        return value;
      }

      @Override
      public @Nullable TestEntry get(Identifier id) { return byId.get(id); }

      @Override
      public @Nullable Identifier getId(TestEntry value) { return byValue.get(value); }

      @Override
      public boolean contains(Identifier id) { return byId.containsKey(id); }

      @Override
      public Set<Identifier> keys() { return Collections.unmodifiableSet(byId.keySet()); }

      @Override
      public Stream<TestEntry> stream() { return byId.values().stream(); }

      @Override
      public int size() { return byId.size(); }

      private boolean frozen;

      @Override
      public void freeze() { frozen = true; }

      @Override
      public boolean isFrozen() { return frozen; }

      @Override
      public Iterator<TestEntry> iterator() { return byId.values().iterator(); }
    };
  }

  // ── Entry point ────────────────────────────────────

  static void main(String[] args) {
    testPlainTag();
    testTagReference();
    testTransitiveTagReference();
    testTagMap();
    testTagMapWithReferences();
    testMissingEntries();
    testConfigRoundtrip();
    testConfigValidation();
    testConfigFile();

    System.out.println("\n=== All tests passed ===");
  }

  // ── Test cases ─────────────────────────────────────

  /** Simplest tag: a flat list of values. */
  private static void testPlainTag() {
    System.out.println("--- testPlainTag ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    String json = """
        {"values": ["test:stone", "test:dirt", "test:grass"]}""";
    CompoundTag tag = JsonUtil.parse(json);
    mgr.load(Identifier.of("test:ground"), tag);
    mgr.resolve();

    Tag<TestEntry> ground = mgr.get(Identifier.of("test:ground"));
    assert ground != null : "tag should not be null";
    assert ground.contains(reg.get(Identifier.of("test:stone")));
    assert ground.contains(reg.get(Identifier.of("test:dirt")));
    assert ground.contains(reg.get(Identifier.of("test:grass")));
    assert !ground.contains(reg.get(Identifier.of("test:sand")));
    assert ground.values().size() == 3;

    System.out.println("  ground tag: " + ground);
    System.out.println("  values: " + ground.values());
  }

  /** Tag that references another tag via #. */
  private static void testTagReference() {
    System.out.println("--- testTagReference ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    mgr.load(Identifier.of("test:ground"), JsonUtil.parse("""
        {"values": ["test:stone", "test:dirt"]}"""));
    mgr.load(Identifier.of("test:all_blocks"), JsonUtil.parse("""
        {"values": ["test:diamond", "#test:ground"]}"""));
    mgr.resolve();

    Tag<TestEntry> allBlocks = mgr.get(Identifier.of("test:all_blocks"));
    assert allBlocks != null;
    // diamond (direct) + stone, dirt (via #test:ground)
    assert allBlocks.contains(reg.get(Identifier.of("test:diamond")));
    assert allBlocks.contains(reg.get(Identifier.of("test:stone")));
    assert allBlocks.contains(reg.get(Identifier.of("test:dirt")));
    assert !allBlocks.contains(reg.get(Identifier.of("test:grass")));
    assert allBlocks.values().size() == 3;

    System.out.println("  all_blocks: " + allBlocks.values());
  }

  /** Transitive tag reference: A → B → C. */
  private static void testTransitiveTagReference() {
    System.out.println("--- testTransitiveTagReference ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    mgr.load(Identifier.of("test:logs"), JsonUtil.parse("""
        {"values": ["test:oak_log", "test:birch_log", "test:spruce_log"]}"""));
    mgr.load(Identifier.of("test:natural"), JsonUtil.parse("""
        {"values": ["test:grass", "test:sand", "#test:logs"]}"""));
    mgr.load(Identifier.of("test:everything"), JsonUtil.parse("""
        {"values": ["test:diamond", "#test:natural"]}"""));
    mgr.resolve();

    Tag<TestEntry> everything = mgr.get(Identifier.of("test:everything"));
    assert everything != null;
    // diamond + grass + sand + oak_log + birch_log + spruce_log
    assert everything.contains(reg.get(Identifier.of("test:diamond")));
    assert everything.contains(reg.get(Identifier.of("test:grass")));
    assert everything.contains(reg.get(Identifier.of("test:sand")));
    assert everything.contains(reg.get(Identifier.of("test:oak_log")));
    assert everything.contains(reg.get(Identifier.of("test:birch_log")));
    assert everything.contains(reg.get(Identifier.of("test:spruce_log")));
    assert everything.values().size() == 6;

    System.out.println("  everything: " + everything.values());
  }

  /** TagMap: maps each entry to an integer weight. */
  @SuppressWarnings("unchecked")
  private static void testTagMap() {
    System.out.println("--- testTagMap ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    String json = """
        {"mappings": {"test:stone": 5, "test:dirt": 3, "test:grass": 1}}""";
    CompoundTag tag = JsonUtil.parse(json);
    mgr.loadMap(Identifier.of("test:weights"), tag,
        v -> ((Number) v).intValue());
    mgr.resolve();

    TagMap<TestEntry, Integer> weights = mgr.getMap(Identifier.of("test:weights"));
    assert weights != null : "tag map should not be null";

    TestEntry stone = reg.get(Identifier.of("test:stone"));
    TestEntry dirt = reg.get(Identifier.of("test:dirt"));
    TestEntry grass = reg.get(Identifier.of("test:grass"));
    TestEntry sand = reg.get(Identifier.of("test:sand"));

    assert weights.map(stone) == 5;
    assert weights.map(dirt) == 3;
    assert weights.map(grass) == 1;
    assert weights.map(sand) == null; // not mapped

    assert weights.mappings().size() == 3;
    assert weights.contains(stone); // still a Tag — contains works

    System.out.println("  weights: " + weights.mappings());
  }

  /** TagMap with # references: inherited values. */
  @SuppressWarnings("unchecked")
  private static void testTagMapWithReferences() {
    System.out.println("--- testTagMapWithReferences ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    // First load a regular tag that the tag map will reference
    mgr.load(Identifier.of("test:ores"), JsonUtil.parse("""
        {"values": ["test:diamond", "test:iron", "test:gold", "test:coal"]}"""));

    // Load a tag map that references the ores tag
    String json = """
        {"mappings": {"test:stone": 1, "#test:ores": 100}}""";
    CompoundTag tag = JsonUtil.parse(json);
    mgr.loadMap(Identifier.of("test:ore_weights"), tag,
        v -> ((Number) v).intValue());
    mgr.resolve();

    TagMap<TestEntry, Integer> oreWeights = mgr.getMap(Identifier.of("test:ore_weights"));
    assert oreWeights != null;

    // Direct mapping
    assert oreWeights.map(reg.get(Identifier.of("test:stone"))) == 1;
    // Via #test:ores
    assert oreWeights.map(reg.get(Identifier.of("test:diamond"))) == 100;
    assert oreWeights.map(reg.get(Identifier.of("test:iron"))) == 100;
    assert oreWeights.map(reg.get(Identifier.of("test:gold"))) == 100;
    assert oreWeights.map(reg.get(Identifier.of("test:coal"))) == 100;
    // Not mapped
    assert oreWeights.map(reg.get(Identifier.of("test:dirt"))) == null;

    assert oreWeights.mappings().size() == 5; // 1 direct + 4 via ref
    assert oreWeights.contains(reg.get(Identifier.of("test:diamond")));

    System.out.println("  ore_weights: " + oreWeights.mappings());
  }

  /** Missing entries in JSON are silently ignored. */
  private static void testMissingEntries() {
    System.out.println("--- testMissingEntries ---");
    Registry<TestEntry> reg = makeRegistry();
    TagManager<TestEntry> mgr = new TagManager<>(reg);

    // Entries that don't exist in the registry
    mgr.load(Identifier.of("test:with_missing"), JsonUtil.parse("""
        {"values": ["test:stone", "test:nonexistent", "test:dirt"]}"""));
    mgr.resolve();

    Tag<TestEntry> tag = mgr.get(Identifier.of("test:with_missing"));
    assert tag != null;
    assert tag.contains(reg.get(Identifier.of("test:stone")));
    assert tag.contains(reg.get(Identifier.of("test:dirt")));
    assert !tag.contains(reg.get(Identifier.of("test:nonexistent")));
    assert tag.values().size() == 2; // nonexistent excluded

    System.out.println("  with_missing (2 valid): " + tag.values());
  }

  // ── Config tests ────────────────────────────────────

  /** Config roundtrip: define via ConfigSpec, set via Config, dump JSON, parse back. */
  private static void testConfigRoundtrip() {
    System.out.println("--- testConfigRoundtrip ---");
    ConfigSpec spec = ConfigSpec.load();
    Config<Integer> width = spec.define("$graphics.width", 1920,
        Validator.rangedInt(800, 7680), "Display width in pixels");
    Config<Integer> height = spec.define("$graphics.height", 1080,
        Validator.rangedInt(600, 4320), "Display height in pixels");
    Config<Boolean> fullscreen = spec.define("$graphics.fullscreen", false, null,
        "Whether to use fullscreen mode");
    Config<String> title = spec.define("$window.title", "My Game",
        Validator.nonBlank(), "Window title");
    Config<Double> volume = spec.define("$audio.masterVolume", 1.0,
        Validator.rangedDouble(0, 1), "Master volume level");

    // Read defaults through Config
    assert width.get() == 1920;
    assert height.get() == 1080;
    assert !fullscreen.get();
    assert title.get().equals("My Game");
    assert volume.get() == 1.0;

    // Modify through Config — live update
    width.set(2560);
    title.set("Test Window");
    fullscreen.set(true);
    volume.set(0.5);

    // Dump to JSON
    String json = spec.dump();
    System.out.println("  JSON output:\n" + json.indent(4));
    assert json.contains("2560");
    assert json.contains("Test Window");
    assert json.contains("0.5");

    // Reload — same Config handles now see new values
    spec.reload(json);
    assert width.get() == 2560;
    assert title.get().equals("Test Window");
    assert fullscreen.get();
    assert volume.get() == 0.5;
  }

  /** Config validation: out-of-range values are silently ignored. */
  private static void testConfigValidation() {
    System.out.println("--- testConfigValidation ---");
    ConfigSpec spec = ConfigSpec.load();
    Config<Integer> aB = spec.define("$a.b", 0,
        Validator.rangedInt(0, 100), null);
    Config<String> name = spec.define("$name", "default",
        Validator.nonBlank(), null);

    // Valid
    aB.set(50);
    assert aB.get() == 50;

    // Out of range — silently keeps previous value
    aB.set(200);
    assert aB.get() == 50; // unchanged

    // Blank string — silently keeps previous value
    name.set("");
    assert name.get().equals("default"); // unchanged

    // OneOf validator
    ConfigSpec spec2 = ConfigSpec.load();
    Config<String> mode = spec2.define("$mode", "windowed",
        Validator.oneOf("windowed", "fullscreen", "borderless"), null);
    mode.set("fullscreen"); // OK
    assert mode.get().equals("fullscreen");
    mode.set("maximized"); // not allowed — silently ignored
    assert mode.get().equals("fullscreen"); // unchanged

    System.out.println("  caught expected: silent ignore on invalid values");
  }

  /** Config from/to file. */
  private static void testConfigFile() {
    System.out.println("--- testConfigFile ---");
    ConfigSpec spec = ConfigSpec.load();
    Config<Integer> answer = spec.define("$answer", 42, null, null);
    Config<String> greeting = spec.define("$greeting", "hello", null, null);

    try {
      Path tmp = Files.createTempFile("fmhi-test-config", ".json");
      tmp.toFile().deleteOnExit();

      answer.set(99);
      spec.save(tmp);

      System.out.println("  saved to: " + tmp);
      System.out.println("  content:\n" + Files.readString(tmp).indent(4));

      ConfigSpec loaded = ConfigSpec.load(tmp);
      Config<Integer> answer2 = loaded.define("$answer", 42, null, null);
      Config<String> greeting2 = loaded.define("$greeting", "hello", null, null);
      assert answer2.get() == 99; // from file
      assert greeting2.get().equals("hello"); // from file

      // save() without explicit path (has filePath from load)
      answer2.set(123);
      loaded.save();

      ConfigSpec reloaded = ConfigSpec.load(tmp);
      Config<Integer> answer3 = reloaded.define("$answer", 42, null, null);
      assert answer3.get() == 123;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // ── Dummy entry type ───────────────────────────────

  private record TestEntry(String name) {
    @Override
    public String toString() {
      return name;
    }
  }
}
