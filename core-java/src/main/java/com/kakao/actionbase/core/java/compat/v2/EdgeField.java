package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.constant.Constant;

public class EdgeField {

  private EdgeField() {}

  public static String convertV2ToV3FieldName(String fieldName) {
    return convertV2ToV3FieldName(fieldName, false);
  }

  public static String convertV2ToV3FieldName(String fieldName, boolean includeProperties) {
    switch (fieldName) {
      case Constant.EdgeField.VERSION_V2:
        return Constant.EdgeField.VERSION_V3;
      case Constant.EdgeField.SOURCE_V2:
        return Constant.EdgeField.SOURCE_V3;
      case Constant.EdgeField.TARGET_V2:
        return Constant.EdgeField.TARGET_V3;
      case Constant.EdgeField.PROPERTIES_V2:
        return includeProperties ? Constant.EdgeField.PROPERTIES_V3 : fieldName;
      default:
        return fieldName;
    }
  }
}
