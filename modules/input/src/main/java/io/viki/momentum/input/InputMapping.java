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

package io.viki.momentum.input;

import io.viki.momentum.codec.Codec;

/**
 * A logical, decoupled input mapping.
 *
 * <p>It does not depend on whether it is used client-side or server-side, allowing input
 * consumers to keep the same logic across both environments.
 */
public interface InputMapping {
  /**
   * Creates a codec for a fixed ordered set of logical actions.
   *
   * @param actions actions included in the serialized schema, in stream order
   * @return codec for headless snapshots using the supplied action schema
   */
  static Codec<HeadlessInputMapping> codec(Action... actions) {
    return new InputMappingCodec(actions);
  }

  /**
   * Returns the current state for a logical action.
   *
   * @param action action to query
   * @param <T> expected input subtype
   * @return current input state for the action
   */
  <T extends Input> T of(Action action);

  /**
   * Captures independent states for the requested actions.
   *
   * @param actions actions to capture
   * @return headless snapshot containing the requested states
   * @throws IllegalArgumentException if a requested state cannot be copied
   */
  default HeadlessInputMapping capture(Action... actions) {
    HeadlessInputMapping result = new HeadlessInputMapping();
    for (Action action : actions) {
      result.set(action, HeadlessInputMapping.copy(of(action)));
    }
    return result;
  }
}
