package actionbase.core

import scala.util.Try

/**
  * Admin-side contract used by [[ActionBaseSyncV1]] to poll whether a newly
  * created storage / label / alias has propagated across the ActionBase
  * control-plane hosts. Concrete implementations (HTTP poll loop) live in
  * the external consumer; an in-memory [[AbAdminServiceFake]] is available
  * for tests.
  */
trait AbAdminService extends Serializable {

  def getMetadataService: Try[AbAdminService.Response]
  def getMetadataStorage: Try[AbAdminService.Response]
  def getMetadataServiceLabel(service: String): Try[AbAdminService.Response]
  def getMetadataServiceAlias(service: String): Try[AbAdminService.Response]

  def isStorageSynced(storageName: String): Boolean
  def isLabelSynced(service: String, labelName: String): Boolean
  def isAliasSynced(service: String, aliasName: String): Boolean
}

object AbAdminService {
  case class Response(data: List[HostData])
  case class HostData(host: String, commitId: String, entities: List[Entity])
  case class Entity(name: String, status: String)
}
