package actionbase.pipeline.adapter

/**
  * Minimal HTTP client abstraction used by OSS service implementations
  * (`AbServiceImpl`, `V3AbServiceImpl`, `AbAdminServiceImpl`).
  *
  * Concrete implementations live in the external consumer (e.g.
  * Apache HttpComponents client) — OSS ships only the surface that the
  * restored algorithms invoke (get/post/put/delete + 2xx-aware response).
  *
  * Signatures are chosen so an existing HTTP client impl can be plugged
  * in through a thin adapter.
  */
trait HttpClient extends Serializable {
  def get(url: String, headers: Map[String, String] = Map.empty): HttpResponse
  def post(url: String, data: String = "", headers: Map[String, String] = Map.empty): HttpResponse
  def put(url: String, data: String = "", headers: Map[String, String] = Map.empty): HttpResponse
  def delete(url: String, data: String = "", headers: Map[String, String] = Map.empty): HttpResponse
}

/**
  * Response envelope for [[HttpClient]]. `is2xx` is the single predicate
  * the restored algorithms rely on to branch between happy path and
  * error propagation.
  */
case class HttpResponse(status: Int, body: String) {
  def is2xx: Boolean = status >= 200 && status < 300
}
