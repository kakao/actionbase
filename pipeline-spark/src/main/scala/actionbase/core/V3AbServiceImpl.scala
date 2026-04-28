package actionbase.core

import actionbase.core.AbService.GetLabelByAliasResponse
import actionbase.core.exception.{InvalidHttpStatusCodeException, MutationResultErrorException}
import actionbase.core.model.V3MultiEdgeMutationResponse.V3MultiEdgeMutationResponse
import actionbase.core.model._
import actionbase.pipeline.adapter.{HttpClient, HttpResponse}
import com.kakao.actionbase.v2.core.metadata.Direction

import scala.util.{Failure, Success, Try}

/**
  * HTTP client for the V3 ActionBase edge endpoints. Covers
  *  - `/sync` URL suffix when `syncForce == true`
  *  - `X-Actor-ID` header propagation on mutation calls
  *  - `MutationResultErrorException` when any response item has
  *    `status == "ERROR"`
  *
  * The `HttpClient` adapter sits at
  * [[actionbase.pipeline.adapter.HttpClient]]; the shared `get`/`post`
  * helpers that used to be `protected def` on the V3 trait are now
  * private methods on the impl (since the trait no longer needs them).
  *
  * @note Slimmed in OSS port: LazyEval replaced with a thunk, HttpClient swapped for the OSS adapter trait.
  */
class V3AbServiceImpl(
    val baseUrl: String,
    val authorizationKey: String,
    val httpClient: HttpClient
) extends V3AbService {

  private val defaultHeaders    = Map("Content-Type" -> "application/json", "Authorization" -> authorizationKey)
  private val HEADER_X_ACTOR_ID = "X-Actor-ID"

  override def mutateEdge(
      actor: String,
      database: String,
      table: String,
      request: V3EdgeMutationRequest,
      syncForce: Boolean = false
  ): V3EdgeMutationResponse = {
    val defaultUrl = s"$baseUrl/graph/v3/databases/$database/tables/$table/edges"
    val url        = if (syncForce) s"$defaultUrl/sync" else defaultUrl
    val requestBody = ActionbaseObjectMapper.scala.writeValueAsString(request)

    val headers = defaultHeaders + (HEADER_X_ACTOR_ID -> actor)

    val response: V3EdgeMutationResponse =
      post(httpClient, url, headers, requestBody, classOf[V3EdgeMutationResponse])

    val hasError = response.results.exists(_.status == "ERROR")
    if (hasError) {
      throw new MutationResultErrorException(
        s"Failed to POST $url. payloadBytes=${requestBody.length} response=${AbServiceImpl.truncate(response.toString)}"
      )
    }

    response
  }

  override def mutateMultiEdge(
      actor: String,
      database: String,
      table: String,
      request: V3MultiEdgeMutationRequest
  ): V3MultiEdgeMutationResponse = {
    val url         = s"$baseUrl/graph/v3/databases/$database/tables/$table/multi-edges"
    val requestBody = ActionbaseObjectMapper.scala.writeValueAsString(request)

    val headers = defaultHeaders + (HEADER_X_ACTOR_ID -> actor)

    val response: V3MultiEdgeMutationResponse =
      post(httpClient, url, headers, requestBody, classOf[V3MultiEdgeMutationResponse])

    if (response.hasError) {
      throw new MutationResultErrorException(
        s"Failed to POST $url. payloadBytes=${requestBody.length} response=${AbServiceImpl.truncate(response.toString)}"
      )
    }

    response
  }

  override def getTableByAlias(database: String, alias: String): GetLabelByAliasResponse =
    get(
      httpClient,
      url = s"$baseUrl/graph/v2/service/$database/alias/$alias",
      headers = defaultHeaders,
      responseType = classOf[GetLabelByAliasResponse]
    )

  override def getEdge(database: String, table: String, source: Any, target: Any): V3EdgeQueryResponse =
    get(
      httpClient,
      url = s"$baseUrl/graph/v3/databases/$database/tables/$table/edges/get?source=$source&target=$target",
      headers = defaultHeaders,
      responseType = classOf[V3EdgeQueryResponse]
    )

  override def scanEdges(
      database: String,
      table: String,
      index: String,
      start: Any,
      direction: Direction,
      ranges: Option[String] = None,
      filters: Option[String] = None,
      offset: Option[String] = None,
      limit: Int = 25
  ): V3EdgeQueryResponse = {
    val rangeParam  = ranges.map(r => s"&ranges=$r").getOrElse("")
    val filterParam = filters.map(f => s"&filters=$f").getOrElse("")
    val offsetParam = offset.map(o => s"&offset=$o").getOrElse("")
    val limitParam  = s"&limit=$limit"

    get(
      httpClient,
      url =
        s"$baseUrl/graph/v3/databases/$database/tables/$table/edges/scan/$index?start=$start&direction=${direction.toString}$rangeParam$filterParam$offsetParam$limitParam",
      headers = defaultHeaders,
      responseType = classOf[V3EdgeQueryResponse]
    )
  }

  override def countEdges(
      database: String,
      table: String,
      start: Any,
      direction: Direction
  ): V3EdgeCountQueryResponse =
    get(
      httpClient,
      url =
        s"$baseUrl/graph/v3/databases/$database/tables/$table/edges/count?start=$start&direction=${direction.toString}",
      headers = defaultHeaders,
      responseType = classOf[V3EdgeCountQueryResponse]
    )

  private def get[T](
      httpClient: HttpClient,
      url: String,
      headers: Map[String, String],
      responseType: Class[T]
  ): T = {
    val response: HttpResponse = httpClient.get(url, headers)
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to call GET $url status=${response.status} body=${AbServiceImpl.truncate(response.body)}"
      )
    }
    handleResponse(response, responseType)
  }

  private def post[T](
      httpClient: HttpClient,
      url: String,
      headers: Map[String, String],
      requestBody: String,
      responseType: Class[T]
  ): T = {
    val response: HttpResponse = httpClient.post(url, requestBody, headers)
    if (!response.is2xx) {
      throw new InvalidHttpStatusCodeException(
        s"Failed to call POST $url status=${response.status} " +
          s"payloadBytes=${requestBody.length} body=${AbServiceImpl.truncate(response.body)}"
      )
    }
    handleResponse(response, responseType)
  }

  private def handleResponse[T](response: HttpResponse, clazz: Class[T]): T =
    Try(ActionbaseObjectMapper.scala.readValue(response.body, clazz)) match {
      case Success(value) => value
      case Failure(ex)    => throw ex
    }
}
