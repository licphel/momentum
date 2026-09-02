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

/**
 * Resource locating and asset loading.
 *
 * <p>Every resource is addressed as a {@link java.nio.file.Path}. A JAR is
 * opened as a {@link java.nio.file.FileSystem} (the built-in {@code jar:}
 * provider), so packaged content is walked and streamed with the same
 * {@link java.nio.file.Files} operations as a plain resource directory in
 * development — no scheme branching or archive enumeration. Resources are
 * located through {@link net.momentum.asset.AssetFinder}, the single entry
 * point for locating and streaming resources; whole-content reads go through
 * {@link net.momentum.util.ResourceProvider}. Engine and mod resources all
 * ship inside JARs; no content lives beside the JAR on disk.
 *
 * <p>{@link net.momentum.asset.AssetLoader} streams those resources into the
 * thread-safe {@link net.momentum.asset.Assets} store keyed by
 * {@link net.momentum.util.Identifier}, with hot-reloadable {@link net.momentum.asset.Ref}
 * containers and derived {@link net.momentum.asset.Reloadable} values.
 */
@NullMarked
package net.momentum.asset;

import org.jspecify.annotations.NullMarked;
