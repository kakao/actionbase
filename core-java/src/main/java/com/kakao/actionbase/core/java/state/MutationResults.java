package com.kakao.actionbase.core.java.state;

import com.kakao.actionbase.core.java.payload.cdc.CDCListPayload;

import java.util.List;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableMutationResults.class)
@JsonDeserialize(as = ImmutableMutationResults.class)
public interface MutationResults {

  @Value.Parameter
  List<String> results();

  /**
   *
   *
   * <pre>
   *  | beforeActive | afterActive | State    |
   *  |--------------|-------------|----------|
   *  | false        | true        | CREATED  |
   *  | false        | false       | IDLE     |
   *  | true         | false       | DELETED  |
   *  | true         | true        | UPDATED  |
   *  </pre>
   */
  static MutationResults from(CDCListPayload<?, ?> cdcList) {
    List<String> results =
        cdcList.items().stream()
            .map(
                cdc -> {
                  boolean beforeActive = cdc.before().active();
                  boolean afterActive = cdc.after().active();

                  // Calculate state
                  if (!beforeActive && afterActive) {
                    return "CREATED";
                  } else if (!beforeActive) {
                    return "IDLE";
                  } else if (!afterActive) {
                    return "DELETED";
                  } else {
                    return "UPDATED";
                  }
                })
            .collect(Collectors.toList()); // Can collect results into list, etc. and return

    return ImmutableMutationResults.of(results);
  }
}
