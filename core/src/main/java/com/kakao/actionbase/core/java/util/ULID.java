/**
 * // from https://github.com/azam/ulidj/blob/main/src/main/java/io/azam/ulidj/ULID.java
 *
 * <p>MIT License
 *
 * <p>Copyright (c) 2016 Azamshul Azizy
 *
 * <p>Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * <p>The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * <p>THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.kakao.actionbase.core.java.util;

import java.util.Random;

/**
 * ULID string generator and parser class, using Crockford Base32 encoding. Only upper case letters
 * are used for generation. Parsing allows upper and lower case letters, and i and l will be treated
 * as 1 and o will be treated as 0. <br>
 * <br>
 * ULID generation examples:<br>
 *
 * <pre>
 * String ulid1 = ULID.random();
 * String ulid2 = ULID.random(ThreadLocalRandom.current());
 * String ulid3 = ULID.random(SecureRandom.newInstance("SHA1PRNG"));
 * byte[] entropy = new byte[] {0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9};
 * String ulid4 = ULID.generate(System.currentTimeMillis(), entropy);
 * </pre>
 *
 * <p>ULID parsing examples:<br>
 *
 * <pre>
 * String ulid = "003JZ9J6G80123456789abcdef";
 * assert ULID.isValid(ulid);
 * long ts = ULID.getTimestamp(ulid);
 * assert ts == 123456789000L;
 * byte[] entropy = ULID.getEntropy(ulid);
 * </pre>
 *
 * @author azam
 * @see <a href="http://www.crockford.com/wrmg/base32.html">Base32 Encoding</a>
 * @see <a href="https://github.com/ulid/spec">ULID</a>
 * @since 0.0.1
 */
public class ULID {
  /** ULID string length. */
  public static final int ULID_LENGTH = 26;

  /** ULID binary length. */
  public static final int ULID_BINARY_LENGTH = 16;

  /** ULID entropy byte length. */
  public static final int ENTROPY_LENGTH = 10;

  /** Minimum allowed timestamp value. */
  public static final long MIN_TIME = 0x0L;

  /**
   * Maximum allowed timestamp value. Encoded value can encode up to 0x0003ffffffffffffL but ULID
   * binary/byte representation states that timestamp will only be 48-bits.
   */
  public static final long MAX_TIME = 0x0000ffffffffffffL;

  /** Base32 characters mapping */
  private static final char[] C =
      new char[] { //
        0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, //
        0x38, 0x39, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, //
        0x47, 0x48, 0x4a, 0x4b, 0x4d, 0x4e, 0x50, 0x51, //
        0x52, 0x53, 0x54, 0x56, 0x57, 0x58, 0x59, 0x5a //
      };

