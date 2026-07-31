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

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A single inventory slot holding an optional {@link ItemStack}.
 *
 * <p>A slot may be empty, indicated by holding {@link ItemStack#EMPTY}.
 * The slot limit caps how many items can be inserted at once, and each
 * slot tracks its own item count independently.
 *
 * @see Slot
 * @see ItemStack
 */
public class SlotImpl implements Slot {
  private final int slotLimit;
  private ItemStack stack = ItemStack.EMPTY;
  private @Nullable Predicate<ItemStack> validator;

  /**
   * Creates an empty slot with no slot-specific limit.
   */
  public SlotImpl() {
    this(Integer.MAX_VALUE);
  }

  /**
   * Creates an empty slot with the given maximum number of items.
   *
   * @param slotLimit the maximum number of items this slot can accept in a single insertion
   */
  public SlotImpl(int slotLimit) {
    this.slotLimit = slotLimit;
  }

  /**
   * Sets the item stack validator of this slot.
   *
   * @param validator the validator
   * @return self for chaining
   */
  public SlotImpl withValidator(@Nullable Predicate<ItemStack> validator) {
    this.validator = validator;
    return this;
  }

  @Override
  public ItemStack get() {
    return stack;
  }

  @Override
  public void set(ItemStack stack) {
    this.stack = stack;
  }

  @Override
  public boolean validate(ItemStack stack) {
    return validator == null || validator.test(stack);
  }

  @Override
  public int slotLimit() {
    return slotLimit;
  }

  @Override
  public ItemStack remove(int count) {
    if (count <= 0) {
      return ItemStack.EMPTY;
    }
    int toRemove = Math.min(count, stack.count());
    ItemStack result = stack.copyWithCount(toRemove);
    stack.shrink(toRemove);
    if (stack.isEmpty()) {
      stack = ItemStack.EMPTY;
    }
    return result;
  }

  @Override
  public ItemStack insert(ItemStack incoming) {
    if (incoming.isEmpty()) {
      return ItemStack.EMPTY;
    }

    if (stack.isEmpty()) {
      stack = incoming;
      return ItemStack.EMPTY;
    }

    if (ItemStack.isMergeable(incoming, stack)) {
      int room = Math.min(slotLimit(), incoming.maxStackSize()) - stack.count();
      if (room <= 0) {
        return incoming;
      }
      int add = Math.min(incoming.count(), room);
      stack.grow(add);
      return incoming.copyWithCount(incoming.count() - add);
    }

    return incoming;
  }

  @Override
  public String toString() {
    return stack.toString();
  }
}
