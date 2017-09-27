package com.bayakala.funda.fdasources

import fs2._
import play.api.libs.iteratee._
import com.bayakala.funda._
import slick.jdbc.JdbcProfile
/*
import akka.actor._
import akka.stream.scaladsl._
import akka.stream._
import akka.stream.stage._
import akka.stream.stage.{GraphStage, GraphStageLogic}
*/
/** stream loader class wrapper */
trait FDADataStream {

  /** running Slick DBIOAction to produce a data stream conforming to reactive-streams api.
    * provide strong typed result conversion if required
    * @param slickProfile  Slick jdbc profile such as 'slick.jdbc.H2Profile'
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
      *    val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)
      *    val streamSource = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()
      *    val safeStreamSource = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)(
      *        println("the end finally!"))
      * }}}
      * @param action       a Slick DBIOAction to produce query results
      * @param slickDB      Slick database object
      * @param fetchSize    number of rows cached during database read
      * @param queSize      size of queque used by iteratee as cache to pass elements to fs2 stream
      * @param finalizer    cleanup callback
      * @param convert      just a measure to guarantee conversion function is defined
      *                     when this function is used there has to be a converter defined
      *                     implicitly in compile time
      * @return             a reactive-stream of TARGET row type elements
      */
    def fda_typedStream(action: DBIOAction[Iterable[SOURCE],Streaming[SOURCE],Effect.Read])(
      slickDB: Database)(
      fetchSize: Int, queSize: Int)(
      finalizer: => Unit = ())(
      implicit convert: SOURCE => TARGET): FDAPipeLine[TARGET] = {
      val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
      val action_ = action.withStatementParameters(fetchSize = fetchSize)
      val publisher = slickDB.stream(disableAutocommit andThen action_)
      val enumerator = streams.IterateeStreams.publisherToEnumerator(publisher)

      val s = Stream.eval(async.boundedQueue[Task,Option[SOURCE]](queSize)).flatMap { q =>
        Task { Iteratee.flatten(enumerator |>> pushData(q)).run }.unsafeRunAsyncFuture()
        pipe.unNoneTerminate(q.dequeue).map {row => convert(row)}
      }
      s.onFinalize(Task.delay(finalizer))

    }
/*
    def fda_akkaTypedStream(action: DBIOAction[Iterable[SOURCE],Streaming[SOURCE],Effect.Read])(
      slickDB: Database)(
                         fetchSize: Int, queSize: Int)(
                         finalizer: => Unit = ())(
                         implicit convert: SOURCE => TARGET): FDAPipeLine[TARGET] = {
      val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
      val action_ = action.withStatementParameters(fetchSize = fetchSize)
      val publisher = slickDB.stream(disableAutocommit andThen action_)
      implicit val actorSys = ActorSystem("actor-system")
      implicit val ec = actorSys.dispatcher
      implicit val mat = ActorMaterializer()
      // construct akka source
      val akkaSource = Source.fromPublisher[SOURCE](publisher)

      val s = Stream.eval(async.boundedQueue[Task,Option[SOURCE]](queSize)).flatMap { q =>
        Task(akkaSource.to(new Fs2Gate[SOURCE](q)).run).unsafeRunAsyncFuture()
        pipe.unNoneTerminate(q.dequeue).map {row => convert(row)}
      }
      s.onFinalize({
        actorSys.terminate()
        Task.delay(finalizer)
      })
    } */
    /**
      * returns a reactive-stream from Slick DBIOAction result
      * using play-iteratees and fs2 queque to connect to slick data stream publisher
      * provide facade for error handler and finalizer to support exception and cleanup handling
      * @example {{{
      *    val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)()
      *    val streamSource = streamLoader.fda_plainStream(aqmQuery.result)(db)(512,512)()
      *    val safeStreamSource = streamLoader.fda_plainStream(aqmQuery.result)(db)(512,512)(
      *        println("the end finally!"))
      * }}}
      * @param action       a Slick DBIOAction to produce query results
      * @param slickDB      Slick database object
      * @param fetchSize    number of rows cached during database read
      * @param queSize      size of queque used by iteratee as cache to pass elements to fs2 stream
      * @param finalizer    cleanup callback
      * @return             a reactive-stream of SOURCE row type elements
      */
    def fda_plainStream(action: DBIOAction[Iterable[SOURCE],Streaming[SOURCE],Effect.Read])(
        slickDB: Database)(
                           fetchSize: Int, queSize: Int)(
                           finalizer: => Unit = ()): FDAPipeLine[SOURCE] = {
      val disableAutocommit = SimpleDBIO(_.connection.setAutoCommit(false))
      val action_ = action.withStatementParameters(fetchSize = fetchSize)
      val publisher = slickDB.stream(disableAutocommit andThen action_)
      val enumerator = streams.IterateeStreams.publisherToEnumerator(publisher)

      val s = Stream.eval(async.boundedQueue[Task,Option[SOURCE]](queSize)).flatMap { q =>
        Task { Iteratee.flatten(enumerator |>> pushData(q)).run }.unsafeRunAsyncFuture()
        pipe.unNoneTerminate(q.dequeue)
      }
      s.onFinalize(Task.delay(finalizer))
    }

    /**
      * consume input from enumerator by pushing each element into q queque
      * end and produce error when enqueque could not be completed in timeout
      * @param q          queque for cache purpose
      * @tparam R         stream element type
      * @return           iteratee in new state
      */
    private def pushData[R](q: async.mutable.Queue[Task,Option[R]]): Iteratee[R,Unit] = Cont {
      case Input.EOF   =>
        q.enqueue1(None).unsafeRun
        Done((), Input.Empty)
      case Input.Empty => pushData(q)
      case Input.El(e) =>
        q.enqueue1(Some(e)).unsafeRun
        pushData(q)

    }
/*
    class Fs2Gate[T](q: fs2.async.mutable.Queue[Task,Option[T]]) extends GraphStage[SinkShape[T]] {
      val in = Inlet[T]("inport")
      val shape = SinkShape.of(in)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
        new GraphStageLogic(shape) with InHandler {
          override def preStart(): Unit = {
            pull(in)          //initiate stream elements movement
            super.preStart()
          }

          override def onPush(): Unit = {
            q.enqueue1(Some(grab(in))).unsafeRun()
            pull(in)
          }

          override def onUpstreamFinish(): Unit = {
            q.enqueue1(None).unsafeRun()
            completeStage()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            q.enqueue1(None).unsafeRun()
            completeStage()
          }
          setHandler(in,this)
        }
    }
  */

  }

  /**
    * constructing FDAStreamLoader given slickProfile and converter
    */
  object FDAStreamLoader {
    /**
      * constructor for FDAStreamLoader
      * @example {{{
      *    val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)
      *    val untypedLoader = FDAStreamLoader(slick.jdbc.H2Profile)()
      * }}}
      * @param slickProfile  Slick jdbcprofile such as 'slick.jdbc.H2Profile'
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