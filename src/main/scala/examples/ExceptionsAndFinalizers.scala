package examples
import slick.jdbc.H2Profile.api._
import com.bayakala.funda.samples.SlickModels._
import com.bayakala.funda._
import api._
import scala.language.implicitConversions

object ExceptionsAndFinalizers extends App {

  val db = Database.forConfig("h2db")
  implicit def toState(row: StateTable#TableElementType) =
    StateModel(row.id,row.name)
  val viewLoader = FDAViewLoader(slick.jdbc.H2Profile)(toState _)
  val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toState _)

  val stateSeq = viewLoader.fda_typedRows(StateQuery.result)(db).toSeq
  val viewState = fda_staticSource(stateSeq)(println("***Finally*** the end of viewState!!!"))
  val streamState = streamLoader.fda_typedStream(StateQuery.result)(db)(64,64)(println("***Finally*** the end of streamState!!!"))()

/*
  viewState.startRun
  viewState.take(2).startRun
  streamState.startRun
  streamState.take(3).startRun
  //  ***Finally*** the end of viewState!!!
  //  ***Finally*** the end of viewState!!!
  //  ***Finally*** the end of streamState!!!
  //  ***Finally*** the end of streamState!!!
*/



  def trackRows: FDAUserTask[FDAROW] = row => {
    row match {
      case m@StateModel(id,name) =>
        println(s"State: $id $name")
        println( "----------------")
        fda_next(m)
      case DivideZeroError(msg, e) => //error row
        println(s"***Error:$msg***")
        fda_skip
      case m@_ => fda_next(m)
    }
  }

  def errorRow: FDAUserTask[FDAROW] = row => {
    row match {
      case StateModel(id,name) =>
        val idx = id / (id - 3)
        fda_next(StateModel(idx,name))
      case m@_ => fda_next(m)
    }
  }

  case class DivideZeroError(msg: String, e: Exception) extends FDAROW
  def catchError: FDAUserTask[FDAROW] = row => {
    row match {
      case StateModel(id,name) =>
        try {
          val idx = id / (id - 3)
          fda_next(StateModel(idx, name))
        } catch {
          case e: Exception => //pass an error row
            fda_next(DivideZeroError(s"Divide by zero excption at ${id}",e))
        }
      case m@_ => fda_next(m)
    }
  }



  /*
  streamState.appendTask(errorRow).appendTask(trackRows).startRun
//  State: 0 Alabama
//  ----------------
//  State: -2 Alaska
//  ----------------
//  Exception in thread "main" java.lang.ArithmeticException: / by zero
//  at examples.ExceptionsAndFinalizers$$anonfun$errorRow$1.apply(ExceptionsAndFinalizers.scala:46)
//  ...
//  at java.lang.Thread.run(Thread.java:745)
//  ***Finally*** the end of streamState!!!
*/
  /*
   val v = viewState.appendTask(errorRow).appendTask(trackRows)
   val v1 = v.onError {case e: Exception => println(s"Caught Error in viewState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
   v1.startRun

   val s = streamState.appendTask(errorRow).appendTask(trackRows)
   val s1 = s.onError {case e: Exception => println(s"Caught Error in streamState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
   s1.startRun
  */

  val s = streamState.take(5).appendTask(catchError).appendTask(trackRows)
  val s1 = s.onError {case e: Exception => println(s"Caught Error in streamState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
  s1.startRun


  
}
