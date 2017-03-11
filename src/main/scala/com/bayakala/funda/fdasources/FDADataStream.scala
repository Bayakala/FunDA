package com.bayakala.funda.fdasources

import fs2._
import play.api.libs.iteratee._
import com.bayakala.funda._
import slick.driver.JdbcProfile
import scala.concurrent.duration._
/** stream loader class wrapper */
trait FDADataStream {

  /** running Slick DBIOAction to produce a data stream conforming to reactive-streams api.
    * provide strong typed result conversion if required
    * @param slickProfile  Slick jdbc profile such as 'slick.driver.H2Driver'
    * @param convert       a defined implicit type conversion function.
    *                      from SOURCE type to TARGET type, set to null if not required
    * @tparam SOURCE       source type, result type of DBIOAction, most likely a tuple type
    * @tparam TARGET       final converted type, most likely a case class type
    */
  class FDAStreamLoader[SOURCE, TARGET](slickProfile: JdbcProfile, convert: SOURCE => TARGET) {

    import slickProfile.api._

    /**
      * returns a reactive-stream from Slick DBIOAction result
      * using play-iteratees and fs2 queque to connect to slick data stream publisher
      * provide facade for error handler and finalizer to support exception and cleanup handling
      * also provide stream element conversion from SOURCE type to TARGET type
      * @example {{{
      *    val streamLoader = FDAStreamLoader(slick.driver.H2Driver)(toTypedRow _)
      *    val streamSource = streamLoader.fda_typedStream(aqmQuery.result)(db)(
      *         10.seconds,512,512)()()
      *
      *    val safeStreamSource = streamLoader.fda_typedStream(aqmQuery.result)(db)(
      *         10.seconds,512,512){
      *            case e: Exception => fda_appendRow(FDAErrorRow(new Exception(e)))
      *        }(println("the end finally!"))
      * }}}
      * @param action       a Slick DBIOAction to produce query results
      * @param slickDB      Slick database object
      * @param maxInterval  max time wait on iteratee to consume of next element
      *                     exceeding presumed streaming failure or completion
      *                     use 0.milli to represent infinity
      *                     inform enumerator to release its resources
      * @param fetchSize    number of rows cached during database read
      * @param queSize      size of queque used by iteratee as cache to pass elements to fs2 stream
      * @param errhandler   error handler callback
      * @param finalizer    cleanup callback
      * @param convert      just a measure to guarantee conversion function is defined
      *                     when this function is used there has to be a converter defined
      *                     implicitly in compile time
      * @return             a reactive-stream of TARGET row type elements
      */
    def fda_typedStream(action: DBIOAction[Iterable[SOURCE],Streaming[SOURCE],Effect.Read])(
      slickDB: Database)(
      maxInterval: FiniteDuration, fetchSize: Int, queSize: Int)(
      errhandler: Throwable => FDAPipeLine[TARGET] = null)(
      finalizer: => Unit = ())(
      implicit convert: SOURCE => TARGET): FDAPipeLine[TARGET] = {
      val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
      val action_ = action.withStatementParameters(fetchSize = fetchSize)
      val publisher = slickDB.stream(disableAutocommit andThen action)
      val enumerator = streams.IterateeStreams.publisherToEnumerator(publisher)

      val s = Stream.eval(async.boundedQueue[Task,Option[SOURCE]](queSize)).flatMap { q =>
        Task { Iteratee.flatten(enumerator |>> pushData(q,maxInterval)).run }.unsafeRunAsyncFuture()
        pipe.unNoneTerminate(q.dequeue).map {row => convert(row)}
      }
      if (errhandler != null)
        s.onError(errhandler).onFinalize(Task.delay(finalizer))
      else
        s.onFinalize(Task.delay(finalizer))

    }

