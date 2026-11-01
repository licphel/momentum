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

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

/**
 * Provides cryptographic primitives for creating and verifying protected package data.
 * Methods may be called concurrently; returned arrays are owned by the caller.
 */
public final class CryptoBox {
  /** Length in bytes of AES-256 keys produced by this class. */
  public static final int KEY_BYTES = 32;
  /** Length in bytes of an AES-GCM nonce. */
  public static final int NONCE_BYTES = 12;
  /** Length in bytes of package key-derivation salts. */
  public static final int SALT_BYTES = 16;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private CryptoBox() {
  }

  /**
   * Creates cryptographically secure random bytes.
   *
   * @param length number of bytes to create, greater than zero
   * @return a new array containing random bytes
   * @throws IllegalArgumentException if {@code length} is not positive
   */
  public static byte[] randomBytes(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("Random byte length must be positive: " + length);
    }
    byte[] result = new byte[length];
    RANDOM.nextBytes(result);
    return result;
  }

  /**
   * Creates a random key suitable for AES-256 operations.
   *
   * @return a new random key containing {@link #KEY_BYTES} bytes
   */
  public static byte[] randomKey() {
    return randomBytes(KEY_BYTES);
  }

  /**
   * Encrypts data with AES-GCM and authenticates optional associated data.
   *
   * @param key  AES-256 key
   * @param nonce unique nonce for this encryption under {@code key}
   * @param aad  additional data to authenticate without encrypting
   * @param plaintext data to encrypt
   * @return ciphertext including its authentication tag
   * @throws IllegalArgumentException if the key or nonce has an invalid length
   * @throws CryptoException if encryption is unavailable or fails
   */
  public static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
    return crypt(Cipher.ENCRYPT_MODE, key, nonce, aad, plaintext);
  }

  /**
   * Decrypts and authenticates AES-GCM ciphertext.
   *
   * @param key AES-256 key used for encryption
   * @param nonce nonce used for encryption
   * @param aad additional data supplied during encryption
   * @param ciphertext ciphertext including its authentication tag
   * @return authenticated plaintext
   * @throws IllegalArgumentException if the key or nonce has an invalid length
   * @throws CryptoException if decryption fails or authentication does not match
   */
  public static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
    return crypt(Cipher.DECRYPT_MODE, key, nonce, aad, ciphertext);
  }

  /**
   * Derives a package key from a parent key, salt, and application-defined context.
   *
   * @param parent parent key containing {@link #KEY_BYTES} bytes
   * @param salt derivation salt
   * @param info context that separates keys used for different purposes
   * @return a new derived key containing {@link #KEY_BYTES} bytes
   * @throws IllegalArgumentException if {@code parent} has an invalid length
   * @throws CryptoException if the required cryptographic provider is unavailable
   */
  public static byte[] deriveKey(byte[] parent, byte[] salt, String info) {
    requireKey(parent);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(parent, "HmacSHA256"));
      byte[] pseudoRandomKey = mac.doFinal(salt);
      mac.init(new SecretKeySpec(pseudoRandomKey, "HmacSHA256"));
      byte[] context = info.getBytes(StandardCharsets.UTF_8);
      byte[] input = Arrays.copyOf(context, context.length + 1);
      input[context.length] = 1;
      return Arrays.copyOf(mac.doFinal(input), KEY_BYTES);
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Unable to derive a package key", exception);
    }
  }

  /**
   * Computes the SHA-256 digest of the supplied data.
   *
   * @param value data to digest
   * @return a new 32-byte digest
   * @throws CryptoException if SHA-256 is unavailable
   */
  public static byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("SHA-256 is unavailable", exception);
    }
  }

  /**
   * Creates an Ed25519 key pair for signing protected package data.
   *
   * @return a generated signing key pair
   * @throws CryptoException if Ed25519 is unavailable
   */
  public static KeyPair generateSigningKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Ed25519 is unavailable", exception);
    }
  }

  /**
   * Signs data with an Ed25519 private key.
   *
   * @param key private signing key
   * @param value data to sign
   * @return the generated signature bytes
   * @throws CryptoException if signing fails or Ed25519 is unavailable
   */
  public static byte[] sign(PrivateKey key, byte[] value) {
    try {
      Signature signature = Signature.getInstance("Ed25519");
      signature.initSign(key);
      signature.update(value);
      return signature.sign();
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Unable to sign protected package data", exception);
    }
  }

  /**
   * Checks whether an Ed25519 signature matches the supplied data and public key.
   *
   * @param key public verification key
   * @param value signed data
   * @param signatureBytes signature to verify
   * @return {@code true} if the signature is valid; otherwise {@code false}
   * @throws CryptoException if signature verification cannot be performed
   */
  public static boolean verify(PublicKey key, byte[] value, byte[] signatureBytes) {
    try {
      Signature signature = Signature.getInstance("Ed25519");
      signature.initVerify(key);
      signature.update(value);
      return signature.verify(signatureBytes);
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Unable to verify protected package data", exception);
    }
  }

  /**
   * Ensures that a key has the required AES-256 length.
   *
   * @param key key to validate
   * @throws IllegalArgumentException if the key length is invalid
   */
  static void requireKey(byte[] key) {
    if (key.length != KEY_BYTES) {
      throw new IllegalArgumentException("AES-256 key must contain " + KEY_BYTES + " bytes");
    }
  }

  /**
   * Applies AES-GCM encryption or decryption to one value.
   *
   * @param mode JCA cipher operation mode
   * @param key AES-256 key
   * @param nonce operation nonce
   * @param aad additional data to authenticate
   * @param value plaintext or ciphertext, according to {@code mode}
   * @return encrypted or decrypted value
   * @throws IllegalArgumentException if the key or nonce has an invalid length
   * @throws CryptoException if the operation fails or ciphertext authentication fails
   */
  private static byte[] crypt(int mode, byte[] key, byte[] nonce, byte[] aad, byte[] value) {
    requireKey(key);
    if (nonce.length != NONCE_BYTES) {
      throw new IllegalArgumentException("AES-GCM nonce must contain " + NONCE_BYTES + " bytes");
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
      if (aad.length != 0) {
        cipher.updateAAD(aad);
      }
      return cipher.doFinal(value);
    } catch (GeneralSecurityException exception) {
      throw new CryptoException(mode == Cipher.ENCRYPT_MODE
          ? "Unable to encrypt protected package data"
          : "Protected package authentication failed", exception);
    }
  }
}
