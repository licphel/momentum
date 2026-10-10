package io.viki.momentum.util.ecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-capacity, dense ECS archetype with primitive component columns.
 * Confined to one owning thread. Systems operate directly on column arrays;
 * row indices are temporary, not stable entity identities.
 * Creation is O(1); swap-removal is O(number of columns), without allocation.
 */
public final class EntityTable {
  private final int capacity;
  private final List<Column> columns = new ArrayList<>();
  private int size;

  /**
   * Creates an empty table with a fixed limit on the number of active rows.
   *
   * @param capacity the maximum row count, must be nonnegative
   * @throws IllegalArgumentException if {@code capacity} is negative
   */
  public EntityTable(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("Negative entity capacity: " + capacity);
    }
    this.capacity = capacity;
  }

  /**
   * Returns the number of active rows.
   *
   * @return the active row count
   */
  public int size() {
    return size;
  }

  /**
   * Returns the maximum number of active rows this table can hold.
   *
   * @return the fixed row capacity
   */
  public int capacity() {
    return capacity;
  }

  /**
   * Reserves the next active row, or leaves the table unchanged when it is full.
   *
   * <p>The caller must initialize every component before making the row available to
   * systems. This operation takes O(1) time and allocates no objects.
   *
   * @return the newly reserved row index, or {@code -1} if the table is full
   */
  public int create() {
    return size == capacity ? -1 : size++;
  }

  /**
   * Removes an active row, replacing its components with those of the last active row.
   *
   * <p>The vacated last row is zeroed in every column. Row order is not preserved;
   * references to the moved entity's previous row index must be updated by the caller.
   * This operation takes O(number of columns) time and allocates no objects.
   *
   * @param row the active row index to remove
   * @throws IndexOutOfBoundsException if {@code row} is negative or at least {@link #size()}
   */
  public void remove(int row) {
    if (row < 0 || row >= size) {
      throw new IndexOutOfBoundsException(row);
    }
    int last = --size;
    for (Column column : columns) {
      column.remove(row, last);
    }
  }

  /**
   * Removes all active rows and zeroes their components, retaining the capacity and columns.
   *
   * <p>All previous active row indices become invalid. This operation takes
   * O(active row count multiplied by number of columns) time.
   */
  public void clear() {
    while (size > 0) {
      remove(size - 1);
    }
  }

  /**
   * Adds a zero-initialized {@code double} component column while the table is empty.
   *
   * <p>The new column has {@link #capacity()} entries and remains attached when rows
   * are removed or cleared. Creating it takes O(capacity) time and storage.
   *
   * @return the attached column for direct component access
   * @throws IllegalStateException if the table has active rows
   */
  public DoubleColumn doubles() {
    return attach(new DoubleColumn(capacity));
  }

  /**
   * Adds a zero-initialized {@code float} component column while the table is empty.
   *
   * <p>The new column has {@link #capacity()} entries and remains attached when rows
   * are removed or cleared. Creating it takes O(capacity) time and storage.
   *
   * @return the attached column for direct component access
   * @throws IllegalStateException if the table has active rows
   */
  public FloatColumn floats() {
    return attach(new FloatColumn(capacity));
  }

  /**
   * Adds a zero-initialized {@code int} component column while the table is empty.
   *
   * <p>The new column has {@link #capacity()} entries and remains attached when rows
   * are removed or cleared. Creating it takes O(capacity) time and storage.
   *
   * @return the attached column for direct component access
   * @throws IllegalStateException if the table has active rows
   */
  public IntColumn ints() {
    return attach(new IntColumn(capacity));
  }

  /**
   * Adds a zero-initialized {@code byte} component column while the table is empty.
   *
   * <p>The new column has {@link #capacity()} entries and remains attached when rows
   * are removed or cleared. Creating it takes O(capacity) time and storage.
   *
   * @return the attached column for direct component access
   * @throws IllegalStateException if the table has active rows
   */
  public ByteColumn bytes() {
    return attach(new ByteColumn(capacity));
  }

  private <T extends Column> T attach(T column) {
    if (size != 0) {
      throw new IllegalStateException("Define the archetype before creating entities");
    }
    columns.add(column);
    return column;
  }

  private interface Column {
    /**
     * Copies the source row's component to the destination and zeroes the source.
     *
     * <p>If both indices are equal, that component is zeroed. This storage operation
     * does not change the owning table's active row count and takes O(1) time.
     *
     * @param row  the destination index within this column's storage
     * @param last the source index within this column's storage
     * @throws IndexOutOfBoundsException if either index is outside the storage
     */
    void remove(int row, int last);
  }

  /**
   * Provides direct access to one {@code double} component for each row of its owning table.
   *
   * <p>Only indices below the table's current size represent active entities. Row
   * indices may change after removal. Access is confined to the table's owning thread.
   */
  public static final class DoubleColumn implements Column {
    /** Component storage with the owning table's capacity; active rows occupy its leading entries. */
    public final double[] values;

    private DoubleColumn(int capacity) {
      values = new double[capacity];
    }

    @Override
    public void remove(int row, int last) {
      values[row] = values[last];
      values[last] = 0;
    }
  }

  /**
   * Provides direct access to one {@code float} component for each row of its owning table.
   *
   * <p>Only indices below the table's current size represent active entities. Row
   * indices may change after removal. Access is confined to the table's owning thread.
   */
  public static final class FloatColumn implements Column {
    /** Component storage with the owning table's capacity; active rows occupy its leading entries. */
    public final float[] values;

    private FloatColumn(int capacity) {
      values = new float[capacity];
    }

    @Override
    public void remove(int row, int last) {
      values[row] = values[last];
      values[last] = 0;
    }
  }

  /**
   * Provides direct access to one {@code int} component for each row of its owning table.
   *
   * <p>Only indices below the table's current size represent active entities. Row
   * indices may change after removal. Access is confined to the table's owning thread.
   */
  public static final class IntColumn implements Column {
    /** Component storage with the owning table's capacity; active rows occupy its leading entries. */
    public final int[] values;

    private IntColumn(int capacity) {
      values = new int[capacity];
    }

    @Override
    public void remove(int row, int last) {
      values[row] = values[last];
      values[last] = 0;
    }
  }

  /**
   * Provides direct access to one {@code byte} component for each row of its owning table.
   *
   * <p>Only indices below the table's current size represent active entities. Row
   * indices may change after removal. Access is confined to the table's owning thread.
   */
  public static final class ByteColumn implements Column {
    /** Component storage with the owning table's capacity. */
    public final byte[] values;

    private ByteColumn(int capacity) {
      values = new byte[capacity];
    }

    @Override
    public void remove(int row, int last) {
      values[row] = values[last];
      values[last] = 0;
    }
  }
}
