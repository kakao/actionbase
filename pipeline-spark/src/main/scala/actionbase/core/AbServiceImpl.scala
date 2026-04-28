package actionbase.core

import actionbase.core.AbService.{EdgeResponse, GetLabelByAliasResponse, StorageDTO}
import actionbase.core.AbServiceImpl.{CreateLabelResponse, CreateStorageResponse, HttpMethod, truncate}
import actionbase.core.exception.{InvalidHttpStatusCodeException, MutationResultErrorException}
import actionbase.core.model.AbResponse.MutationResult
import actionbase.core.model.{AbAudit, ActionbaseObjectMapper, V2TableUpdateResponse}
import actionbase.pipeline.adapter.{HttpClient, HttpResponse}
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.TypeFactory
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.{DirectionType, LabelDTO}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.{asJavaIterableConverter, iterableAsScalaIterableConverter}
import scala.util.{Failure, Success, Try}

/**
  * HTTP client for the V1 ActionBase endpoints. The implementation covers
  * the exact URL layout, header composition, and the
  * `Try(readValue[MutationResult])` error detection for
  * `insertEdge`/`updateEdge`/`deleteEdge`. JSON I/O shares the module-wide
  * [[ActionbaseObjectMapper.scala]] mapper (Jackson + Scala module,
  * lenient about unknown properties).
  *
  * @param abUrl     Base URL of the ActionBase control-plane API.
  * @param abAuthKey Authorization header value supplied with every request.
  * @param httpClient HTTP transport (external consumer supplies the concrete impl).
  * @param dmlUrl    Hook to override the V1 DML URL layout (kept for
  *                  binary compatibility with callers that rewrite URLs
  *                  before dispatch).
  *
  * @note Slimmed in OSS port: LazyEval removed, HttpClient replaced by an OSS adapter, JSON I/O uses the module-shared ActionbaseObjectMapper.
  */
