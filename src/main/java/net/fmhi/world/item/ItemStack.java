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

import net.fmhi.Registries;
import net.fmhi.codec.tag.CompoundTag;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A counted reference to an {@link ItemLike} with optional attached data.
 *
 * <p>An item stack holds an item type, a count, and optional
 * {@link CompoundTag} components. The singleton {@link #EMPTY} represents
 * the absence of an item. Most mutating operations throw
 * {@link IllegalStateException} when called on an empty stack.
 *
 * <p>Static helpers such as {@link #isSameItem(ItemStack, ItemStack)},
 * {@link #isSameItemSameData(ItemStack, ItemStack)}, and
 * {@link #isMergeable(ItemStack, ItemStack)} support inventory logic.
 *
 * @see ItemLike
 * @see SlotImpl
 */
public final class ItemStack {
  /** The empty stack singleton. Never use {@code ==} to compare emptiness. */
  public static final ItemStack EMPTY = new ItemStack(Registries.AIR_ITEM.get(), 0);

  private final ItemLike item;
  private int count;
  private @Nullable CompoundTag data;

  private ItemStack(@Nullable ItemLike item, int count) {
    if (count < 0) {
      throw new IllegalArgumentException("count < 0");
    }
    this.item = item;
    this.count = count;
  }

  /**
   * Creates a new stack with the given item and count.
   *
   * @param item  the item type
   * @param count the initial count
   * @return a new item stack
   */
  public static ItemStack of(ItemLike item, int count) {
    return new ItemStack(item, count);
  }

  /**
   * Creates a new stack with a single item.
   *
   * @param item the item type
   * @return a new item stack with count 1
   */
  public static ItemStack of(ItemLike item) {
    return new ItemStack(item, 1);
  }

  /**
   * Returns whether two stacks share the same item type.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if both stacks have the same item type
   */
  public static boolean isSameItem(ItemStack a, ItemStack b) {
    return Objects.equals(a.item(), b.item());
  }

  /**
   * Returns whether two stacks share the same item type and attached data.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if both item type and data are equal
   */
  public static boolean isSameItemSameData(ItemStack a, ItemStack b) {
    return isSameItem(a, b) && Objects.equals(a.getData(), b.getData());
  }

  /**
   * Returns whether two stacks can be merged into a single slot.
   *
   * <p>Stacks are mergeable if either is empty, or if they share the same
   * item type and data.
   *
   * @param a the first stack
   * @param b the second stack
   * @return {@code true} if the stacks are mergeable
   */
  public static boolean isMergeable(ItemStack a, ItemStack b) {
    return a.isEmpty() || b.isEmpty() || isSameItemSameData(a, b);
  }

  /**
   * Returns the item type of this stack.
   *
   * @return the item type, or {@code null} if this stack is empty
   */
  public @Nullable ItemLike item() {
    return isEmpty() ? null : item;
  }

  /**
   * Returns the maximum stack size for the item type.
   *
   * @return the max stack size, or {@code 0} if this stack is empty
   */
  public int maxStackSize() {
    return isEmpty() ? 0 : item.maxStackSize();
  }

  /**
   * Splits up to {@code take} items from this stack, shrinking it and returning
   * a new stack with the taken count.
   *
   * <p>If {@code take} exceeds the current count or is non-positive, returns
   * {@link #EMPTY}.
   *
   * @param take the number of items to split off
   * @return the taken items, or {@link #EMPTY} if the split failed
   * @throws IllegalStateException if this stack is empty
   */
  public ItemStack split(int take) {
    checkModifiable();
    if (count < take || take <= 0) {
      return EMPTY;
    }
    shrink(take);
    return copyWithCount(take);
  }

  /**
   * Splits up to {@code take} items, clamped to the actual count.
   *
   * @param take the desired number of items to split
   * @return the taken items, or {@link #EMPTY} if the count is zero
   * @throws IllegalStateException if this stack is empty
   */
  public ItemStack splitByMaxEffort(int take) {
    return split(Math.min(take, count));
  }

  /**
   * Returns the current stack count.
   *
   * @return the count, or {@code 0} if this stack is empty
   */
  public int count() {
    return isEmpty() ? 0 : count;
  }

  /**
   * Returns whether this stack is empty.
   *
   * @return {@code true} if the count is zero or the item is {@code null}
   */
  public boolean isEmpty() {
    return count <= 0 || item == null;
  }

  /**
   * Grows the count by {@code n}, clamped to the item's maximum stack size.
   *
   * @param n the amount to add
   * @throws IllegalStateException if this stack is empty
   */
  public void grow(int n) {
    checkModifiable();
    count = Math.clamp(count + n, 0, maxStackSize());
  }

  /**
   * Reduces the count by {@code n}, clamped to 0.
   *
   * @param n the amount to subtract
   * @throws IllegalStateException if this stack is empty
   */
  public void shrink(int n) {
    checkModifiable();
    count = Math.clamp(count - n, 0, maxStackSize());
  }

  /**
   * Sets the count to the given value.
   *
   * @param count the new count
   * @throws IllegalStateException if this stack is empty
   */
  public void setCount(int count) {
    checkModifiable();
    this.count = count;
  }

  /**
   * Returns a copy of this stack with the given count, sharing the same item and data.
   *
   * @param newCount the count for the copy
   * @return a new stack with the same item and data
   */
  public ItemStack copyWithCount(int newCount) {
    ItemStack copy = new ItemStack(item, newCount);
    if (data != null) {
      copy.data = data.copy();
    }
    return copy;
  }

  /**
   * Returns a full copy of this stack, including the same item, count, and data.
   *
   * @return a copy of this stack
   */
  public ItemStack copy() {
    return copyWithCount(count);
  }

  /**
   * Returns the attached component data.
   *
   * @return the data, or {@code null} if not set or if this stack is empty
   */
  public @Nullable CompoundTag getData() {
    return isEmpty() ? null : data;
  }

  /**
   * Sets the attached component data.
   *
   * @param data the data to attach, or {@code null} to clear
   * @throws IllegalStateException if this stack is empty
   */
  public void setData(@Nullable CompoundTag data) {
    checkModifiable();
    this.data = data;
  }

  /**
   * Returns the attached component data, creating a new empty tag if none is present.
   *
   * @return the data; never {@code null}
   * @throws IllegalStateException if this stack is empty
   */
  public CompoundTag getOrCreateData() {
    checkModifiable();
    if (data == null) {
      data = new CompoundTag();
    }
    return data;
  }

  @Override
  public int hashCode() {
    if (isEmpty()) {
      return 0;
    }
    int h = item.hashCode() * 31 + count;
    if (data != null) {
      h = h * 31 + data.hashCode();
    }
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ItemStack that)) {
      return false;
    }
    if (isEmpty() && that.isEmpty()) {
      return true; // Special workaround
    }
    if (count != that.count) {
      return false;
    }
    return isSameItemSameData(this, that);
  }

  @Override
  public String toString() {
    return isEmpty() ? "empty" : count + "x " + item;
  }

  private void checkModifiable() {
    if (isEmpty()) {
      throw new IllegalStateException("Stack is empty");
    }
  }
}
