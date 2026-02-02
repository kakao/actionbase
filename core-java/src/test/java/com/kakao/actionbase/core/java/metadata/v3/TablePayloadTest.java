package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.ImmutableTenantId;

import org.junit.jupiter.api.Test;

class TablePayloadTest {

  @Test
  void typeShouldReturnVertexType() {
    // given
    TableDescriptor<?> table = Databases.databaseTable(ImmutableTenantId.of("test-tenant"));

    System.out.println(ActionbaseObjectMapper.toJson(table));

    String jsonString =
        "{\"table\":\"database\",\"storage\":\"metadata\",\"comment\":\"sys.database table\",\"schema\":{\"type\":\"EDGE\",\"source\":{\"type\":{\"type\":\"string\"},\"comment\":\"tenant\"},\"target\":{\"type\":{\"type\":\"string\"},\"comment\":\"database\"},\"properties\":[{\"name\":\"comment\",\"type\":{\"type\":\"string\"},\"comment\":\"\",\"nullable\":false}],\"direction\":\"OUT\",\"indexes\":[{\"index\":\"default\",\"fields\":[{\"field\":\"target\",\"order\":\"ASC\"}],\"comment\":\"\"}],\"indexMap\":{\"default\":{\"index\":\"default\",\"fields\":[{\"field\":\"target\",\"order\":\"ASC\"}],\"comment\":\"\"}},\"propertyNames\":[\"comment\"]},\"mode\":\"SYNC\",\"type\":\"EDGE\"}";

    TablePayload<?> payload = ActionbaseObjectMapper.fromJson(jsonString, TablePayload.class);

    System.out.println(payload);
  }
}
