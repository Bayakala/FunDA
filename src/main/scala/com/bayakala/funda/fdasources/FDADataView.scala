package com.bayakala.funda.fdasources

import com.bayakala.funda._
import fs2._

/**
  * produce a static view source from a Seq[ROW] collection returned from
  * running a fda_typedRows function. provide error handling and cleanup
  */
trait FDADataView {
  /**
    * return a fs2 stream by closing a Pull object
    * @param h       a Seq[ROW] type collection
    * @tparam ROW    type of element inside collection
    * @return        an effectful stream
    */
  private def streamSeq[ROW](h: Seq[ROW]): FDAPipeLine[ROW] =
    pullSeq(h).close

  /**
    * emit a collection of rows one by one through a Pull object.
    * could use fold to reduce Seq[ROW] to Stream[Task,ROW] like this:
    * foldLeft(Stream[Task,Int]())((s,a) => s ++ Stream.emit(a))
    * but since h could be huge and recursion safety could not be guaranteed
    * streaming through Pull object is a much safer option because
    * Pull is a free monad
    * @param h        a Seq[ROW] type collection
    * @tparam ROW     type of element inside collection
    * @return         Pull in new state
    */
  private def pullSeq[ROW](h: Seq[ROW]): FDAPipeJoint[ROW] = {
    val it = h.iterator
    def go(it: Iterator[ROW]): Pull[Task, ROW, Unit] = for {
      res <- Pull.eval(Task.delay({ if (it.hasNext) Some(it.next()) else None }))
      next <- res.fold[Pull[Task, ROW, Unit]](Pull.done)(o => Pull.output1(o) >> go(it))
    } yield next
    go(it)
  }

  /**
    * produce a static view source from a Seq[ROW] collection using famous 'bracket'
    * provide facade to error handling and cleanup
    * @example {{{
    *     val source = fda_staticSource(dataSeq)()
    *
    *     val safeSource = fda_staticSource(dataSeq)(
    *        println("the end finally!"))
    * }}}
    * @param acquirer       the Seq[ROW] collection
    * @param finalizer      cleanup callback
    * @tparam ROW           type of row
    * @return               a new stream
    */
  def fda_staticSource[ROW](acquirer: => Seq[ROW])(
                            finalizer: => Unit = ()): FDAPipeLine[ROW] = {
     val s = Stream.bracket(Task.delay(acquirer))(r => streamSeq(r), r => Task.delay((): Unit))
     s.onFinalize(Task.delay(finalizer))
    }

  }

/**
  * for global imports
  */
object FDADataView extends FDADataView
