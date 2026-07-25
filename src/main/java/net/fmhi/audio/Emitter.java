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

package net.fmhi.audio;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A node in an audio volume hierarchy.
 *
 * <p>Emitters form a tree where each node's effective volume is the product
 * of its own local volume and the effective volumes of all its ancestors. This enables category-level volume control,
 * where adjusting a parent emitter affects all descendants.
 *
 * <p>This class is thread-safe.
 *
 * @see Clip
 */
public final class Emitter {
  private final String name;
  private final List<Emitter> children = new CopyOnWriteArrayList<>();
  private @Nullable Emitter parent;
  private volatile float volume = 1.0F;

  /**
   * Creates a root emitter with the given name.
   *
   * @param name human-readable label for this emitter
   */
  public Emitter(String name) {
    this.name = name;
    parent = null;
  }

  private Emitter(String name, Emitter parent) {
    this.name = name;
    this.parent = parent;
  }

  /**
   * Returns this emitter's name.
   *
   * @return the emitter name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the parent emitter.
   *
   * @return the parent emitter, or {@code null} if this is a root
   */
  public @Nullable Emitter parent() {
    return parent;
  }

  /**
   * Returns an unmodifiable view of this emitter's direct children.
   *
   * @return unmodifiable list of child emitters
   */
  public List<Emitter> children() {
    return Collections.unmodifiableList(children);
  }

  /**
   * Creates a new child emitter and attaches it to this emitter.
   *
   * @param childName name for the new child
   * @return the newly created child emitter
   */
  public Emitter derive(String childName) {
    Emitter child = new Emitter(childName, this);
    children.add(child);
    return child;
  }

  /**
   * Detaches a direct child from this emitter.
   *
   * <p>Has no effect if the given emitter is not a direct child.
   *
   * @param child the child emitter to detach
   */
  public void remove(Emitter child) {
    if (children.remove(child)) {
      child.parent = null;
    }
  }

  /**
   * Returns this emitter's local volume, not accounting for ancestors.
   *
   * @return local volume, where {@code 0.0} is silent and {@code 1.0} is full
   */
  public float volume() {
    return volume;
  }

  /**
   * Sets this emitter's local volume.
   *
   * <p>Values below {@code 0.0} are clamped to {@code 0.0}.
   *
   * @param volume new local volume
   */
  public void setVolume(float volume) {
    this.volume = Math.max(0.0F, volume);
  }

  /**
   * Returns the effective volume as seen by clips played through this emitter.
   *
   * <p>Computed as the product of this emitter's local volume and the
   * effective volume of all ancestors.
   *
   * @return effective volume
   */
  public float effectiveVolume() {
    return parent == null ? volume : volume * parent.effectiveVolume();
  }

  @Override
  public String toString() {
    return String.format("Emitter[name='%s', volume=%.2f, effective=%.2f, children=%d]", name, volume,
        effectiveVolume(), children.size());
  }
}
