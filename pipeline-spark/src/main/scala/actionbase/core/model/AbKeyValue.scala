package actionbase.core.model

case class AbKeyValue(key: Array[Byte], value: Array[Byte]) {

  def isCounterKey: Boolean = key.length == 0
}
