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

package io.viki.momentum.codec.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A growable page-aligned byte store with malloc-style free-range allocation.
 *
 * <p>Allocations are rounded up to complete pages. Freed ranges are merged with
 * adjacent ranges and reused by a best-fit search. When fragmentation becomes
 * undesirable, {@link #compact()} moves live allocations toward the beginning
 * of the store and updates their handles. A handle obtained before compaction
 * remains valid; a {@link ByteBuffer} view obtained from {@link #view(Allocation)}
 * must be reacquired after compaction.
 *
 * <p>The buffer is deliberately not thread-safe. A file container should use
 * its own read/write coordination around allocation, release, and compaction;
 * read-only views may be used concurrently when no compaction or mutation is
 * in progress.
 */
public final class PageBuffer {
  /** Default page size for indexed records. */
  public static final int DEFAULT_PAGE_SIZE = 4096;
  /** Default initial allocation avoids growing for the first few records. */
  public static final int DEFAULT_INITIAL_PAGES = 16;

  private final int pageSize;
  private byte[] storage;
  private int pageCount;
  private int usedPages;
  private final NavigableMap<Integer, Integer> freeRanges = new TreeMap<>();
  private final NavigableMap<Integer, Allocation> allocations = new TreeMap<>();

  private PageBuffer(int pageSize, int initialPages) {
    this.pageSize = pageSize;
    pageCount = initialPages;
    storage = new byte[Math.multiplyExact(pageSize, initialPages)];
    if (initialPages > 0) {
      freeRanges.put(0, initialPages);
    }
  }

  /**
   * Opens a page buffer with the default page size and initial capacity.
   *
   * @return a new page buffer
   */
  public static PageBuffer open() {
    return open(DEFAULT_PAGE_SIZE, DEFAULT_INITIAL_PAGES);
  }

  /**
   * Opens an empty page buffer that grows in {@code pageSize} units.
   *
   * @param pageSize size of one allocation page in bytes
   * @return a new empty page buffer
   */
  public static PageBuffer open(int pageSize) {
    return open(pageSize, 0);
  }

  /**
   * Opens a page buffer with an initial number of free pages.
   *
   * @param pageSize size of one allocation page in bytes
   * @param initialPages initial free page count
   * @return a new page buffer
   */
  public static PageBuffer open(int pageSize, int initialPages) {
    if (pageSize <= 0 || initialPages < 0) {
      throw new IllegalArgumentException("Page size must be positive and initial pages non-negative");
    }
    return new PageBuffer(pageSize, initialPages);
  }

  /**
   * Returns the fixed allocation page size in bytes.
   *
   * @return allocation page size in bytes
   */
  public int pageSize() {
    return pageSize;
  }

  /**
   * Returns the current total number of pages, including free pages.
   *
   * @return total page count
   */
  public int pageCount() {
    return pageCount;
  }

  /**
   * Returns the current storage capacity in bytes.
   *
   * @return storage capacity in bytes
   */
  public int capacity() {
    return storage.length;
  }

  /**
   * Returns the number of pages occupied by live allocations.
   *
   * @return pages occupied by live allocations
   */
  public int usedPages() {
    return usedPages;
  }

  /**
   * Returns the number of pages available for new allocations.
   *
   * @return pages available for allocation
   */
  public int freePages() {
    return pageCount - usedPages;
  }

  /**
   * Returns the number of live allocation handles.
   *
   * @return live allocation count
   */
  public int allocationCount() {
    return allocations.size();
  }

  /**
   * Allocates enough whole pages for {@code bytes} and returns its mutable handle.
   *
   * <p>The search is best-fit, so the smallest free range that can hold the
   * request is selected before extending the store.
   *
   * @param bytes requested logical byte length, greater than zero
   * @return the allocation handle
   */
  public Allocation allocate(int bytes) {
    if (bytes <= 0) {
      throw new IllegalArgumentException("Allocation size must be positive: " + bytes);
    }
    int pages = pagesFor(bytes);
    Map.Entry<Integer, Integer> best = findBestRange(pages);
    if (best == null) {
      grow(pages);
      best = findBestRange(pages);
      if (best == null) {
        throw new IllegalStateException("PageBuffer could not allocate grown pages");
      }
    }
    int start = best.getKey();
    takeFreeRange(best.getKey(), best.getValue(), pages);
    Allocation allocation = new Allocation(this, start, pages, bytes);
    allocations.put(start, allocation);
    usedPages += pages;
    return allocation;
  }

  /**
   * Releases an allocation and merges its pages into neighboring free ranges.
   *
   * @param allocation allocation owned by this buffer
   */
  public void free(Allocation allocation) {
    requireOwned(allocation);
    allocations.remove(allocation.pageStart);
    allocation.active = false;
    usedPages -= allocation.pages;
    addFreeRange(allocation.pageStart, allocation.pages);
  }

  /**
   * Returns a writable view limited to the allocation's logical byte length.
   *
   * <p>The view is invalidated by {@link #compact()} or by a later storage growth;
   * reacquire it from the handle after either operation.
   *
   * @param allocation allocation whose bytes should be viewed
   * @return writable view of the allocation's logical bytes
   */
  public ByteBuffer view(Allocation allocation) {
    requireOwned(allocation);
    return ByteBuffer.wrap(storage, allocation.offset(), allocation.length).slice();
  }

  /**
   * Returns the fraction of free pages that are not in the largest free range.
   *
   * @return fragmentation ratio in the range {@code [0, 1]}
   */
  public double fragmentation() {
    int free = freePages();
    if (free == 0) {
      return 0;
    }
    int largest = 0;
    for (int pages : freeRanges.values()) {
      largest = Math.max(largest, pages);
    }
    return (free - largest) / (double) free;
  }

  /**
   * Compacts live allocations toward page zero and returns the number moved.
   * Handles remain valid and report their new offsets after the operation.
   *
   * @return number of allocations moved
   */
  public int compact() {
    if (allocations.isEmpty()) {
      freeRanges.clear();
      if (pageCount > 0) {
        freeRanges.put(0, pageCount);
      }
      return 0;
    }
    List<Allocation> live = new ArrayList<>(allocations.values());
    allocations.clear();
    int destination = 0;
    int moved = 0;
    for (Allocation allocation : live) {
      int source = allocation.pageStart;
      if (source != destination) {
        System.arraycopy(storage, source * pageSize, storage, destination * pageSize,
            allocation.pages * pageSize);
        allocation.pageStart = destination;
        moved++;
      }
      allocations.put(destination, allocation);
      destination += allocation.pages;
    }
    freeRanges.clear();
    if (destination < pageCount) {
      freeRanges.put(destination, pageCount - destination);
    }
    return moved;
  }

  /**
   * Compacts when fragmentation has reached {@code threshold}.
   *
   * @param threshold fragmentation ratio that triggers compaction, from {@code 0} to {@code 1}
   * @return whether compaction was performed
   */
  public boolean compactIf(double threshold) {
    if (threshold < 0 || threshold > 1) {
      throw new IllegalArgumentException("Fragmentation threshold must be between 0 and 1");
    }
    if (fragmentation() < threshold) {
      return false;
    }
    compact();
    return true;
  }

  private int pagesFor(int bytes) {
    return Math.addExact(bytes - 1, pageSize) / pageSize;
  }

  private Map.@Nullable Entry<Integer, Integer> findBestRange(int pages) {
    Map.Entry<Integer, Integer> best = null;
    for (Map.Entry<Integer, Integer> range : freeRanges.entrySet()) {
      if (range.getValue() >= pages
          && (best == null || range.getValue() < best.getValue())) {
        best = range;
      }
    }
    return best;
  }

  private void grow(int additionalPages) {
    int requiredPages = Math.addExact(pageCount, additionalPages);
    int nextPages = Math.max(requiredPages, Math.max(1, pageCount * 2));
    if (nextPages < requiredPages) {
      nextPages = requiredPages;
    }
    byte[] next = new byte[Math.multiplyExact(pageSize, nextPages)];
    System.arraycopy(storage, 0, next, 0, storage.length);
    storage = next;
    if (pageCount < nextPages) {
      addFreeRange(pageCount, nextPages - pageCount);
      pageCount = nextPages;
    }
  }

  private void takeFreeRange(int start, int length, int pages) {
    freeRanges.remove(start);
    if (length > pages) {
      freeRanges.put(start + pages, length - pages);
    }
  }

  private void addFreeRange(int start, int pages) {
    int mergedStart = start;
    int mergedPages = pages;
    Map.Entry<Integer, Integer> lower = freeRanges.lowerEntry(start);
    if (lower != null && lower.getKey() + lower.getValue() == start) {
      mergedStart = lower.getKey();
      mergedPages += lower.getValue();
      freeRanges.remove(lower.getKey());
    }
    Map.Entry<Integer, Integer> higher = freeRanges.ceilingEntry(start);
    if (higher != null && start + pages == higher.getKey()) {
      mergedPages += higher.getValue();
      freeRanges.remove(higher.getKey());
    }
    freeRanges.put(mergedStart, mergedPages);
  }

  private void requireOwned(Allocation allocation) {
    if (allocation.owner != this || !allocation.active) {
      throw new IllegalArgumentException("Allocation does not belong to this PageBuffer");
    }
  }

  /**
   * Mutable allocation handle whose offset changes when the owner is compacted.
   *
   * <p>The handle is valid until its allocation is released. It is not
   * thread-safe and must be used with the owning buffer's synchronization.
   */
  public static final class Allocation {
    private final PageBuffer owner;
    private final int pages;
    private final int length;
    private int pageStart;
    private boolean active = true;

    private Allocation(PageBuffer owner, int pageStart, int pages, int length) {
      this.owner = owner;
      this.pageStart = pageStart;
      this.pages = pages;
      this.length = length;
    }

    /**
     * Returns the current byte offset of this allocation.
     *
     * @return current byte offset
     */
    public int offset() {
      owner.requireOwned(this);
      return pageStart * owner.pageSize;
    }

    /**
     * Returns the requested logical byte length.
     *
     * @return logical byte length
     */
    public int length() {
      owner.requireOwned(this);
      return length;
    }

    /**
     * Returns the page-rounded capacity reserved by this allocation.
     *
     * @return reserved capacity in bytes
     */
    public int capacity() {
      owner.requireOwned(this);
      return pages * owner.pageSize;
    }
  }
}
