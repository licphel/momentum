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

package net.fmhi.gfx.shader;

/**
 * Creates a new {@code Slot} declaring a single shader resource binding.
 *
 * @param count   the number of resources in this binding (e.g. array size, normally 1)
 * @param name    the shader-side variable name
 * @param stages  bitmask of {@link ShaderType} flags indicating which stages access this resource
 * @param type    the kind of resource ({@link ResourceType#TEXTURE} or {@link ResourceType#UNIFORM_BUFFER})
 * @param binding the resolved GPU binding index (assigned by {@link ResourceSetLayout#bake})
 */
public record Slot(int count, String name, int stages, ResourceType type, int binding) {
  /**
   * Creates a new {@code Slot} without a resolved binding index.
   *
   * <p>The binding is set to 0 and will be assigned by
   * {@link ResourceSetLayout#bake}.
   *
   * @param count  the number of resources in this binding
   * @param name   the shader-side variable name
   * @param stages bitmask of {@link ShaderType} flags
   * @param type   the kind of resource
   */
  public Slot(int count, String name, int stages, ResourceType type) {
    this(count, name, stages, type, 0);
  }
}
