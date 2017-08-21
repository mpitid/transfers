
package money.transfers.http

sealed trait ApiError extends Throwable {
  def status: Int
  def message: String
}

object ApiError {
  case class InternalError(id: Long, cause: Throwable) extends ApiError {
    def status = 500
    def message = s"unexpected error ${id.toHexString}"
  }
  case class ResourceNotFound(id: String) extends ApiError {
    def status = 404
    def message = s"resource not found $id"
  }
  case class UnsupportedCurrency(currency: String) extends ApiError {
    def status = 400
    def message = s"unsupported currency $currency"
  }
  case class MalformedTransaction(message: String) extends ApiError {
    def status = 400
  }
}
