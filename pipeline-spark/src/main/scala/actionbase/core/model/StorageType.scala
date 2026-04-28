package actionbase.core.model

sealed abstract class StorageType(val prefix: String, val desc: String) extends Serializable

object StorageType {
  case object ST1 extends StorageType("st1", desc = "default storage")
  case object ST2 extends StorageType("st2", desc = "infra-restorable, no initial load")
  case object ST3 extends StorageType("st3", desc = "infra-restorable with initial load")
  case object ST4 extends StorageType("st4", desc = "periodic re-load only, not infra-restorable")

  def byName(name: String): Option[StorageType] = {
    name.toLowerCase match {
      case "st1" => Some(ST1)
      case "st2" => Some(ST2)
      case "st3" => Some(ST3)
      case "st4" => Some(ST4)
      case _     => None
    }
  }

  def findByName(name: String): StorageType = {
    name.toLowerCase match {
      case "st1" => ST1
      case "st2" => ST2
      case "st3" => ST3
      case "st4" => ST4
      case _     => throw new IllegalArgumentException(s"Invalid StorageType: $name")
    }
  }
}
