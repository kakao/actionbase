package actionbase.core.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

/** Request body for the V3 edge mutation endpoint. */
@JsonInclude(Include.NON_ABSENT)
case class V3EdgeMutationRequest(
    mutations: Seq[V3EdgeMutation],
    audit: Option[AbAudit] = None
)
