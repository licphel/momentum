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

package io.viki.momentum.mod;

import io.viki.momentum.util.SemanticVersion;

import java.util.function.Predicate;

/**
 * A mod dependency evaluated against a version condition.
 *
 * <p>The dependency is satisfied when its condition accepts the dependency's
 * resolved version.
 *
 * @param modId         the required mod identifier
 * @param condition     the predicate used to determine whether a version is accepted
 * @param conditionDesc description of the predicate
 */
public record Dependency(String modId, Predicate<SemanticVersion> condition, String conditionDesc) {
  /**
   * Creates a dependency with no version constraints.
   *
   * @param modId the required mod identifier
   */
  public Dependency(String modId) {
    this(modId, _ -> true, "<any>");
  }

  /**
   * Returns whether the given version satisfies this dependency.
   *
   * @param version the version to check
   * @return {@code true} if the version is within bounds
   */
  public boolean isSatisfiedBy(SemanticVersion version) {
    return condition.test(version);
  }

  /**
   * Creates a "Not satisfied" exception with detailed info.
   *
   * @param selfModId the mod that depends on this dependency
   * @param version   the target mod version
   * @return an exception object to be thrown
   */
  public ModException createNotSatisfiedException(String selfModId, SemanticVersion version) {
    return new ModException(selfModId + " requires " + modId + " " + conditionDesc + ", but got " + version);
  }
}
