package actionbase.core

import actionbase.core.model.ActionbaseObjectMapper
import actionbase.pipeline.adapter.HttpClient
import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

/**
  * HTTP client for the V1 admin metadata endpoints:
  *  - four `getMetadata*` fetches against `/graph/v2/admin/metadata/{service,storage,...}`
  *  - `isSynced` counts hosts whose entity list reports status=="sync"
  *  - `hostNum == syncNum` is the success condition
  *
  * The in-house `APIHelper` trait mix-in is dropped (unused by the
  * sync-polling algorithms) and JSON deserialisation uses the same Jackson
  * mapper used by [[AbServiceImpl]].
  *
  * @param graphUrl     Base URL of the ActionBase admin API.
  * @param graphAuthKey Authorization header value.
  * @param httpClient   HTTP transport (external consumer supplies the concrete impl).
  *
  * @note Slimmed in OSS port: LazyEval / APIHelper trait mix-in removed, JSON uses the shared Jackson mapper.
  */
class AbAdminServiceImpl(
    val graphUrl: String,
    val graphAuthKey: String,
    val httpClient: HttpClient
) extends AbAdminService with StrictLogging {

  private val baseUrl = s"$graphUrl/graph/v2/admin/metadata"
  private val headers = Map("Authorization" -> graphAuthKey)

  private def get(path: String): Try[AbAdminService.Response] = {
    val response = httpClient.get(s"$baseUrl/$path", headers)
    Try(ActionbaseObjectMapper.scala.readValue(response.body, classOf[AbAdminService.Response]))
  }

  override def getMetadataService: Try[AbAdminService.Response]                    = get("service")
  override def getMetadataStorage: Try[AbAdminService.Response]                    = get("storage")
  override def getMetadataServiceLabel(service: String): Try[AbAdminService.Response] = get(s"service/$service/label")
  override def getMetadataServiceAlias(service: String): Try[AbAdminService.Response] = get(s"service/$service/alias")

  private def isSynced(getData: => Try[AbAdminService.Response], entityName: String): Boolean =
    getData
      .map { response =>
        val hostNum = response.data.size
        val syncNum = response.data.count { hostData =>
          hostData.entities.exists { entity =>
            entity.name == entityName && entity.status.equalsIgnoreCase("sync")
          }
        }
        logger.debug(s"hostNum: $hostNum, syncNum: $syncNum")
        hostNum == syncNum
      }
      .getOrElse(false)

  override def isLabelSynced(service: String, labelName: String): Boolean =
    isSynced(getMetadataServiceLabel(service), s"$service.$labelName")

  override def isStorageSynced(storageName: String): Boolean =
    isSynced(getMetadataStorage, s"origin.$storageName")

  override def isAliasSynced(service: String, aliasName: String): Boolean =
    isSynced(getMetadataServiceAlias(service), s"$service.$aliasName")
}
