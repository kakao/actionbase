package actionbase.core.model

/**
  * Response shape for the V1 admin endpoint that toggles a label's active flag.
  * `ActionBaseSyncV1.deactivateOldTable` relies on [[isUpdated]] to decide
  * whether to print success or diagnostic output.
  */
case class V2TableUpdateResponse(
    status: String,
    result: Option[V2TableInfo],
    message: Option[String] = None
) {
  private val UPDATED = "UPDATED"

  def isUpdated: Boolean = status == UPDATED
}

case class V2TableInfo(
    active: Boolean,
    name: String
)
