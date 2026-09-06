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

package io.viki.momentum.audio;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A node in an audio volume hierarchy.
 *
 * <p>controls form a tree where each node's effective volume is the product
 * of its own local volume and the effective volumes of all its ancestors. This enables category-level volume control,
 * where adjusting a parent control affects all descendants.
 *
 * <p>This class is thread-safe.
 *
 * @see Clip
 */
public final class VolumeControl {
  private final String name;
  private final List<VolumeControl> children = new CopyOnWriteArrayList<>();
  private @Nullable VolumeControl parent;
  private volatile float volume = 1.0F;

  /**
   * Creates a root control with the given name.
   *
   * @param name human-readable label for this control
   */
  public VolumeControl(String name) {
    this.name = name;
    parent = null;
  }

  private VolumeControl(String name, VolumeControl parent) {
    this.name = name;
    this.parent = parent;
  }

  /**
   * Returns this control's name.
   *
   * @return the control name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the parent control.
   *
   * @return the parent control, or {@code null} if this is a root
   */
  public @Nullable VolumeControl parent() {
    return parent;
  }

  /**
   * Returns an unmodifiable view of this control's direct children.
   *
   * @return unmodifiable list of child controls
   */
  public List<VolumeControl> children() {
    return Collections.unmodifiableList(children);
  }

  /**
   * Creates a new child control and attaches it to this control.
   *
   * @param childName name for the new child
   * @return the newly created child control
   */
  public VolumeControl derive(String childName) {
    VolumeControl child = new VolumeControl(childName, this);
    children.add(child);
    return child;
  }

  /**
   * Detaches a direct child from this control.
   *
   * <p>Has no effect if the given control is not a direct child.
   *
   * @param child the child control to detach
   */
  public void remove(VolumeControl child) {
    if (children.remove(child)) {
      child.parent = null;
    }
  }

  /**
   * Returns this control's local volume, not accounting for ancestors.
   *
   * @return local volume, where {@code 0.0} is silent and {@code 1.0} is full
   */
  public float volume() {
    return volume;
  }

  /**
   * Sets this control's local volume.
   *
   * <p>Values below {@code 0.0} are clamped to {@code 0.0}.
   *
   * @param volume new local volume
   */
  public void setVolume(float volume) {
    this.volume = Math.max(0.0F, volume);
  }

  /**
   * Returns the effective volume as seen by clips played through this control.
   *
   * <p>Computed as the product of this control's local volume and the
   * effective volume of all ancestors.
   *
   * @return effective volume
   */
  public float effectiveVolume() {
    return parent == null ? volume : volume * parent.effectiveVolume();
  }

  @Override
  public String toString() {
    return String.format("VolumeControl[name='%s', volume=%.2f, effective=%.2f, children=%d]", name, volume,
        effectiveVolume(), children.size());
  }
}
