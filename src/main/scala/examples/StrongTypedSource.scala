package com.bayakala.funda.examples
import slick.driver.H2Driver.api._
import com.bayakala.funda._
import api._
import com.bayakala.funda.samples._
import scala.language.implicitConversions


object StrongTypedSource extends App {

  val aqmraw =  SlickModels.AQMRawQuery

  val db = Database.forConfig("h2db")
  // aqmQuery.result returns Seq[(String,String,String,String)]
  val aqmQuery = aqmraw.map {r => (r.year,r.state,r.county,r.value)}
  // user designed strong typed resultset type. must extend FDAROW
  case class TypedRow(year: String, state: String, county: String, value: String) extends FDAROW
  // strong typed resultset conversion function. declared implicit to remind during compilation
  implicit def toTypedRow(row: (String,String,String,String)): TypedRow =
    TypedRow(row._1,row._2,row._3,row._4)
  // loader to read from database and convert result collection to strong typed collection
  val viewLoader = FDAViewLoader(slick.driver.H2Driver)(toTypedRow _)
  val dataSeq = viewLoader.fda_typedRows(aqmQuery.result)(db).toSeq
  // turn Seq collection into fs2 stream
  val aqmStream =  fda_staticSource(dataSeq)()()
  // now access fields in the strong typed resultset
  def showRecord: FDAUserTask[FDAROW] = row => {
    row match {
      case qmr: TypedRow =>
        println(s"州名: ${qmr.state}")
        println(s"县名：${qmr.county}")
        println(s"年份：${qmr.year}")
        println(s"取值：${qmr.value}")
        println("-------------")
        fda_skip
      case _ => fda_skip
    }
  }
  // use stream combinators with field names
  aqmStream.filter{r => r.year > "1999"}.take(3).appendTask(showRecord).startRun

  val allState = aqmraw.map(_.state)
  //no converter to help type inference. must provide type parameters explicitly
  val stateLoader = FDAViewLoader[String,String](slick.driver.H2Driver)()
  val stateSeq = stateLoader.fda_plainRows(allState.distinct.result)(db).toSeq
  //constructed a Stream[Task,String]
  val stateStream =  fda_staticSource(stateSeq)()()
  //strong typed row type. must extend FDAROW
  case class StateRow(state: String) extends FDAROW
  def showState: FDAUserTask[FDAROW] = row => {
    row match {
      case StateRow(sname) =>
        println(s"州名称：$sname")
        fda_skip
      case _ => fda_skip
    }
  }
  //first convert to StateRows to turn Stream[Task,FDAROW] typed stream
  stateStream.map{s => StateRow(s)}
    .filter{r => r.state > "Alabama"}.take(3)
    .appendTask(showState).startRun


  val streamLoader = FDAStreamLoader(slick.driver.H2Driver)(toTypedRow _)
  val streamSource = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()()
  streamSource.filter{r => r.year > "1999"}.take(3).appendTask(showRecord).startRun

  val stateStreamLoader = FDAStreamLoader[String,String](slick.driver.H2Driver)()
  val stateStreamSource = stateStreamLoader.fda_plainStream(allState.distinct.result)(db)(512,512)()()

  //first convert to StateRows to turn Stream[Task,FDAROW] typed stream
  stateStreamSource.map{s => StateRow(s)}
    .filter{r => r.state > "Alabama"}.take(3)
    .appendTask(showState).startRun
}