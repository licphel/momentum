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

package net.fmhi.world.item;

import java.util.List;

/**
 * An inventory backed by an array of {@link SlotImpl}s.
 *
 * <p>Insertion tries to merge into existing stacks of the same type first, then
 * fills empty slots. Extraction pulls matching items from any allowed slot.
 * Both operations are filtered by a slot-index predicate.
 *
 * @param slots the slot array backing this container
 * @see SlotImpl
 * @see Container
 */
public record SlotArrayContainer(List<Slot> slots) implements Container {
  /**
   * Creates a container with the given number of empty slots.
   *
   * @param size the number of slots
   */
  public SlotArrayContainer(int size) {
    Slot[] slots = new Slot[size];
    for (int i = 0; i < size; i++) {
      slots[i] = new SlotImpl();
    }
    this(List.of(slots));
  }

  @Override
  public Slot slotAt(int slot) {
    assertInRange(slot);
    return slots.get(slot);
  }

  @Override
  public void set(int slot, ItemStack stack) {
    slotAt(slot).set(stack);
  }

  private void assertInRange(int slot) {
    if (slot < 0 || slot >= slots.size()) {
      throw new IndexOutOfBoundsException("SlotImpl " + slot + " is out of bounds");
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    SlotArrayContainer that = (SlotArrayContainer) obj;
    return slots.equals(that.slots);
  }

  @Override
  public int hashCode() {
    return slots.hashCode();
  }
}
