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

package net.momentum.util.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * An {@link Output} appending UTF-8 log lines to a file.
 *
 * <p>The file (and parent directories) is created on construction and kept open with a buffered writer for the life of
 * the output; {@link #flush()} pushes buffered bytes to disk and {@link #close()} closes the channel. Because the
 * format includes no year, rollover by year is left to whoever manages the log directory.
 */
public final class FileAccess implements Output {
  private final BufferedWriter writer;

  /**
   * Opens (or creates) the log file for appending.
   *
   * @param path the log file path
   * @throws IOException if the file or its parent directories cannot be created/opened
   */
  public FileAccess(Path path) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  @Override
  public void write(LogRecord record) {
    try {
      writer.write(record.line());
    } catch (IOException e) {
      // Ignored
    }
  }

  @Override
  public void flush() {
    try {
      writer.flush();
    } catch (IOException e) {
      // Ignored
    }
  }

  @Override
  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      // Ignored
    }
  }
}
