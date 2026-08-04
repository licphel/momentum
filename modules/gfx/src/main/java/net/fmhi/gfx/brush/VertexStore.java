package net.fmhi.gfx.brush;

import net.fmhi.codec.streaming.CursorBuffer;

/**
 * Staging area for vertex and index data being assembled for rendering.
 */
public interface VertexStore {
  /**
   * Returns the buffer holding interleaved vertex data.
   *
   * @return the vertex buffer
   */
  CursorBuffer vertices();

  /**
   * Returns the buffer holding index data.
   *
   * @return the index buffer
   */
  CursorBuffer indices();

  /**
   * Returns the number of vertices written so far.
   *
   * @return the vertex count
   */
  int vertexCount();

  /**
   * Returns the number of indices written so far.
   *
   * @return the index count
   */
  int indexCount();

  /**
   * Records the given number of additional vertices.
   *
   * @param count the number of vertices written
   */
  void addVertex(int count);

  /**
   * Records the given number of additional indices.
   *
   * @param count the number of indices written
   */
  void addIndex(int count);

  /**
   * Resets the recorded counts and clears both buffers.
   */
  void clear();

  /**
   * Returns the recorded vertex data as a byte array; whether the array is a copy or
   * live backing storage depends on the implementation.
   *
   * @return the vertex data bytes
   */
  byte[] recordVertices();

  /**
   * Returns the recorded index data as a byte array; whether the array is a copy or
   * live backing storage depends on the implementation.
   *
   * @return the index data bytes
   */
  byte[] recordIndices();
}
