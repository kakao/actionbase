package com.kakao.actionbase.core.java.metadata.v3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Page metadata model. A container that holds ActionbaseModel, used for pagination processing. */
@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutablePage.class)
@JsonDeserialize(as = ImmutablePage.class)
public interface Page<T extends Descriptor<?>> {

  List<T> content();

  @Value.Derived
  default int count() {
    return content().size();
  }

  @JsonIgnore
  @Value.Derived
  default boolean isEmpty() {
    return content().isEmpty();
  }

  class PageTypeReferences {
    private static final Map<Class<?>, TypeReference<?>> MAP = new HashMap<>();

    static {
      MAP.put(DatabaseDescriptor.class, new TypeReference<Page<DatabaseDescriptor>>() {});
      MAP.put(TenantDescriptor.class, new TypeReference<Page<TenantDescriptor>>() {});
      MAP.put(StorageDescriptor.class, new TypeReference<Page<StorageDescriptor>>() {});
      MAP.put(AliasDescriptor.class, new TypeReference<Page<AliasDescriptor>>() {});
      MAP.put(TableDescriptor.class, new TypeReference<Page<TableDescriptor<?>>>() {});
    }

    @SuppressWarnings("unchecked")
    public static <T extends Descriptor<?>> TypeReference<Page<T>> get(Class<T> clazz) {
      return (TypeReference<Page<T>>) MAP.get(clazz);
    }
  }
}
