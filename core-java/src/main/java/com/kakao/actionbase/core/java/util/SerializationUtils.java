package com.kakao.actionbase.core.java.util;

import java.util.Base64;

public class SerializationUtils {

  public static final Serializer base64 = new Base64Serializer();

  public static final Serializer hex = new HexSerializer();

  public interface Serializer {
    String encode(byte[] data);

    byte[] decode(String data);
  }

  static class Base64Serializer implements Serializer {

    static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    static final Base64.Decoder base64Decoder = Base64.getUrlDecoder();

    @Override
    public String encode(byte[] data) {
      return base64Encoder.encodeToString(data);
    }

    @Override
    public byte[] decode(String data) {
      return base64Decoder.decode(data);
    }
  }

  static class HexSerializer implements Serializer {

    @Override
    public String encode(byte[] data) {
      StringBuilder sb = new StringBuilder();
      for (byte b : data) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    }

    @Override
    public byte[] decode(String data) {
      int len = data.length();
      byte[] byteArray = new byte[len / 2];
      for (int i = 0; i < len; i += 2) {
        byteArray[i / 2] =
            (byte)
                ((Character.digit(data.charAt(i), 16) << 4)
                    + Character.digit(data.charAt(i + 1), 16));
      }
      return byteArray;
    }
  }
}
