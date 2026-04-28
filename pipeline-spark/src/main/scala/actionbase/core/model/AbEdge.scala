package actionbase.core.model

import com.kakao.actionbase.v2.core.edge.BulkLoadEdge

/**
  * Edge factory used by pipeline algorithms. The only live surface is
  * [[createJavaBulkLoadEdge]], which [[actionbase.pipeline.bulkload.step01.EdgeEncoder]]
  * tests exercise. JSON I/O is delegated to [[ActionbaseObjectMapper]].
  */
object AbEdge {

  def createJavaBulkLoadEdge(active: Boolean, ts: Long, src: Any, tgt: Any, props: Map[String, Any]): BulkLoadEdge = {
    new BulkLoadEdge(active, ts, src, tgt, convertScalaPropsToJava(props))
  }

  private def convertScalaPropsToJava(props: Map[String, Any]): java.util.Map[String, Object] = {
    val serialized = ActionbaseObjectMapper.scala.writeValueAsString(props)
    ActionbaseObjectMapper.java.readValue(serialized, classOf[java.util.Map[String, Object]])
  }
}
