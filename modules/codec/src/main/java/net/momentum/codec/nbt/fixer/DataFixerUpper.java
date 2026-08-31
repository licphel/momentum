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

package net.momentum.codec.nbt.fixer;

import net.momentum.codec.nbt.NBT;

import java.util.*;

/**
 * Default {@link DataFixer} implementation — a stripped-down DataFixerUpper.
 *
 * <p>Fixes are registered per {@link TypeReference} for the version range they
 * cover; each start version may carry at most one fix. Walkers run before the
 * fix chain on every update, in registration order.
 *
 * <p>Instances are immutable and thread-safe once built.
 */
public final class DataFixerUpper implements DataFixer {
  private final Map<TypeReference, Map<Integer, DataFix>> fixes;
  private final List<NBTWalker> walkers;

  private DataFixerUpper(Builder builder) {
    Map<TypeReference, Map<Integer, DataFix>> copy = new HashMap<>();
    builder.fixes.forEach((type, byVersion) -> copy.put(type, Map.copyOf(byVersion)));
    this.fixes = Collections.unmodifiableMap(copy);
    this.walkers = List.copyOf(builder.walkers);
  }

  /**
   * Creates a fixer builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public NBT update(TypeReference type, NBT input, int version, int newVersion) {
    NBT result = input;
    for (NBTWalker walker : walkers) {
      result = walker.walk(type, result, this, version);
    }

    Map<Integer, DataFix> byVersion = fixes.get(type);
    if (byVersion != null) {
      // Chain the fixes whose start version falls in [version, newVersion),
      // ascending — versions without a fix are skipped.
      for (int from : byVersion.keySet().stream()
          .filter(start -> start >= version && start < newVersion)
          .sorted()
          .toList()) {
        result = byVersion.get(from).fix(result);
      }
    }
    return result;
  }

  /**
   * Builds a {@link DataFixerUpper}. Not thread-safe.
   */
  public static final class Builder {
    private final Map<TypeReference, Map<Integer, DataFix>> fixes = new HashMap<>();
    private final List<NBTWalker> walkers = new ArrayList<>();

    private Builder() {
    }

    /**
     * Registers a fix that upgrades data of the given type from one version to
     * a later one. The start version is the fix's position in the upgrade
     * chain: {@link #update} applies registered fixes in ascending start-version
     * order.
     *
     * @param type        the type the fix applies to
     * @param fromVersion the version the fix upgrades from
     * @param toVersion   the version the fix upgrades to, must be greater than {@code fromVersion}
     * @param fix         the migration
     * @return this builder
     * @throws IllegalArgumentException if the range is not increasing, or a fix
     *                                  for the same type and start version is already registered
     */
    public Builder register(TypeReference type, int fromVersion, int toVersion, DataFix fix) {
      if (toVersion <= fromVersion) {
        throw new IllegalArgumentException("Fix version range must be increasing: " + fromVersion + " -> " + toVersion);
      }
      Map<Integer, DataFix> byVersion = fixes.computeIfAbsent(type, t -> new HashMap<>());
      if (byVersion.putIfAbsent(fromVersion, fix) != null) {
        throw new IllegalArgumentException("A fix for " + type + " at version " + fromVersion + " is already registered");
      }
      return this;
    }

    /**
     * Registers a walker that runs on every update, in registration order.
     *
     * @param walker the walker to add
     * @return this builder
     */
    public Builder registerWalker(NBTWalker walker) {
      walkers.add(walker);
      return this;
    }

    /**
     * Builds the fixer.
     *
     * @return the immutable fixer
     */
    public DataFixerUpper build() {
      return new DataFixerUpper(this);
    }
  }
}
