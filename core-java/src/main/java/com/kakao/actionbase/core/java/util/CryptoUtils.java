package com.kakao.actionbase.core.java.util;

public class CryptoUtils {

  private static final byte[] KEY_BYTES = {
    90, 69, -31, -121, -53, -24, -34, -4, 55, 54, 1, -31, -43, -64, 116, -11
  };

  private static byte[] xorOperation(byte[] data, byte[] result) {
    assert data.length == result.length;
    for (int i = 0; i < data.length; i++) {
      result[i] = (byte) (data[i] ^ KEY_BYTES[i % KEY_BYTES.length]);
    }
    return result;
  }

  private static byte[] xorOperation(byte[] data) {
    return xorOperation(data, new byte[data.length]);
  }

  public static String encryptAndEncodeUrlSafe(byte[] data) {
    byte[] encryptedData = xorOperation(data);
    return SerializationUtils.base64.encode(encryptedData);
  }

  public static byte[] decodeAndDecryptUrlSafe(String encodedData) {
    byte[] data = SerializationUtils.base64.decode(encodedData);
    return xorOperation(data, data);
  }
}
