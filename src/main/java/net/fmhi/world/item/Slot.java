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

/**
 * A single slot in an inventory, capable of holding one {@link ItemStack} at a time.
 *
 * <p>Slots support insertion (merging into the existing stack or replacing it)
 * and extraction (removing a portion of the held stack). The {@link #slotLimit()}
 * caps the number of items that can be inserted in a single operation.
 *
 * @see ItemStack
 * @see Container
 * @see SlotImpl
 */
public interface Slot {
  /**
   * Returns the item currently in this slot.
   *
   * @return the item stack; never {@code null} — use {@link ItemStack#isEmpty()} to check for emptiness
   */
  ItemStack get();

  /**
   * Sets the item in this slot.
   *
   * @param stack the item stack to store
   */
  void set(ItemStack stack);

  /**
   * Returns whether the given stack is allowed in this slot.
   *
   * @param stack the stack to validate
   * @return {@code true} if the stack is allowed; defaults to {@code true}
   */
  default boolean validate(ItemStack stack) {
    return true;
  }

  /**
   * Returns whether this slot is empty.
   *
   * @return {@code true} if the held stack is empty
   */
  default boolean isEmpty() {
    return get().isEmpty();
  }

  /**
   * Returns the maximum number of items this slot can accept in a single insertion.
   *
   * @return the slot limit
   */
  int slotLimit();

  /**
   * Removes up to {@code count} items from this slot and returns them as a new stack.
   *
   * @param count the number of items to remove
   * @return the removed items, or {@link ItemStack#EMPTY} if the slot is empty or count is non-positive
   */
  ItemStack remove(int count);

  /**
   * Inserts an item stack into this slot.
   *
   * <p>If the incoming stack matches the current item, counts are merged up to the
   * slot limit. Otherwise, the slot is replaced. Any remainder that could not fit
   * is returned.
   *
   * @param incoming the stack to insert
   * @return the portion that could not fit, or {@link ItemStack#EMPTY} if fully inserted
   */
  ItemStack insert(ItemStack incoming);
}
