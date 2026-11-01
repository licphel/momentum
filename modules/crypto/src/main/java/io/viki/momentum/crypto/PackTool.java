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

package io.viki.momentum.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Packages a JAR file into the encrypted format consumed by {@link SecurePack}.
 * The generated key is written beside the output package for release tooling.
 */
public final class PackTool {
  private PackTool() {
  }

  /**
   * Packages the input JAR using the command-line input and output paths.
   *
   * @param args arguments in the form {@code --input <jar> --output <pack>}
   * @throws IllegalArgumentException if the arguments are malformed or the key is invalid
   * @throws IOException if the input, key, or output cannot be read or written
   */
  static void main(String[] args) throws IOException {
    if (args.length != 4 || !args[0].equals("--input") || !args[2].equals("--output")) {
      throw new IllegalArgumentException("Usage: PackTool --input X.jar --output X.pack");
    }
    Path input = Path.of(args[1]);
    Path output = Path.of(args[3]);
    Path keyFile = output.resolveSibling(output.getFileName() + ".key");
    byte[] key = Files.isRegularFile(keyFile) ? Files.readAllBytes(keyFile) : CryptoBox.randomKey();
    CryptoBox.requireKey(key);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.write(keyFile, key);
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (JarFile jar = new JarFile(input.toFile())) {
      jar.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> readEntry(jar, entry, entries));
    }
    SecurePack.write(output, entries, key);
  }

  /**
   * Copies one JAR entry into the package source map.
   *
   * @param jar JAR containing the entry
   * @param entry entry to read
   * @param entries destination map keyed by entry path
   * @throws CryptoException if the entry cannot be read
   */
  private static void readEntry(JarFile jar, JarEntry entry, Map<String, byte[]> entries) {
    try {
      entries.put(entry.getName(), jar.getInputStream(entry).readAllBytes());
    } catch (IOException exception) {
      throw new CryptoException("Unable to read JAR entry: " + entry.getName(), exception);
    }
  }
}
