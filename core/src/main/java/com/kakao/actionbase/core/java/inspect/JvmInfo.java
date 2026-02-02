package com.kakao.actionbase.core.java.inspect;

public class JvmInfo {

  private final String runtimeVersion;

  private final String vmVersion;

  private final String javaVersion;

  private final int classMajorVersion;

  private final String classVersionName;

  public JvmInfo(
      String runtimeVersion, String vmVersion, String javaVersion, int classMajorVersion) {
    this.runtimeVersion = runtimeVersion;
    this.vmVersion = vmVersion;
    this.javaVersion = javaVersion;
    this.classMajorVersion = classMajorVersion;
    this.classVersionName = classVersionToJava(classMajorVersion);
  }

  public String getRuntimeVersion() {
    return runtimeVersion;
  }

  public String getVmVersion() {
    return vmVersion;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public int getClassMajorVersion() {
    return classMajorVersion;
  }

  public String getClassVersionName() {
    return classVersionName;
  }

  public boolean isJava8() {
    return classMajorVersion == 52;
  }

  public boolean isJava17() {
    return classMajorVersion == 61;
  }

  public boolean isJava21() {
    return classMajorVersion == 65;
  }

  @Override
  public String toString() {
    return "JvmInfo{"
        + "runtimeVersion='"
        + runtimeVersion
        + '\''
        + ", vmVersion='"
        + vmVersion
        + '\''
        + ", javaVersion='"
        + javaVersion
        + '\''
        + ", classMajorVersion="
        + classMajorVersion
        + ", classVersionName='"
        + classVersionName
        + '\''
        + '}';
  }

  public static String classVersionToJava(int major) {
    switch (major) {
      case 52:
        return "Java 8";
      case 53:
        return "Java 9";
      case 54:
        return "Java 10";
      case 55:
        return "Java 11";
      case 56:
        return "Java 12";
      case 57:
        return "Java 13";
      case 58:
        return "Java 14";
      case 59:
        return "Java 15";
      case 60:
        return "Java 16";
      case 61:
        return "Java 17";
      case 62:
        return "Java 18";
      case 63:
        return "Java 19";
      case 64:
        return "Java 20";
      case 65:
        return "Java 21";
      default:
        return "Unknown";
    }
  }
}
