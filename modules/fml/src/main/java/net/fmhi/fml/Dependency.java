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

package net.fmhi.fml;

import net.fmhi.util.SemanticVersion;
import org.jspecify.annotations.Nullable;

/**
 * A mod dependency with an optional version range.
 *
 * <p>Both {@code minVersion} and {@code maxVersion} are inclusive bounds.
 * If both are {@code null}, any version of the dependency is accepted.
 *
 * @param modId      the required mod identifier
 * @param minVersion the minimum acceptable version, or {@code null} for no lower bound
 * @param maxVersion the maximum acceptable version, or {@code null} for no upper bound
 */
public record Dependency(String modId,
                         @Nullable SemanticVersion minVersion,
                         @Nullable SemanticVersion maxVersion) {
  /**
   * Creates a dependency with no version constraints.
   *
   * @param modId the required mod identifier
   */
  public Dependency(String modId) {
    this(modId, null, null);
  }

  /**
   * Returns whether the given version satisfies this dependency.
   *
   * @param version the version to check
   * @return {@code true} if the version is within bounds
   */
  public boolean isSatisfiedBy(SemanticVersion version) {
    if (minVersion != null && version.compareTo(minVersion) < 0) {
      return false;
    }
    return maxVersion == null || version.compareTo(maxVersion) <= 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("'").append(modId).append("'");
    if (minVersion != null) {
      sb.append(" >= ").append(minVersion);
    }
    if (maxVersion != null) {
      sb.append(" <= ").append(maxVersion);
    }
    return sb.toString();
  }
}
