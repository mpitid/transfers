
package money.transfers

import java.util.concurrent.atomic.AtomicReference

import scalaz.\/

package object util {

  implicit class RichAtomicReference[A](val underlying: AtomicReference[A]) extends AnyVal {
    /** Update reference with the result of a function on the previous value.
      * The function may be called multiple times, so it should be idempotent.
      * Return the previous value. */
    def update(fun: (A) => A): A = {
      var init = underlying.get()
      while (!underlying.compareAndSet(init, fun(init))) {
        init = underlying.get()
      }
      init
    }

    /** Same as `update` but encode failures in a disjunction. */
    def updateMaybe[E](fun: (A) => E \/ A): E \/ A = {
      var v1 = underlying.get()
      var v2 = fun(v1)
      var err = v2.map(_ => false).getOrElse(true)
      while (!err && !underlying.compareAndSet(v1, v2.getOrElse(???))) {
        v1 = underlying.get()
        v2 = fun(v1)
        err = v2.map(_ => false).getOrElse(true)
      }
      v2
    }
  }
}
