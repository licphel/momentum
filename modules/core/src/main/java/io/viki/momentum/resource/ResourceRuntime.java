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

package io.viki.momentum.resource;

import io.viki.momentum.util.InternalApi;

/**
 * Runtime switches shared by resource providers.
 *
 * <p>This runtime this specifically for GraalVM native image publish.
 */
@InternalApi
public final class ResourceRuntime {
  /** Resource list embedded by the Native Image build. */
  public static final String NATIVE_RESOURCE_INDEX = "META-INF/momentum/resources.list";
  private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

  private static volatile boolean forcedNativeImage;

  private ResourceRuntime() {
  }

  /**
   * Returns whether resource providers must use the embedded Native Image index.
   *
   * @return if this application is natively published
   */
  public static boolean nativeImage() {
    return forcedNativeImage || Boolean.getBoolean("momentum.native")
        || System.getProperty(IMAGE_CODE_PROPERTY) != null;
  }

  /**
   * Overrides the resource indexing mode before resource discovery starts.
   *
   * @param enabled native image flag to set
   */
  public static void setNativeImage(boolean enabled) {
    forcedNativeImage = enabled;
  }
}
