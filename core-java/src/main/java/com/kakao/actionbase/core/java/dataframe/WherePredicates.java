package com.kakao.actionbase.core.java.dataframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WherePredicates {

  private WherePredicates() {}

  public static List<WherePredicate> parse(String filterString) {
    return Arrays.stream(filterString.split(";"))
        .map(
            it -> {
              String[] parts = it.split(":");
              String key = parts[0];
              String op = parts[1];
              String value = parts[2];

              switch (op) {
                case WherePredicate.IN:
                  List<Object> values = new ArrayList<>(Arrays.asList(value.split(",")));
                  return WherePredicate.in(key, values);
                case WherePredicate.EQ:
                  return WherePredicate.eq(key, value);
                case WherePredicate.GT:
                  return WherePredicate.gt(key, value);
                case WherePredicate.GTE:
                  return WherePredicate.gte(key, value);
                case WherePredicate.LT:
                  return WherePredicate.lt(key, value);
                case WherePredicate.LTE:
                  return WherePredicate.lte(key, value);
                case WherePredicate.BETWEEN:
                  String[] fromTo = value.split(",");
                  return WherePredicate.between(key, fromTo[0], fromTo[1]);
                case WherePredicate.IS_NULL:
                  return WherePredicate.isNull(key);
                default:
                  throw new IllegalArgumentException("Unsupported operator: " + op);
              }
            })
        .collect(Collectors.toList());
  }

  public static String toString(List<WherePredicate> predicates) {
    return predicates.stream()
        .map(
            predicate -> {
              String key = predicate.key();
              if (predicate instanceof WherePredicate.In) {
                WherePredicate.In inPredicate = (WherePredicate.In) predicate;
                String values =
                    inPredicate.values().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
                return key + ":" + WherePredicate.IN + ":" + values;
              } else if (predicate instanceof WherePredicate.Eq) {
                WherePredicate.Eq eqPredicate = (WherePredicate.Eq) predicate;
                return key + ":" + WherePredicate.EQ + ":" + eqPredicate.value();
              } else if (predicate instanceof WherePredicate.Gt) {
                WherePredicate.Gt gtPredicate = (WherePredicate.Gt) predicate;
                return key + ":" + WherePredicate.GT + ":" + gtPredicate.value();
              } else if (predicate instanceof WherePredicate.Gte) {
                WherePredicate.Gte gtePredicate = (WherePredicate.Gte) predicate;
                return key + ":" + WherePredicate.GTE + ":" + gtePredicate.value();
              } else if (predicate instanceof WherePredicate.Lt) {
                WherePredicate.Lt ltPredicate = (WherePredicate.Lt) predicate;
                return key + ":" + WherePredicate.LT + ":" + ltPredicate.value();
              } else if (predicate instanceof WherePredicate.Lte) {
                WherePredicate.Lte ltePredicate = (WherePredicate.Lte) predicate;
                return key + ":" + WherePredicate.LTE + ":" + ltePredicate.value();
              } else if (predicate instanceof WherePredicate.Between) {
                WherePredicate.Between betweenPredicate = (WherePredicate.Between) predicate;
                return key
                    + ":"
                    + WherePredicate.BETWEEN
                    + ":"
                    + betweenPredicate.fromValue()
                    + ","
                    + betweenPredicate.toValue();
              } else if (predicate instanceof WherePredicate.IsNull) {
                return key + ":" + WherePredicate.IS_NULL + ":null";
              } else {
                throw new IllegalArgumentException(
                    "Unsupported predicate type: " + predicate.getClass().getName());
              }
            })
        .collect(Collectors.joining(";"));
  }
}
