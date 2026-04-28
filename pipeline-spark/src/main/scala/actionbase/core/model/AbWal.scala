package actionbase.core.model

import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation

import scala.util.Try

/**
  * Single write-ahead-log record for an ActionBase mutation. Used by the WAL
  * replayer algorithms to reconstruct edge mutations partition-local before
  * dispatching to the remote service.
  */
case class AbWal(
    ts: Long,
    phase: String,
    alias: Option[String],
    label: String,
    edge: TraceEdge,
    op: EdgeOperation,
    version: String,
    audit: Option[AbAudit],
    requestId: String,
    mode: Option[MutationResultContext] = None,
    tenant: Option[String] = None
)

/** Subset of [[AbWal]] used when only the routing prefix is required. */
case class AbWalPrefix(
    alias: Option[String],
    phase: String,
    label: String,
    tenant: Option[String],
    audit: Option[AbAudit]
)

/** Mutation result context — optional metadata attached to a WAL record. */
case class MutationResultContext(l: String, r: Option[String], queue: Boolean)

object AbWal {

  /**
    * Parse a single JSON WAL record. Returns `None` on parse failure to keep
    * the Spark flatMap pipeline alive when individual lines are malformed.
    *
    * Uses [[ActionbaseObjectMapper.scala]] so the WAL JSON contract
    * (Scala module + lenient unknown-property handling) matches the rest
    * of the pipeline.
    */
  def parse(json: String): Option[AbWal] =
    Try(ActionbaseObjectMapper.scala.readValue(json, classOf[AbWal])).toOption
}
