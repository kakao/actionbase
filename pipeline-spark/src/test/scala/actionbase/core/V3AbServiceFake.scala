package actionbase.core

import actionbase.core.AbService.GetLabelByAliasResponse
import actionbase.core.V3AbServiceFake._
import actionbase.core.model.V3MultiEdgeMutationResponse.V3MultiEdgeMutationResponse
import actionbase.core.model.{
  ActionbaseObjectMapper,
  V3Edge,
  V3EdgeCountQueryResponse,
  V3EdgeMutationRequest,
  V3EdgeMutationResponse,
  V3EdgeQueryResponse,
  V3MultiEdgeMutationRequest
}
import com.kakao.actionbase.v2.core.metadata.{Direction, EdgeOperation, LabelDTO}

import scala.collection.mutable

/**
  * In-memory [[V3AbService]] for tests. Keeps a per-(database, table)
  * `VersionedMap` so tests can assert on what was persisted.
  *
  * Includes a fixed `schemaJson` returned by [[getTableByAlias]] and the
  * per-operation dispatch in [[mutateEdge]] (INSERT activates,
  * UPDATE leaves active-flag as-is, DELETE deactivates).
  */
class V3AbServiceFake(
    val storage: mutable.Map[(String, String), VersionedMap] = mutable.Map.empty[(String, String), VersionedMap],
    val aliasStorage: mutable.Map[String, String] = mutable.Map.empty[String, String]
) extends V3AbService {

  private val schemaJson =
    """{
      |        "name": "example.example_label_v1",
      |        "desc": "example fake",
      |        "type": "INDEXED",
      |        "schema": {
      |          "src": "LONG",
      |          "tgt": "LONG",
      |          "fields": [
      |            { "name": "createdAt", "type": "LONG", "nullable": false },
      |            { "name": "permission", "type": "STRING", "nullable": false },
      |            { "name": "origin", "type": "STRING", "nullable": true }
      |          ]
      |        },
      |        "dirType": "BOTH",
      |        "storage": "hbase_sandbox",
      |        "indices": [
      |          {
      |            "name": "permission_created_at_desc",
      |            "fields": [
      |              { "name": "permission", "order": "ASC" },
      |              { "name": "createdAt", "order": "DESC" }
      |            ]
      |          },
      |          {
      |            "name": "created_at_desc",
      |            "fields": [
      |              { "name": "createdAt", "order": "DESC" }
      |            ]
      |          }
      |        ]
      |    }""".stripMargin

  def clear(): Unit = {
    storage.clear()
    aliasStorage.clear()
  }

  def showAll(): Unit =
    storage.foreach {
      case ((db, table), versionedMap) =>
        println(s"Database: $db, Table: $table")
        versionedMap.data.foreach {
          case (key, versionedValue) =>
            println(s"  Key: $key, Value: $versionedValue")
        }
    }

  override def mutateEdge(
      actor: String,
      database: String,
      table: String,
      request: V3EdgeMutationRequest,
      syncForce: Boolean = false
  ): V3EdgeMutationResponse = {
    val _table       = aliasStorage.getOrElse(table, table)
    val insertEvents = request.mutations.filter(_.`type` == EdgeOperation.INSERT).map(_.edge)
    val updateEvents = request.mutations.filter(_.`type` == EdgeOperation.UPDATE).map(_.edge)
    val deleteEvents = request.mutations.filter(_.`type` == EdgeOperation.DELETE).map(_.edge)

    storage
      .getOrElseUpdate((database, _table), new VersionedMap())
      .putAll(insertEvents, activeOpt = Some(true))
    storage
      .getOrElseUpdate((database, _table), new VersionedMap())
      .putAll(updateEvents, activeOpt = None)
    storage
      .getOrElseUpdate((database, _table), new VersionedMap())
      .putAll(deleteEvents, activeOpt = Some(false))

    V3EdgeMutationResponse(results = List.empty)
  }

  override def mutateMultiEdge(
      actor: String,
      database: String,
      table: String,
      request: V3MultiEdgeMutationRequest
  ): V3MultiEdgeMutationResponse = {
    val _table = aliasStorage.getOrElse(table, table)
    val edges = request.mutations.map(_.edge).map { _edge =>
      V3Edge(
        version = _edge.version,
        source = _edge.source,
        target = _edge.target,
        properties = _edge.properties
      )
    }
    storage.getOrElseUpdate((database, _table), new VersionedMap()).putAll(edges, activeOpt = Some(true))

    V3MultiEdgeMutationResponse(results = Seq.empty)
  }

  override def getTableByAlias(database: String, alias: String): GetLabelByAliasResponse =
    GetLabelByAliasResponse("fake_target", ActionbaseObjectMapper.scala.readValue(schemaJson, classOf[LabelDTO]))

  override def getEdge(database: String, table: String, source: Any, target: Any): V3EdgeQueryResponse =
    getEdgeInternal(database, table, source, Some(target))

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
    val _labelName = aliasStorage.getOrElse(table, table)
    val edges =
      storage.getOrElse((database, _labelName), new VersionedMap()).getEdges(start, None)

    V3EdgeQueryResponse(
      edges = edges,
      count = edges.size,
      total = edges.size,
      offset = None,
      hasNext = false
    )
  }

  override def countEdges(
      database: String,
      table: String,
      start: Any,
      direction: Direction
  ): V3EdgeCountQueryResponse = {
    val response = getEdgeInternal(database, table, start, None)
    V3EdgeCountQueryResponse(
      start = start.toString,
      direction = direction.name(),
      count = response.count
    )
  }

  private def getEdgeInternal(
      database: String,
      table: String,
      source: Any,
      target: Option[Any]
  ): V3EdgeQueryResponse = {
    val _labelName = aliasStorage.getOrElse(table, table)
    val edges =
      storage.getOrElse((database, _labelName), new VersionedMap()).getEdges(source, target)

    V3EdgeQueryResponse(
      edges = edges,
      count = edges.size,
      total = edges.size,
      offset = None,
      hasNext = false
    )
  }
}

