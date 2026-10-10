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

package io.viki.momentum.sfx.openal.ext;

import io.viki.momentum.sfx.ext.SfxEffect;
import io.viki.momentum.util.InternalApi;

import java.util.Objects;

/**
 * OpenAL EFX registrations used by the optional sfx effect registry.
 */
@InternalApi
public final class OpenALEffects {
  {
    SfxEffect.register("filter", effect -> new Object[] {new ExtEfxFilter(Objects.requireNonNull(effect.argument(0)))});
    SfxEffect.register("reverb", effect -> new Object[] {new ExtEfxReverb(Objects.requireNonNull(effect.argument(0)))});
    SfxEffect.register("echo", effect -> new Object[] {new ExtEfxEcho(Objects.requireNonNull(effect.argument(0)))});
  }
}
