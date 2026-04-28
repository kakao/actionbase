package actionbase.core.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonInclude(Include.NON_ABSENT)
case class V3MultiEdgeMutationRequest(
    mutations: Seq[V3MultiEdgeMutation],
    audit: Option[AbAudit] = None
)
