package com.kakao.actionbase.core.java.inspect;

import java.io.IOException;
import java.io.InputStream;

public class JvmRuntimeInspector {

  public static JvmInfo inspect(Class<?> clazz) throws IOException {
    String runtimeVersion = System.getProperty("java.runtime.version");
    String vmVersion = System.getProperty("java.vm.version");
    String javaVersion = System.getProperty("java.version");
    int classMajorVersion = -1;

    String className = clazz.getName().replace('.', '/') + ".class";
    try (InputStream in =
        JvmRuntimeInspector.class.getClassLoader().getResourceAsStream(className)) {
      if (in != null) {
        byte[] header = new byte[8]; // Enough for magic + minor + major
        if (in.read(header) == 8) {
          classMajorVersion = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        }
      }
    } catch (IOException ignored) {
    }
    return new JvmInfo(runtimeVersion, vmVersion, javaVersion, classMajorVersion);
  }

  public static JvmInfo inspectCore() throws IOException {
    return inspect(JvmRuntimeInspector.class);
  }

  public static void main(String[] args) throws IOException {
    System.out.println(inspect(JvmRuntimeInspector.class));
  }
}