    /**
      * returns a reactive-stream from Slick DBIOAction result
      * using play-iteratees and fs2 queque to connect to slick data stream publisher
      * provide facade for error handler and finalizer to support exception and cleanup handling
      * @example {{{
      *    val streamLoader = FDAStreamLoader(slick.driver.H2Driver)()
      *    val streamSource = streamLoader.fda_plainStream(aqmQuery.result)(db)(
      *         10.seconds,512,512)()()
      *
      *    val safeStreamSource = streamLoader.fda_plainStream(aqmQuery.result)(db)(
      *         10.seconds,512,512){
      *            case e: Exception => fda_appendRow(FDAErrorRow(new Exception(e)))
      *        }(println("the end finally!"))
      * }}}
      * @param action       a Slick DBIOAction to produce query results
      * @param slickDB      Slick database object
      * @param maxInterval  max time wait on iteratee to consume of next element
      *                     exceeding presumed streaming failure or completion
      *                     inform enumerator to release its resources
      * @param fetchSize    number of rows cached during database read
      * @param queSize      size of queque used by iteratee as cache to pass elements to fs2 stream
      * @param errhandler   error handler callback
      * @param finalizer    cleanup callback
      * @return             a reactive-stream of SOURCE row type elements
      */
    def fda_plainStream(action: DBIOAction[Iterable[SOURCE],Streaming[SOURCE],Effect.Read])(
        slickDB: Database)(
                           maxInterval: FiniteDuration, fetchSize: Int, queSize: Int)(
                           errhandler: Throwable => FDAPipeLine[SOURCE] = null)(
                           finalizer: => Unit = ()): FDAPipeLine[SOURCE] = {
      val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
      val action_ = action.withStatementParameters(fetchSize = fetchSize)
      val publisher = slickDB.stream(disableAutocommit andThen action)
      val enumerator = streams.IterateeStreams.publisherToEnumerator(publisher)

      val s = Stream.eval(async.boundedQueue[Task,Option[SOURCE]](queSize)).flatMap { q =>
        Task { Iteratee.flatten(enumerator |>> pushData(q,maxInterval)).run }.unsafeRunAsyncFuture()
        pipe.unNoneTerminate(q.dequeue)
      }
      if (errhandler != null)
        s.onError(errhandler).onFinalize(Task.delay(finalizer))
      else
        s.onFinalize(Task.delay(finalizer))
    }

    /**
      * consume input from enumerator by pushing each element into q queque
      * end and produce error when enqueque could not be completed in timeout
      * @param q          queque for cache purpose
      * @param timeout    time to wait for completion of enqueque before error exit
      * @tparam R         stream element type
      * @return           iteratee in new state
      */
    private def pushData[R](q: async.mutable.Queue[Task,Option[R]], timeout: FiniteDuration): Iteratee[R,Unit] = Cont {
      case Input.EOF
        => {
          try {
            if (timeout == 0.milli)
              q.enqueue1(None).unsafeRun
            else
              q.enqueue1(None).unsafeRunFor(timeout)
            Done((), Input.Empty)
          } catch { case err: Throwable =>
              q.enqueue1(None).unsafeRun
              Error(err.getMessage,Input.Empty)
          }
        }
      case Input.Empty
        => pushData(q,timeout)
      case Input.El(e)
        => {
          try {
            if (timeout == 0.milli)
              q.enqueue1(Some(e)).unsafeRun
            else
              q.enqueue1(Some(e)).unsafeRunFor(timeout)
            pushData(q, timeout)
          } catch {case err: Throwable =>
            q.enqueue1(None).unsafeRun
            Error(err.getMessage,Input.Empty)
          }
        }
    }

  }

  /**
    * constructing FDAStreamLoader given slickProfile and converter
    */
  object FDAStreamLoader {
    /**
      * constructor for FDAStreamLoader
      * @example {{{
      *    val streamLoader = FDAStreamLoader(slick.driver.H2Driver)(toTypedRow _)
      *    val untypedLoader = FDAStreamLoader(slick.driver.H2Driver)()
      * }}}
      * @param slickProfile  Slick jdbcprofile such as 'slick.driver.H2Driver'
      * @param converter     a defined implicit type conversion function.
      *                      from SOURCE type to TARGET type, set to null if not required
      * @tparam SOURCE       source type, result type of DBIOAction, most likely a tuple type
      * @tparam TARGET       final converted type, most likely a case class type
      * @return              a new FDAStreamLoader object
      */
    def apply[SOURCE, TARGET](slickProfile: JdbcProfile)(converter: SOURCE => TARGET = null): FDAStreamLoader[SOURCE, TARGET] =
      new FDAStreamLoader[SOURCE, TARGET](slickProfile, converter)
  }
}

/**
  * for global imports
  */
object FDADataStream extends FDADataStream