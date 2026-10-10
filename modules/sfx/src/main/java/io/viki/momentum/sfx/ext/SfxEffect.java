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

package io.viki.momentum.sfx.ext;

import io.viki.momentum.sfx.Clip;
import io.viki.momentum.sfx.Mixer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A backend-neutral audio effect that can be attached to a {@link Clip}.
 *
 * <p>The registry is intentionally small: the sfx module only provides the
 * lookup and lifecycle contract. A backend registers an effect when it can
 * implement it, while callers can safely request optional effects and receive
 * {@link #NONE} when the active backend does not provide one.
 *
 * <p>Effect registration and lookup are safe for concurrent use. Individual
 * effects must be attached and detached according to the owning clip's
 * backend-thread contract.
 */
public final class SfxEffect {
  /**
   * Native/backend binding returned by an effect definition.
   */
  @FunctionalInterface
  public interface Binding {
    /**
     * Applies the binding to an opened clip.
     *
     * @param clip opened clip receiving this binding
     */
    void attach(Clip clip);

    /**
     * Removes the binding from a clip before its backend resource is reused.
     *
     * @param clip clip losing this binding
     */
    default void detach(Clip clip) {
    }

    /**
     * Applies this binding to one mixer-owned auxiliary send.
     *
     * <p>Backends that do not expose mixer sends can keep the default no-op.
     *
     * @param mixer mixer owning the send
     * @param slot backend-specific auxiliary slot handle
     */
    default void attach(Mixer mixer, int slot) {
    }

    /**
     * Removes this binding from one mixer-owned auxiliary send.
     *
     * @param mixer mixer owning the send
     * @param slot backend-specific auxiliary slot handle
     */
    default void detach(Mixer mixer, int slot) {
    }
  }

  /** No-op effect used for unsupported names and clips without an effect. */
  public static final SfxEffect NONE = new SfxEffect("", new Object[0], null);

  private static final Map<String, Function<SfxEffect, Object[]>> EFFECTS = new ConcurrentHashMap<>();

  private final String name;
  private final Object[] arguments;
  private final Object[] bindings;
  private final SfxEffect[] children;

  private SfxEffect(String name, Object[] arguments,
                    @Nullable Function<SfxEffect, Object[]> definition) {
    this.name = name;
    this.arguments = arguments.clone();
    this.bindings = definition == null ? new Object[0] : definition.apply(this);
    this.children = new SfxEffect[0];
  }

  private SfxEffect(SfxEffect[] children) {
    this.name = "composite";
    this.arguments = new Object[0];
    this.bindings = new Object[0];
    this.children = children;
  }

  /**
   * Resolves an optional effect by its backend-independent name.
   *
   * @param name registered effect name
   * @param args effect-specific arguments
   * @return a new effect instance, or {@link #NONE} when unavailable
   */
  public static SfxEffect get(String name, Object... args) {
    Function<SfxEffect, Object[]> definition = EFFECTS.get(name);
    return definition == null ? NONE : new SfxEffect(name, args, definition);
  }

  /**
   * Resolves the built-in configurable filter effect.
   *
   * @param parameters filter parameters
   * @return resolved filter effect, or {@link #NONE} when unavailable
   */
  public static SfxEffect get(Filter parameters) {
    return get("filter", parameters);
  }

  /**
   * Resolves the built-in reverb effect.
   *
   * @param parameters reverb parameters
   * @return resolved reverb effect, or {@link #NONE} when unavailable
   */
  public static SfxEffect get(Reverb parameters) {
    return get("reverb", parameters);
  }

  /**
   * Resolves the built-in configurable echo effect.
   *
   * @param parameters echo parameters
   * @return resolved echo effect, or {@link #NONE} when unavailable
   */
  public static SfxEffect get(Echo parameters) {
    return get("echo", parameters);
  }

  /**
   * Registers or replaces an effect implementation.
   *
   * <p>Registration is normally performed by an audio backend during startup.
   * The name is only used at the extension boundary; clips retain the resolved
   * object and do not perform name lookups while playing.
   *
   * @param name effect name
   * @param definition creates backend bindings for each new effect instance
   */
  public static void register(String name, Function<SfxEffect, Object[]> definition) {
    EFFECTS.put(name, definition);
  }

  /**
   * Combines independently implemented effects into one clip attachment.
   * Unsupported or empty effects are ignored, so callers can compose optional
   * backend features without branching on backend capabilities.
   *
   * @param effects effects to apply in declaration order
   * @return one effect representing all supplied effects, or {@link #NONE}
   */
  public static SfxEffect combine(SfxEffect... effects) {
    return new SfxEffect(effects);
  }

  /**
   * Returns the registered effect name.
   *
   * @return registered effect name
   */
  public String name() {
    return name;
  }

  /**
   * Returns a copy of the arguments supplied to {@link #get(String, Object...)}.
   *
   * @return effect arguments
   */
  public Object[] arguments() {
    return arguments.clone();
  }

  /**
   * Returns one optional effect argument.
   *
   * @param index zero-based argument index
   * @param <T> expected argument type
   * @return argument value, or {@code null} when the index is outside the argument list
   */
  @SuppressWarnings("unchecked")
  public <T> @Nullable T argument(int index) {
    return index >= 0 && index < arguments.length ? (T) arguments[index] : null;
  }

  /**
   * Returns an effect argument or a fallback when it was not supplied.
   *
   * @param index zero-based argument index
   * @param fallback value returned when the argument is unavailable
   * @param <T> expected argument type
   * @return argument value, or {@code fallback} when the argument is unavailable
   */
  @SuppressWarnings("unchecked")
  public <T> T argument(int index, T fallback) {
    Object argument = argument(index);
    return argument == null ? fallback : (T) argument;
  }

  /**
   * Applies this effect to a clip on the clip's backend thread.
   *
   * <p>The default implementation applies the bindings produced by the
   * registered definition. A subclass can override this for a backend that
   * does not use the binding protocol.
   *
   * @param clip clip receiving this effect
   */
  public void attach(Clip clip) {
    for (SfxEffect child : children) {
      child.attach(clip);
    }
    for (Object binding : bindings) {
      if (binding instanceof Binding value) {
        value.attach(clip);
      }
    }
  }

  /**
   * Removes this effect from a clip on the clip's backend thread.
   *
   * @param clip clip losing this effect
   */
  public void detach(Clip clip) {
    for (Object binding : bindings) {
      if (binding instanceof Binding value) {
        value.detach(clip);
      }
    }
    for (int i = children.length - 1; i >= 0; i--) {
      children[i].detach(clip);
    }
  }

  /**
   * Applies this effect to a mixer-owned auxiliary send.
   *
   * @param mixer mixer receiving the effect
   * @param slot backend-specific auxiliary slot handle
   */
  public void attach(Mixer mixer, int slot) {
    for (SfxEffect child : children) {
      child.attach(mixer, slot);
    }
    for (Object binding : bindings) {
      if (binding instanceof Binding value) {
        value.attach(mixer, slot);
      }
    }
  }

  /**
   * Removes this effect from a mixer-owned auxiliary send.
   *
   * @param mixer mixer losing the effect
   * @param slot backend-specific auxiliary slot handle
   */
  public void detach(Mixer mixer, int slot) {
    for (Object binding : bindings) {
      if (binding instanceof Binding value) {
        value.detach(mixer, slot);
      }
    }
    for (int i = children.length - 1; i >= 0; i--) {
      children[i].detach(mixer, slot);
    }
  }
}