  /** {@code char} to {@code byte} O(1) mapping with alternative chars mapping */
  private static final byte[] V =
      new byte[] { //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, //
        (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, //
        (byte) 0x08, (byte) 0x09, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, //
        (byte) 0x0d, (byte) 0x0e, (byte) 0x0f, (byte) 0x10, //
        (byte) 0x11, (byte) 0xff, (byte) 0x12, (byte) 0x13, //
        (byte) 0xff, (byte) 0x14, (byte) 0x15, (byte) 0xff, //
        (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, //
        (byte) 0x1a, (byte) 0xff, (byte) 0x1b, (byte) 0x1c, //
        (byte) 0x1d, (byte) 0x1e, (byte) 0x1f, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, //
        (byte) 0x0d, (byte) 0x0e, (byte) 0x0f, (byte) 0x10, //
        (byte) 0x11, (byte) 0xff, (byte) 0x12, (byte) 0x13, //
        (byte) 0xff, (byte) 0x14, (byte) 0x15, (byte) 0xff, //
        (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, //
        (byte) 0x1a, (byte) 0xff, (byte) 0x1b, (byte) 0x1c, //
        (byte) 0x1d, (byte) 0x1e, (byte) 0x1f, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, //
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff //
      };

  /**
   * Generate random ULID string using {@link Random} instance.
   *
   * @return ULID string
   */
  public static String random() {
    byte[] entropy = new byte[10];
    Random random = new Random();
    random.nextBytes(entropy);
    return generate(System.currentTimeMillis(), entropy);
  }

  /**
   * Generate random ULID binary using {@link Random} instance.
   *
   * @return ULID binary
   */
  public static byte[] randomBinary() {
    byte[] entropy = new byte[10];
    Random random = new Random();
    random.nextBytes(entropy);
    return generateBinary(System.currentTimeMillis(), entropy);
  }

  /**
   * Generate random ULID string using provided {@link Random} instance.
   *
   * @param random {@link Random} instance
   * @return ULID string
   */
  public static String random(Random random) {
    byte[] entropy = new byte[10];
    random.nextBytes(entropy);
    return generate(System.currentTimeMillis(), entropy);
  }

  /**
   * Generate random ULID binary using provided {@link Random} instance.
   *
   * @param random {@link Random} instance
   * @return ULID string
   */
  public static byte[] randomBinary(Random random) {
    byte[] entropy = new byte[10];
    random.nextBytes(entropy);
    return generateBinary(System.currentTimeMillis(), entropy);
  }

  /**
   * Generate ULID string from Unix epoch timestamp in millisecond and entropy bytes. Throws {@link
   * IllegalArgumentException} if timestamp is less than {@value #MIN_TIME}, is more than {@value
   * #MAX_TIME}, or entropy bytes is null or less than 10 bytes.
   *
   * @param time Unix epoch timestamp in millisecond
   * @param entropy Entropy bytes
   * @return ULID string
   */
  public static String generate(long time, byte[] entropy) {
    if (time < MIN_TIME || time > MAX_TIME || entropy == null || entropy.length < ENTROPY_LENGTH) {
      throw new IllegalArgumentException(
          "Time is too long, or entropy is less than 10 bytes or null");
    }

    char[] chars = new char[26];

    // time
    chars[0] = C[((byte) (time >>> 45)) & 0x1f];
    chars[1] = C[((byte) (time >>> 40)) & 0x1f];
    chars[2] = C[((byte) (time >>> 35)) & 0x1f];
    chars[3] = C[((byte) (time >>> 30)) & 0x1f];
    chars[4] = C[((byte) (time >>> 25)) & 0x1f];
    chars[5] = C[((byte) (time >>> 20)) & 0x1f];
    chars[6] = C[((byte) (time >>> 15)) & 0x1f];
    chars[7] = C[((byte) (time >>> 10)) & 0x1f];
    chars[8] = C[((byte) (time >>> 5)) & 0x1f];
    chars[9] = C[((byte) (time)) & 0x1f];

    // entropy
    chars[10] = C[(byte) ((entropy[0] & 0xff) >>> 3)];
    chars[11] = C[(byte) (((entropy[0] << 2) | ((entropy[1] & 0xff) >>> 6)) & 0x1f)];
    chars[12] = C[(byte) (((entropy[1] & 0xff) >>> 1) & 0x1f)];
    chars[13] = C[(byte) (((entropy[1] << 4) | ((entropy[2] & 0xff) >>> 4)) & 0x1f)];
    chars[14] = C[(byte) (((entropy[2] << 1) | ((entropy[3] & 0xff) >>> 7)) & 0x1f)];
    chars[15] = C[(byte) (((entropy[3] & 0xff) >>> 2) & 0x1f)];
    chars[16] = C[(byte) (((entropy[3] << 3) | ((entropy[4] & 0xff) >>> 5)) & 0x1f)];
    chars[17] = C[(byte) (entropy[4] & 0x1f)];
    chars[18] = C[(byte) ((entropy[5] & 0xff) >>> 3)];
    chars[19] = C[(byte) (((entropy[5] << 2) | ((entropy[6] & 0xff) >>> 6)) & 0x1f)];
    chars[20] = C[(byte) (((entropy[6] & 0xff) >>> 1) & 0x1f)];
    chars[21] = C[(byte) (((entropy[6] << 4) | ((entropy[7] & 0xff) >>> 4)) & 0x1f)];
    chars[22] = C[(byte) (((entropy[7] << 1) | ((entropy[8] & 0xff) >>> 7)) & 0x1f)];
    chars[23] = C[(byte) (((entropy[8] & 0xff) >>> 2) & 0x1f)];
    chars[24] = C[(byte) (((entropy[8] << 3) | ((entropy[9] & 0xff) >>> 5)) & 0x1f)];
    chars[25] = C[(byte) (entropy[9] & 0x1f)];

    return new String(chars);
  }

  /**
   * Generate ULID binary from Unix epoch timestamp in millisecond and entropy bytes. Throws {@link
   * IllegalArgumentException} if timestamp is less than {@value #MIN_TIME}, is more than {@value
   * #MAX_TIME}, or entropy bytes is null or less than 10 bytes.
   *
   * @param time Unix epoch timestamp in millisecond
   * @param entropy Entropy bytes
   * @return ULID string
   */
  public static byte[] generateBinary(long time, byte[] entropy) {
    if (time < MIN_TIME || time > MAX_TIME || entropy == null || entropy.length < ENTROPY_LENGTH) {
      throw new IllegalArgumentException(
          "Time is too long, or entropy is less than 10 bytes or null");
    }

    byte[] bytes = new byte[ULID.ULID_BINARY_LENGTH];

    // Long to big endian byte array up to 6 bytes
    long ts = time;
    for (int i = 5; i >= 0; i--) {
      bytes[i] = (byte) (ts & 0xff);
      ts = ts >>> 8;
    }

    // Copy over bytes from entropy
    System.arraycopy(entropy, 0, bytes, 6, 10);

    return bytes;
  }

  /**
   * Checks ULID string validity.
   *
   * @param ulid ULID string
   * @return true if ULID string is valid
   */
  public static boolean isValid(CharSequence ulid) {
    if (ulid == null || ulid.length() != ULID_LENGTH) {
      return false;
    }
    for (int i = 0; i < ULID_LENGTH; i++) {
      /** We only care for chars between 0x00 and 0xff. */
      char c = ulid.charAt(i);
      if (c < 0 || c > V.length || V[c] == (byte) 0xff) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks ULID binary validity.
   *
   * @param ulid ULID binary
   * @return true if ULID binary is valid
   */
  public static boolean isValidBinary(byte[] ulid) {
    return ulid != null && ulid.length == ULID_BINARY_LENGTH;
  }

  /**
   * Extract and return the timestamp part from ULID string. Expects a valid ULID string. Call
   * {@link io.azam.ulidj.ULID#isValid(CharSequence)} and check validity before calling this method
   * if you do not trust the origin of the ULID string.
   *
   * @param ulid ULID string
   * @return Unix epoch timestamp in millisecond
   */
  public static long getTimestamp(CharSequence ulid) {
    return (long) V[ulid.charAt(0)] << 45 //
        | (long) V[ulid.charAt(1)] << 40 //
        | (long) V[ulid.charAt(2)] << 35 //
        | (long) V[ulid.charAt(3)] << 30 //
        | (long) V[ulid.charAt(4)] << 25 //
        | (long) V[ulid.charAt(5)] << 20 //
        | (long) V[ulid.charAt(6)] << 15 //
        | (long) V[ulid.charAt(7)] << 10 //
        | (long) V[ulid.charAt(8)] << 5 //
        | (long) V[ulid.charAt(9)];
  }

  /**
   * Extract and return the timestamp part from ULID binary. Expects a valid ULID binary. Call
   * {@link io.azam.ulidj.ULID#isValidBinary(byte[])} and check validity before calling this method
   * if you do not trust the origin of the ULID string.
   *
   * @param ulid ULID string
   * @return Unix epoch timestamp in millisecond
   */
  public static long getTimestampBinary(byte[] ulid) {
    long timestamp = ulid[0];
    timestamp = (timestamp << 8) | ulid[1] & 0xff;
    timestamp = (timestamp << 8) | ulid[2] & 0xff;
    timestamp = (timestamp << 8) | ulid[3] & 0xff;
    timestamp = (timestamp << 8) | ulid[4] & 0xff;
    timestamp = (timestamp << 8) | ulid[5] & 0xff;
    return timestamp;
  }

  /**
   * Extract and return the entropy part from ULID string. Expects a valid ULID string. Call {@link
   * io.azam.ulidj.ULID#isValid(CharSequence)} and check validity before calling this method if you
   * do not trust the origin of the ULID string.
   *
   * @param ulid ULID string
   * @return Entropy bytes
   */
  public static byte[] getEntropy(CharSequence ulid) {
    byte[] bytes = new byte[ENTROPY_LENGTH];
    bytes[0] =
        (byte)
            ((V[ulid.charAt(10)] << 3) //
                | (V[ulid.charAt(11)] & 0xff) >>> 2);
    bytes[1] =
        (byte)
            ((V[ulid.charAt(11)] << 6) //
                | V[ulid.charAt(12)] << 1 //
                | (V[ulid.charAt(13)] & 0xff) >>> 4);
    bytes[2] =
        (byte)
            ((V[ulid.charAt(13)] << 4) //
                | (V[ulid.charAt(14)] & 0xff) >>> 1);
    bytes[3] =
        (byte)
            ((V[ulid.charAt(14)] << 7) //
                | V[ulid.charAt(15)] << 2 //
                | (V[ulid.charAt(16)] & 0xff) >>> 3);
    bytes[4] =
        (byte)
            ((V[ulid.charAt(16)] << 5) //
                | V[ulid.charAt(17)]);
    bytes[5] =
        (byte)
            ((V[ulid.charAt(18)] << 3) //
                | (V[ulid.charAt(19)] & 0xff) >>> 2);
    bytes[6] =
        (byte)
            ((V[ulid.charAt(19)] << 6) //
                | V[ulid.charAt(20)] << 1 //
                | (V[ulid.charAt(21)] & 0xff) >>> 4);
    bytes[7] =
        (byte)
            ((V[ulid.charAt(21)] << 4) //
                | (V[ulid.charAt(22)] & 0xff) >>> 1);
    bytes[8] =
        (byte)
            ((V[ulid.charAt(22)] << 7) //
                | V[ulid.charAt(23)] << 2 //
                | (V[ulid.charAt(24)] & 0xff) >>> 3);
    bytes[9] =
        (byte)
            ((V[ulid.charAt(24)] << 5) //
                | V[ulid.charAt(25)]);
    return bytes;
  }

  /**
   * Extract and return the entropy part from ULID binary. Expects a valid ULID binary. Call {@link
   * io.azam.ulidj.ULID#isValidBinary(byte[])} and check validity before calling this method if you
   * do not trust the origin of the ULID string.
   *
   * @param ulid ULID binary
   * @return Entropy bytes
   */
  public static byte[] getEntropyBinary(byte[] ulid) {
    byte[] bytes = new byte[ENTROPY_LENGTH];
    System.arraycopy(ulid, 6, bytes, 0, 10);
    return bytes;
  }

  /**
   * Convert a valid ULID string to it's binary representation. Call {@link
   * io.azam.ulidj.ULID#isValid(CharSequence)} and check validity before calling this method if you
   * do not trust the origin of the ULID string.<br>
   * <br>
   * Binary layout:
   *
   * <pre>
   * 0                   1                   2                   3
   *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                      32_bit_uint_time_high                    |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |     16_bit_uint_time_low      |       16_bit_uint_random      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                       32_bit_uint_random                      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                       32_bit_uint_random                      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * </pre>
   *
   * @param ulid ULID string
   * @return ULID binary
   */
  public static byte[] toBinary(CharSequence ulid) {
    byte[] bytes = new byte[ULID_BINARY_LENGTH];
    // Timestamp
    bytes[0] =
        (byte)
            ((V[ulid.charAt(0)] << 5) //
                | V[ulid.charAt(1)]);
    bytes[1] =
        (byte)
            ((V[ulid.charAt(2)] << 3) //
                | (V[ulid.charAt(3)] & 0xff) >>> 2);
    bytes[2] =
        (byte)
            ((V[ulid.charAt(3)] << 6) //
                | V[ulid.charAt(4)] << 1 //
                | (V[ulid.charAt(5)] & 0xff) >>> 4);
    bytes[3] =
        (byte)
            ((V[ulid.charAt(5)] << 4) //
                | (V[ulid.charAt(6)] & 0xff) >>> 1);
    bytes[4] =
        (byte)
            ((V[ulid.charAt(6)] << 7) //
                | V[ulid.charAt(7)] << 2 //
                | (V[ulid.charAt(8)] & 0xff) >>> 3);
    bytes[5] =
        (byte)
            ((V[ulid.charAt(8)] << 5) //
                | V[ulid.charAt(9)]);
    // Entropy
    bytes[6] =
        (byte)
            ((V[ulid.charAt(10)] << 3) //
                | (V[ulid.charAt(11)] & 0xff) >>> 2);
    bytes[7] =
        (byte)
            ((V[ulid.charAt(11)] << 6) //
                | V[ulid.charAt(12)] << 1 //
                | (V[ulid.charAt(13)] & 0xff) >>> 4);
    bytes[8] =
        (byte)
            ((V[ulid.charAt(13)] << 4) //
                | (V[ulid.charAt(14)] & 0xff) >>> 1);
    bytes[9] =
        (byte)
            ((V[ulid.charAt(14)] << 7) //
                | V[ulid.charAt(15)] << 2 //
                | (V[ulid.charAt(16)] & 0xff) >>> 3);
    bytes[10] =
        (byte)
            ((V[ulid.charAt(16)] << 5) //
                | V[ulid.charAt(17)]);
    bytes[11] =
        (byte)
            ((V[ulid.charAt(18)] << 3) //
                | (V[ulid.charAt(19)] & 0xff) >>> 2);
    bytes[12] =
        (byte)
            ((V[ulid.charAt(19)] << 6) //
                | V[ulid.charAt(20)] << 1 //
                | (V[ulid.charAt(21)] & 0xff) >>> 4);
    bytes[13] =
        (byte)
            ((V[ulid.charAt(21)] << 4) //
                | (V[ulid.charAt(22)] & 0xff) >>> 1);
    bytes[14] =
        (byte)
            ((V[ulid.charAt(22)] << 7) //
                | V[ulid.charAt(23)] << 2 //
                | (V[ulid.charAt(24)] & 0xff) >>> 3);
    bytes[15] =
        (byte)
            ((V[ulid.charAt(24)] << 5) //
                | V[ulid.charAt(25)]);
    return bytes;
  }

  /**
   * Convert a valid ULID binary representation to it's string representation. Call {@link
   * io.azam.ulidj.ULID#isValidBinary(byte[])} and check validity before calling this method if you
   * do not trust the origin of the ULID string.<br>
   * <br>
   * Binary layout:
   *
   * <pre>
   * 0                   1                   2                   3
   *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                      32_bit_uint_time_high                    |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |     16_bit_uint_time_low      |       16_bit_uint_random      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                       32_bit_uint_random                      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                       32_bit_uint_random                      |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * </pre>
   *
   * @param binary ULID binary
   * @return ULID string
   */
  public static String fromBinary(byte[] binary) {
    char[] chars = new char[26];

    // time
    chars[0] = C[(byte) (((binary[0] & 0xff) >>> 5) & 0x1f)];
    chars[1] = C[(byte) (binary[0] & 0x1f)];
    chars[2] = C[(byte) ((binary[1] & 0xff) >>> 3)];
    chars[3] = C[(byte) (((binary[1] << 2) | ((binary[2] & 0xff) >>> 6)) & 0x1f)];
    chars[4] = C[(byte) (((binary[2] & 0xff) >>> 1) & 0x1f)];
    chars[5] = C[(byte) (((binary[2] << 4) | ((binary[3] & 0xff) >>> 4)) & 0x1f)];
    chars[6] = C[(byte) (((binary[3] << 1) | ((binary[4] & 0xff) >>> 7)) & 0x1f)];
    chars[7] = C[(byte) (((binary[4] & 0xff) >>> 2) & 0x1f)];
    chars[8] = C[(byte) (((binary[4] << 3) | ((binary[5] & 0xff) >>> 5)) & 0x1f)];
    chars[9] = C[(byte) (binary[5] & 0x1f)];

    // entropy
    chars[10] = C[(byte) ((binary[6] & 0xff) >>> 3)];
    chars[11] = C[(byte) (((binary[6] << 2) | ((binary[7] & 0xff) >>> 6)) & 0x1f)];
    chars[12] = C[(byte) (((binary[7] & 0xff) >>> 1) & 0x1f)];
    chars[13] = C[(byte) (((binary[7] << 4) | ((binary[8] & 0xff) >>> 4)) & 0x1f)];
    chars[14] = C[(byte) (((binary[8] << 1) | ((binary[9] & 0xff) >>> 7)) & 0x1f)];
    chars[15] = C[(byte) (((binary[9] & 0xff) >>> 2) & 0x1f)];
    chars[16] = C[(byte) (((binary[9] << 3) | ((binary[10] & 0xff) >>> 5)) & 0x1f)];
    chars[17] = C[(byte) (binary[10] & 0x1f)];
    chars[18] = C[(byte) ((binary[11] & 0xff) >>> 3)];
    chars[19] = C[(byte) (((binary[11] << 2) | ((binary[12] & 0xff) >>> 6)) & 0x1f)];
    chars[20] = C[(byte) (((binary[12] & 0xff) >>> 1) & 0x1f)];
    chars[21] = C[(byte) (((binary[12] << 4) | ((binary[13] & 0xff) >>> 4)) & 0x1f)];
    chars[22] = C[(byte) (((binary[13] << 1) | ((binary[14] & 0xff) >>> 7)) & 0x1f)];
    chars[23] = C[(byte) (((binary[14] & 0xff) >>> 2) & 0x1f)];
    chars[24] = C[(byte) (((binary[14] << 3) | ((binary[15] & 0xff) >>> 5)) & 0x1f)];
    chars[25] = C[(byte) (binary[15] & 0x1f)];

    return new String(chars);
  }
}