class AbServiceImpl(
    val abUrl: String,
    val abAuthKey: String,
    val httpClient: HttpClient,
    val dmlUrl: (String, String, String) => String =
      (abUrl, serviceName, labelName) => s"$abUrl/graph/v2/service/$serviceName/label/$labelName/edge"
) extends AbService {

  private val scalaMapper                               = ActionbaseObjectMapper.scala
  private val typeCache: TrieMap[Manifest[_], JavaType] = TrieMap.empty

  private def getOrConstructType[T: Manifest]: JavaType = {
    val m = manifest[T]
    typeCache.getOrElseUpdate(
      m, {
        if (m.typeArguments.isEmpty) {
          TypeFactory.defaultInstance().constructType(m.runtimeClass)
        } else {
          val typeArgs = m.typeArguments.map(arg => getOrConstructType(arg))
          TypeFactory.defaultInstance().constructParametricType(m.runtimeClass, typeArgs: _*)
        }
      }
    )
  }

  def deserialize[T: Manifest](json: String): T =
    scalaMapper.readValue(json, getOrConstructType[T]).asInstanceOf[T]

  override def getLabelByAlias(serviceName: String, aliasName: String): GetLabelByAliasResponse = {
    val url      = s"$abUrl/graph/v2/service/$serviceName/alias/$aliasName"
    val response = httpClient.get(url, headers = Map("Authorization" -> abAuthKey))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to get label: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[GetLabelByAliasResponse])
  }

  override def getLabel(serviceName: String, labelName: String): LabelDTO = {
    val url      = s"$abUrl/graph/v2/service/$serviceName/label/$labelName"
    val response = httpClient.get(url, headers = Map("Authorization" -> abAuthKey))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to get label: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[LabelDTO])
  }

  override def createStorage(storageCreate: AbService.StorageCreate): AbService.StorageDTO = {
    val url     = s"$abUrl/graph/v2/storage/${storageCreate.name}"
    val payload = scalaMapper.writeValueAsString(storageCreate)
    val response = httpClient.post(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey),
      data = payload
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to create storage: POST $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[CreateStorageResponse]).result
  }

  override def createLabel(serviceName: String, labelName: String, label: LabelDTO): LabelDTO = {
    val url     = s"$abUrl/graph/v2/service/$serviceName/label/$labelName"
    val payload = scalaMapper.writeValueAsString(label)
    val response = httpClient.post(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey),
      data = payload
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to create label: POST $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[CreateLabelResponse]).result
  }

  override def updateLabelActive(
      serviceName: String,
      labelName: String,
      active: Boolean
  ): V2TableUpdateResponse = {
    val url     = s"$abUrl/graph/v2/service/$serviceName/label/$labelName"
    val payload = scalaMapper.writeValueAsString(Map("active" -> active))
    val response = httpClient.put(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey),
      data = payload
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to update label active: PUT $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[V2TableUpdateResponse])
  }

  override def updateAlias(serviceName: String, aliasName: String, fullLabelName: String): Unit = {
    val url     = s"$abUrl/graph/v2/service/$serviceName/alias/$aliasName"
    val payload = scalaMapper.writeValueAsString(Map("target" -> fullLabelName))
    val response = httpClient.put(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey),
      data = payload
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to update alias: PUT $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }
  }

  override def findSelfEdges[T: Manifest](
      serviceName: String,
      labelName: String,
      src: Any
  ): EdgeResponse[T] = {
    val url      = s"$abUrl/graph/v2/service/$serviceName/label/$labelName/edge?self=$src"
    val response = httpClient.get(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey)
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to find edge: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[EdgeResponse[T]])
  }

  override def findEdges[T](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T] = {
    val url    = s"$abUrl/graph/v2/service/$serviceName/label/$labelName/edge"
    val params = Seq(s"src=${src}", s"dir=${direction.name}") ++
      tgt.map(v => s"tgt=$v") ++
      index.map(v => s"index=$v") ++
      limit.map(v => s"limit=$v") ++
      offset.map(v => s"offset=$v") ++
      select.map(v => s"select=$v")
    val response = httpClient.get(
      s"$url?${params.mkString("&")}",
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey)
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to find edge: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[EdgeResponse[T]])
  }

  override def findEdgesWithType[T: Encoder: Decoder](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T] = {
    val url    = s"$abUrl/graph/v2/service/$serviceName/label/$labelName/edge"
    val params = Seq(s"src=${src}", s"dir=${direction.name}") ++
      tgt.map(v => s"tgt=$v") ++
      index.map(v => s"index=$v") ++
      limit.map(v => s"limit=$v") ++
      offset.map(v => s"offset=$v") ++
      select.map(v => s"select=$v")
    val response = httpClient.get(
      s"$url?${params.mkString("&")}",
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey)
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to find edge: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    decode[EdgeResponse[T]](response.body) match {
      case Right(v) => v
      case Left(_)  => EdgeResponse(Seq.empty, 0, Seq.empty, Some(""), hasNext = false)
    }
  }

  override def findPaginatedEdgesWithType[T: Encoder: Decoder](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      fetchSize: Int = 10,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T] = {
    @tailrec
    def fetchRecursive(
        accumulatedEdges: Seq[T],
        currentOffset: Option[String],
        remainingLimit: Option[Int]
    ): EdgeResponse[T] = {
      val currentFetchSize = remainingLimit.map(rem => Math.min(rem, fetchSize)).getOrElse(fetchSize)

      val currentPageResponse = findEdgesWithType[T](
        serviceName = serviceName,
        labelName = labelName,
        src = src,
        tgt = tgt,
        direction = direction,
        index = index,
        limit = Some(currentFetchSize),
        offset = currentOffset,
        select = select
      )

      val newAccumulatedEdges = accumulatedEdges ++ currentPageResponse.data
      val newRemainingLimit   = remainingLimit.map(_ - currentPageResponse.data.length).filter(_ > 0)

      if (currentPageResponse.hasNext && newRemainingLimit.isDefined) {
        fetchRecursive(newAccumulatedEdges, currentPageResponse.offset, newRemainingLimit)
      } else {
        EdgeResponse(
          data = newAccumulatedEdges,
          rows = newAccumulatedEdges.length,
          stats = currentPageResponse.stats,
          offset = currentPageResponse.offset,
          hasNext = currentPageResponse.hasNext && newRemainingLimit.isEmpty
        )
      }
    }

    fetchRecursive(Seq.empty[T], offset, limit)
  }

  private def mutateEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit],
      requestMethod: (String, String, Map[String, String]) => HttpResponse,
      httpMethod: String
  ): Unit = {
    val url = dmlUrl(abUrl, serviceName, labelName)
    val payload = scalaMapper.writeValueAsString(
      Map("edges" -> edges) ++ (if (audit.isEmpty) Map.empty else Map("audit" -> audit))
    )
    val headers  = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey)
    val response = requestMethod(url, payload, headers)
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to mutate edge: $httpMethod $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }

    Try {
      scalaMapper.readValue(response.body, classOf[MutationResult])
    } match {
      case Success(mutationResult) =>
        val errorElement = mutationResult.result.find(_.status == "ERROR")
        if (errorElement.nonEmpty)
          throw new MutationResultErrorException(
            s"Failed to mutate edge: $httpMethod $url " +
              s"payloadBytes=${payload.length} body=${truncate(response.body)}"
          )
      case Failure(ex) => throw ex
    }
  }

  override def insertEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit = mutateEdge(serviceName, labelName, edges, audit, httpClient.post, HttpMethod.POST)

  override def updateEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit = mutateEdge(serviceName, labelName, edges, audit, httpClient.put, HttpMethod.PUT)

  override def deleteEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit = mutateEdge(serviceName, labelName, edges, audit, httpClient.delete, HttpMethod.DELETE)

  override def getStorage(storageName: String): StorageDTO = {
    val url      = s"$abUrl/graph/v2/storage/$storageName"
    val response = httpClient.get(url, headers = Map("Authorization" -> abAuthKey))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to get storage: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }
    scalaMapper.readValue(response.body, classOf[StorageDTO])
  }

  override def updateStorage(storage: StorageDTO): Unit = {
    val body = scalaMapper.writeValueAsString(storage)
    val url  = s"$abUrl/graph/v2/storage/${storage.name}"
    val response = httpClient.put(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey),
      data = body
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to update storage: PUT $url status=${response.status} " +
          s"payloadBytes=${body.length} body=${truncate(response.body)}"
      )
    }
  }

  override def deleteLabel(labelDTO: LabelDTO): Unit = {
    val Array(service, name) = labelDTO.getName.split("\\.")
    val url                  = s"$abUrl/graph/v2/admin/service/$service/label/$name"
    val response             = httpClient.delete(url, headers = Map("Authorization" -> abAuthKey))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to delete label: DELETE $url status=${response.status} body=${truncate(response.body)}"
      )
    }
  }

  override def deleteStorage(storageName: String): Unit = {
    val url      = s"$abUrl/graph/v2/admin/storage/$storageName"
    val response = httpClient.delete(url, headers = Map("Authorization" -> abAuthKey))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to delete storage: DELETE $url status=${response.status} body=${truncate(response.body)}"
      )
    }
  }

  override def listLabel(serviceName: String): Seq[LabelDTO] = {
    val url = s"$abUrl/graph/v2/service/$serviceName/label"
    val response = httpClient.get(
      url,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey)
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to list label: GET $url status=${response.status} body=${truncate(response.body)}"
      )
    }

    val jsonNode = scalaMapper.readValue(response.body, classOf[ObjectNode])
    jsonNode.get("content") match {
      case contentArray: ArrayNode =>
        val filteredContent = contentArray.asScala.filter { item =>
          val typeValue = item.get("type").asText()
          typeValue != "MULTI_EDGE"
        }
        scalaMapper.convertValue(
          filteredContent.asJava,
          TypeFactory.defaultInstance().constructCollectionType(classOf[java.util.List[_]], classOf[LabelDTO])
        ).asInstanceOf[java.util.List[LabelDTO]]
          .asScala.toSeq

      case _ =>
        Seq.empty[LabelDTO]
    }
  }

  override def disableHbaseTable(tableFullName: String): Unit = {
    val url     = s"$abUrl/graph/v3/datastore/hbase/tables/$tableFullName"
    val payload = scalaMapper.writeValueAsString(Map("enable" -> false))

    val response = httpClient.put(
      url = url,
      data = payload,
      headers = Map("Content-Type" -> "application/json", "Authorization" -> abAuthKey, "Actor-ROLE" -> "ADMIN")
    )
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to disable hbase table: PUT $url status=${response.status} " +
          s"payloadBytes=${payload.length} body=${truncate(response.body)}"
      )
    }
  }

  override def deleteHbaseTable(tableFullName: String): Unit = {
    val url      = s"$abUrl/graph/v3/datastore/hbase/tables/$tableFullName"
    val response = httpClient.delete(url, headers = Map("Authorization" -> abAuthKey, "Actor-ROLE" -> "ADMIN"))
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to delete hbase table: DELETE $url status=${response.status} body=${truncate(response.body)}"
      )
    }
  }
}

object AbServiceImpl {

  /**
    * Truncate a potentially large HTTP response/payload body to keep error
    * messages bounded and avoid leaking full request bodies (which may
    * contain user identifiers) into log sinks via stack traces.
    */
  private[core] def truncate(s: String, max: Int = 200): String =
    if (s == null) "" else if (s.length <= max) s else s.take(max) + s"...(${s.length - max} more)"

  case class CreateStorageResponse(@JsonProperty("result") result: AbService.StorageDTO)

  case class CreateLabelResponse(@JsonProperty("result") result: LabelDTO)

  object HttpMethod {
    val POST   = "POST"
    val PUT    = "PUT"
    val DELETE = "DELETE"
  }
}
