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

import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes authenticated encrypted resource packs whose paths and contents are protected.
 * The outer file does not expose logical entry names. Instances are not thread-safe; coordinate
 * access with {@link #close()} so decrypted data is not cleared during a read.
 */
public final class SecurePack implements AutoCloseable {
  private static final int MAGIC = 0x52504B31;
  private static final int VERSION = 1;
  private final byte[] packBytes;
  private final Map<String, Entry> entries;
  private final byte[] key;

  private SecurePack(byte[] packBytes, Map<String, Entry> entries, byte[] key) {
    this.packBytes = packBytes;
    this.entries = entries;
    this.key = key.clone();
  }

  /**
   * Writes a protected package containing the supplied named resources.
   *
   * @param file destination package path
   * @param sources resource bytes keyed by their logical paths
   * @param key AES-256 key containing {@link CryptoBox#KEY_BYTES} bytes
   * @throws IllegalArgumentException if {@code key} has an invalid length
   * @throws CryptoException if encryption or package writing fails
   */
  public static void write(Path file, Map<String, byte[]> sources, byte[] key) {
    CryptoBox.requireKey(key);
    try {
      byte[] salt = CryptoBox.randomBytes(CryptoBox.SALT_BYTES);
      ByteArrayOutputStream indexBytes = new ByteArrayOutputStream();
      try (DataOutputStream index = new DataOutputStream(indexBytes)) {
        index.writeInt(sources.size());
        for (Map.Entry<String, byte[]> source : sources.entrySet()) {
          byte[] name = source.getKey().getBytes(StandardCharsets.UTF_8);
          byte[] nonce = CryptoBox.randomBytes(CryptoBox.NONCE_BYTES);
          byte[] aad = name;
          byte[] encrypted = CryptoBox.encrypt(key, nonce, aad, source.getValue());
          index.writeInt(name.length);
          index.write(name);
          index.write(nonce);
          index.writeInt(encrypted.length);
          index.write(encrypted);
        }
      }
      byte[] encryptedIndex = CryptoBox.encrypt(key, Arrays.copyOf(salt, CryptoBox.NONCE_BYTES),
          header(salt), indexBytes.toByteArray());
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (DataOutputStream stream = new DataOutputStream(output)) {
        stream.writeInt(MAGIC);
        stream.writeInt(VERSION);
        stream.write(salt);
        stream.writeInt(encryptedIndex.length);
        stream.write(encryptedIndex);
      }
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(file, output.toByteArray());
    } catch (IOException exception) {
      throw new CryptoException("Unable to write protected package: " + file, exception);
    }
  }

  /**
   * Opens and authenticates a protected package for resource lookup.
   *
   * @param file package path to read
   * @param key AES-256 key containing {@link CryptoBox#KEY_BYTES} bytes
   * @return an open package reader that owns its decrypted index and key copy
   * @throws IllegalArgumentException if {@code key} has an invalid length
   * @throws CryptoException if the package cannot be read, parsed, or authenticated
   */
  public static SecurePack open(Path file, byte[] key) {
    CryptoBox.requireKey(key);
    try {
      byte[] bytes = Files.readAllBytes(file);
      try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
        if (stream.readInt() != MAGIC || stream.readInt() != VERSION) {
          throw new CryptoException("Unsupported protected package: " + file);
        }
        byte[] salt = stream.readNBytes(CryptoBox.SALT_BYTES);
        int encryptedLength = stream.readInt();
        byte[] encryptedIndex = stream.readNBytes(encryptedLength);
        byte[] plainIndex = CryptoBox.decrypt(key, Arrays.copyOf(salt, CryptoBox.NONCE_BYTES),
            header(salt), encryptedIndex);
        Map<String, Entry> entries = readEntries(plainIndex);
        return new SecurePack(bytes, entries, key);
      }
    } catch (IOException exception) {
      throw new CryptoException("Unable to read protected package: " + file, exception);
    }
  }

  /**
   * Reads and decrypts the package index into an entry lookup table.
   *
   * @param plainIndex authenticated, decrypted index bytes
   * @return entries keyed by their logical resource paths
   * @throws IOException if the index is truncated or cannot be decoded
   */
  private static Map<String, Entry> readEntries(byte[] plainIndex) throws IOException {
    Map<String, Entry> result = new LinkedHashMap<>();
    try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(plainIndex))) {
      int count = stream.readInt();
      for (int i = 0; i < count; i++) {
        int nameLength = stream.readInt();
        byte[] nameBytes = stream.readNBytes(nameLength);
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        byte[] nonce = stream.readNBytes(CryptoBox.NONCE_BYTES);
        int encryptedLength = stream.readInt();
        byte[] encrypted = stream.readNBytes(encryptedLength);
        result.put(name, new Entry(nameBytes, nonce, encrypted));
      }
    }
    return result;
  }

  /**
   * Builds the package metadata authenticated with the encrypted index.
   *
   * @param salt package key-derivation salt
   * @return serialized package metadata
   * @throws CryptoException if the metadata cannot be serialized
   */
  private static byte[] header(byte[] salt) {
    ByteArrayOutputStream output = new ByteArrayOutputStream(8 + salt.length);
    try (DataOutputStream stream = new DataOutputStream(output)) {
      stream.writeInt(MAGIC);
      stream.writeInt(VERSION);
      stream.write(salt);
    } catch (IOException exception) {
      throw new CryptoException("Unable to build package authentication data", exception);
    }
    return output.toByteArray();
  }

  /**
   * Reads and decrypts a resource by its logical path.
   *
   * @param path logical resource path stored in the package
   * @return decrypted resource bytes, or {@code null} if the path is absent
   * @throws CryptoException if the resource fails authentication or decryption
   */
  public java.util.stream.Stream<String> walk(String directory) {
    String prefix = directory.isEmpty() ? "" : directory + "/";
    return entries.keySet().stream().filter(path -> path.startsWith(prefix));
  }

  public byte @Nullable [] read(String path) {
    Entry entry = entries.get(path);
    if (entry == null) {
      return null;
    }
    return CryptoBox.decrypt(key, entry.nonce, entry.nameBytes, entry.ciphertext);
  }

  /**
   * Opens a resource as an input stream without exposing the package's encrypted storage.
   *
   * @param path logical resource path stored in the package
   * @return a stream over decrypted bytes, or {@code null} if the path is absent
   * @throws CryptoException if the resource fails authentication or decryption
   */
  public @Nullable InputStream stream(String path) {
    byte[] data = read(path);
    return data == null ? null : new ByteArrayInputStream(data);
  }

  /**
   * Clears decrypted index data, entry payloads, and the owned key copy.
   */
  @Override
  public void close() {
    Arrays.fill(packBytes, (byte) 0);
    Arrays.fill(key, (byte) 0);
    for (Entry entry : entries.values()) {
      entry.clear();
    }
    entries.clear();
  }

  private record Entry(byte[] nameBytes, byte[] nonce, byte[] ciphertext) {
    /** Clears the sensitive entry metadata and ciphertext after the pack closes. */
    private void clear() {
      Arrays.fill(nameBytes, (byte) 0);
      Arrays.fill(nonce, (byte) 0);
      Arrays.fill(ciphertext, (byte) 0);
    }
  }
}
