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

package io.viki.momentum.asset;

import io.viki.momentum.util.Identifier;
import io.viki.momentum.resource.Resource;

import java.io.IOException;

/**
 * Plans loading for one resolved asset. Invoked only on the queue's owner thread.
 */
@FunctionalInterface
public interface AssetProcessor {
  /**
   * Schedules the asset's stages using run() or prepare(); do not perform I/O here.
   *
   * <p>The processor owns any stream it opens from {@code resources} and must
   * close that stream when processing is complete.
   *
   * @param id        the resolved asset identifier
   * @param resources the provider-local resource source
   * @param queue     the task queue that can be modified reversely
   * @throws IOException if the resource cannot be read
   */
  void process(Identifier id, Resource resources, TaskQueue.Plan queue) throws IOException;
}