object V3AbServiceFake {

  class VersionedMap(initial: Map[Key, VersionedValue] = Map.empty) {
    var data: Map[Key, VersionedValue] = initial

    def get(key: Key): Option[VersionedValue] = data.get(key)

    def getEdges(source: Any, tgt: Option[Any]): Seq[V3Edge] =
      source.toString.split(",").toSeq.flatMap(_source => getEdge(_source, tgt.map(_.toString)))

    def getEdge(source: String, tgt: Option[String]): Seq[V3Edge] =
      data
        .filter {
          case (key, versionedValue) =>
            versionedValue.active &&
              key.source == source &&
              tgt.forall(_ == key.target)
        }
        .map {
          case (key, versionedValue) =>
            toEdge(key, versionedValue)
        }
        .toSeq

    def put(key: Key, edge: V3Edge, activeOpt: Option[Boolean]): Unit = {
      val version               = edge.version
      val currentVersionedValue = data.getOrElse(key, VersionedValue(Map.empty, 0, active = false))
      val currentProps          = currentVersionedValue.props
      val currentActive         = currentVersionedValue.active

      val newTs = if (version > currentVersionedValue.ts) version else currentVersionedValue.ts

      val newProps = edge.properties.foldLeft(currentProps) {
        case (currentProps, (propKey, propValue)) =>
          val currentProp    = currentProps.get(propKey)
          val currentVersion = currentProp.map(_.version).getOrElse(-1L)
          if (version >= currentVersion) {
            // V3 INSERT semantics: unspecified fields are materialised as null.
            val isPropCreated = currentProp.exists(_.isCreated) || activeOpt.contains(true)
            currentProps.updated(propKey, Prop(propValue, version, isPropCreated))
          } else {
            currentProps
          }
      }

      val newActive = activeOpt
        .filter(_ => version > currentVersionedValue.ts)
        .getOrElse(currentActive)

      data = data.updated(key, VersionedValue(newProps, newTs, newActive))
    }

    def putAll(edges: Seq[V3Edge], activeOpt: Option[Boolean]): Unit =
      edges.foreach { edge =>
        put(Key(edge.source, edge.target), edge, activeOpt)
      }

    private def toEdge(key: Key, versionedValue: VersionedValue): V3Edge = {
      val props = versionedValue.props.mapValues(_.value).toMap
      V3Edge(versionedValue.ts, key.source, key.target, props)
    }
  }

  case class Prop(value: Any, version: Long, isCreated: Boolean)
  case class VersionedValue(props: Map[String, Prop], ts: Long, active: Boolean)
  case class Key(source: Any, target: Any)
}
