package actionbase.core

import scala.util.Try

/**
  * In-memory [[AbAdminService]] for tests. Every query returns an empty
  * [[AbAdminService.Response]] and every sync predicate returns `true`,
  * so callers that poll sync state resolve on the first iteration.
  */
class AbAdminServiceFake extends AbAdminService {
  override def getMetadataService: Try[AbAdminService.Response]                       = Try(AbAdminService.Response(List.empty))
  override def getMetadataStorage: Try[AbAdminService.Response]                       = Try(AbAdminService.Response(List.empty))
  override def getMetadataServiceLabel(service: String): Try[AbAdminService.Response] = Try(AbAdminService.Response(List.empty))
  override def getMetadataServiceAlias(service: String): Try[AbAdminService.Response] = Try(AbAdminService.Response(List.empty))
  override def isLabelSynced(service: String, labelName: String): Boolean             = true
  override def isStorageSynced(storageName: String): Boolean                          = true
  override def isAliasSynced(service: String, aliasName: String): Boolean             = true
}
