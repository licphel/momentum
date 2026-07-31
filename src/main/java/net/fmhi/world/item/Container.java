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
import java.util.function.Predicate;

/**
 * An inventory made up of multiple {@link Slot} slots.
 *
 * <p>Provides bulk {@link #insert(ItemStack, Predicate)} and
 * {@link #extract(ItemStack, Predicate)} operations that iterate over
 * slots matching a predicate. Insertion merges into existing stacks first,
 * then fills empty slots. Extraction pulls matching items from any
 * matching slot.
 *
 * <p>Convenience overloads {@link #insert(ItemStack)} and
 * {@link #extract(ItemStack)} operate on all slots.
 *
 * @see Slot
 * @see SlotArrayContainer
 */
public interface Container {
  /**
   * Returns the underlying slot list (possibly unmodifiable).
   *
   * @return the slots
   */
  List<Slot> slots();

  /**
   * Returns the number of slots in this handler.
   *
   * @return the slot count
   */
  default int size() {
    return slots().size();
  }

  /**
   * Inserts a stack into slots matching the given predicate.
   *
   * <p>First tries to merge into existing stacks of the same type, then fills
   * empty slots. Returns any remainder that could not fit.
   *
   * @param stack       the stack to insert
   * @param slotMatcher a predicate that returns {@code true} for allowed slot indices
   * @return the portion that could not fit, or {@link ItemStack#EMPTY} if fully inserted
   */
  default ItemStack insert(ItemStack stack, Predicate<Integer> slotMatcher) {
    if (stack.isEmpty()) {
      return ItemStack.EMPTY;
    }

    ItemStack remainder = stack.copy();

    for (int i = 0; i < size() && !remainder.isEmpty(); i++) {
      if (!slotMatcher.test(i)) {
        continue;
      }
      Slot slot = slotAt(i);
      if (slot.get().isEmpty()) {
        continue;
      }
      if (!ItemStack.isMergeable(slot.get(), remainder)) {
        continue;
      }

      int room = slot.slotLimit() - slot.get().count();
      if (room <= 0) {
        continue;
      }
      int add = Math.min(remainder.count(), room);
      slot.get().grow(add);
      remainder.shrink(add);
    }

    for (int i = 0; i < size() && !remainder.isEmpty(); i++) {
      if (!slotMatcher.test(i)) {
        continue;
      }
      Slot slot = slotAt(i);
      if (!slot.get().isEmpty()) {
        continue;
      }
      if (!slot.validate(remainder)) {
        continue;
      }

      int fit = Math.min(remainder.count(), slot.slotLimit());
      slot.set(remainder.copyWithCount(fit));
      remainder.shrink(fit);
    }

    return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
  }

  /**
   * Extracts items matching the given template from slots that match the predicate.
   *
   * @param template    a stack describing the item type and data to match
   * @param slotMatcher a predicate that returns {@code true} for allowed slot indices
   * @return the extracted items, or {@link ItemStack#EMPTY} if nothing matched
   */
  default ItemStack extract(ItemStack template,
                            Predicate<Integer> slotMatcher) {
    if (template.isEmpty()) {
      return ItemStack.EMPTY;
    }

    int remaining = template.count();
    ItemStack collected = ItemStack.EMPTY;

    for (int i = 0; i < size() && remaining > 0; i++) {
      if (!slotMatcher.test(i)) {
        continue;
      }
      Slot slot = slotAt(i);
      if (slot.get().isEmpty()) {
        continue;
      }
      if (!ItemStack.isSameItemSameData(template, slot.get())) {
        continue;
      }

      int take = Math.min(remaining, slot.get().count());
      ItemStack taken = slot.remove(take);
      remaining -= taken.count();
      if (collected.isEmpty()) {
        collected = taken;
      } else {
        collected.grow(taken.count());
      }
    }

    return collected;
  }

  /**
   * Inserts a stack into all slots.
   *
   * @param stack the stack to insert
   * @return the portion that could not fit, or {@link ItemStack#EMPTY} if fully inserted
   */
  default ItemStack insert(ItemStack stack) {
    return insert(stack, i -> true);
  }

  /**
   * Extracts items matching the given template from all slots.
   *
   * @param template a stack describing the item type and data to match
   * @return the extracted items, or {@link ItemStack#EMPTY} if nothing matched
   */
  default ItemStack extract(ItemStack template) {
    return extract(template, i -> true);
  }

  /**
   * Returns the stack in the given slot.
   *
   * @param slot the slot index
   * @return the stack at that slot
   */
  default ItemStack get(int slot) {
    return slotAt(slot).get();
  }

  /**
   * Returns the slot handler at the given index.
   *
   * @param slot the slot index
   * @return the slot handler
   */
  Slot slotAt(int slot);

  /**
   * Sets the stack in the given slot.
   *
   * @param slot  the slot index
   * @param stack the stack to store
   */
  void set(int slot, ItemStack stack);

  /**
   * Clears every slot in this handler.
   */
  default void clear() {
    for (Slot slot : slots()) {
      slot.set(ItemStack.EMPTY);
    }
  }

  /**
   * Returns whether every slot in this handler is empty.
   *
   * @return {@code true} if all slots are empty
   */
  default boolean isEmpty() {
    for (Slot slot : slots()) {
      if (!slot.isEmpty()) {
        return false;
      }
    }
    return true;
  }
}
