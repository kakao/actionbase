package com.kakao.actionbase.core.java.compat.v2;

import com.kakao.actionbase.core.java.edge.EdgeEvent;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.payload.cdc.CDCPayload;

import java.util.Objects;

public class StatusFromV3Cdc {
  private static final String CREATED = "CREATED";
  private static final String DELETED = "DELETED";
  private static final String UPDATED = "UPDATED";
  private static final String IDLE = "IDLE";

  private StatusFromV3Cdc() {}

  public static String deriveStatus(CDCPayload<EdgeEvent, EdgeState> cdc) {
    EdgeState cdcBefore = cdc.before();
    EdgeState cdcAfter = cdc.after();

    String statusFromActivity = deriveStatusFromActivity(cdcBefore.active(), cdc.after().active());

    // Change IDLE to UPDATED if the edge is actually updated and active
    if (cdcAfter.active() && Objects.equals(IDLE, statusFromActivity)) {
      if (cdcEquals(cdcBefore, cdcAfter)) {
        return IDLE;
      } else {
        return UPDATED;
      }
    }
    // Change UPDATED to IDLE if the edge is not actually updated
    if (Objects.equals(UPDATED, statusFromActivity)) {
      if (cdcEquals(cdcBefore, cdcAfter)) {
        return IDLE;
      } else {
        return UPDATED;
      }
    }
    return statusFromActivity;
  }

  private static String deriveStatusFromActivity(boolean before, boolean after) {
    if (!before && after) {
      return CREATED;
    } else if (before && !after) {
      return DELETED;
    } else if (before && after) {
      return UPDATED;
    } else {
      return IDLE;
    }
  }

  private static Boolean cdcEquals(EdgeState before, EdgeState after) {
    if (before == after) {
      return true;
    }
    return before.active() == after.active()
        && before.version() == after.version()
        && Objects.equals(before.properties(), after.properties());
  }
}
