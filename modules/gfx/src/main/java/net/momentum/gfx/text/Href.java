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

package net.momentum.gfx.text;

import net.momentum.util.logging.Log;
import net.momentum.util.logging.Logger;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A hyperlink attached to a {@link Literal}.
 *
 * <p>When a user clicks on a text region carrying this metadata, the linked
 * URL can be opened via {@link #raiseBrowser()}.
 *
 * @param url the target URL
 */
public record Href(String url) implements Meta {
  private static final Logger LOGGER = Log.getLogger();

  /**
   * Opens the URL in the system default browser.
   *
   * <p>If the desktop environment does not support the browse action, a
   * warning is logged and the call returns silently.
   *
   * @throws IllegalArgumentException if the URL is malformed
   * @throws UncheckedIOException     if the browser cannot be opened
   */
  public void raiseBrowser() {
    if (!Desktop.isDesktopSupported()) {
      LOGGER.warn("Cannot open" + url + ": Desktop is not supported.");
      return;
    }
    try {
      URI uri = new URI(url);
      Desktop.getDesktop().browse(uri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URL: " + url, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to open browser for: " + url, e);
    }
  }
}
