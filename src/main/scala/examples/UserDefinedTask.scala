package com.bayakala.funda.examples
import slick.jdbc.meta._
import scala.language.implicitConversions
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import slick.jdbc.H2Profile.api._
import com.bayakala.funda._
import api._
import com.bayakala.funda.samples.SlickModels._

object UserDefinedTasks extends App {


  val db = Database.forConfig("h2db")

  //drop original table schema
  val futVectorTables = db.run(MTable.getTables)

  val futDropTable = futVectorTables.flatMap{ tables => {
    val tableNames = tables.map(t => t.name.name)
    if (tableNames.contains(AQMRPTQuery.baseTableRow.tableName))
      db.run(AQMRPTQuery.schema.drop)
    else Future(():Unit)
  }
  }.andThen {
    case Success(_) => println(s"Table ${AQMRPTQuery.baseTableRow.tableName} dropped successfully! ")
    case Failure(e) => println(s"Failed to drop Table ${AQMRPTQuery.baseTableRow.tableName}, it may not exist! Error: ${e.getMessage}")
  }
  Await.ready(futDropTable,Duration.Inf)

  //create new table to refine AQMRawTable
  val actionCreateTable = AQMRPTQuery.schema.create
  val futCreateTable = db.run(actionCreateTable).andThen {
    case Success(_) => println("Table created successfully!")
    case Failure(e) => println(s"Table may exist already! Error: ${e.getMessage}")
  }
  //would carry on even fail to create table
  Await.ready(futCreateTable,Duration.Inf)


  //truncate data, only available in slick 3.2.1
  val futTruncateTable = futVectorTables.flatMap{ tables => {
    val tableNames = tables.map(t => t.name.name)
    if (tableNames.contains(AQMRPTQuery.baseTableRow.tableName))
      db.run(AQMRPTQuery.schema.truncate)
    else Future(():Unit)
  }
  }.andThen {
    case Success(_) => println(s"Table ${AQMRPTQuery.baseTableRow.tableName} truncated successfully!")
    case Failure(e) => println(s"Failed to truncate Table ${AQMRPTQuery.baseTableRow.tableName}! Error: ${e.getMessage}")
  }
  Await.ready(futDropTable,Duration.Inf)


  //load original table content
  //original table strong-typed-row
  case class AQMRaw(mid: String, state: String,
                    county: String, year: String, value: String) extends FDAROW
  implicit def toAQMRaw(row: (String,String,String,String,String)) =
    AQMRaw(row._1,row._2,row._3,row._4,row._5)
  val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toAQMRaw _)
  //  val queryAQMRaw = for { r <- AQMRawQuery } yield (r.mid,r.state,r.county,r.year,r.value)
  val queryAQMRaw = sql"""
    SELECT MEASUREID,STATENAME,COUNTYNAME,REPORTYEAR,VALUE FROM AIRQM
  """.as[(String,String,String,String,String)]

  val streamAQMRaw: FDAPipeLine[FDAROW] = streamLoader.fda_typedStream(queryAQMRaw)(db)(512,512)()


  //filter out rows with inconvertible value strings and out of ranged value and year
  def filterRows: FDAUserTask[FDAROW] = row => {
    row match {
      case r: AQMRaw => {
        try {
          val yr = r.year.toInt
          val v = r.value.toInt
          val vlu = if ( v > 10  ) 10 else v
          val data = AQMRPTModel(0,r.mid.toInt,r.state,r.county,yr,vlu,0,true)
          if ((yr > 1960 && yr < 2018))
            fda_next(data)   //this row ok. pass downstream
          else
            fda_skip    //filter out this row
        } catch {
          case e: Exception =>
            fda_next(AQMRPTModel(0,r.mid.toInt,r.state,r.county,2000,0,0,false))
          //pass a invalid row
        }
      }
      case _ => fda_skip   //wrong type, skip
    }
  }

  //transform data to action for later execution
  def toAction: FDAUserTask[FDAROW] = row => {
    row match {
      case r: AQMRPTModel =>
        val queryAction = AQMRPTQuery += r
        fda_next(FDAActionRow(queryAction))
      case other @ _ => fda_next(other)
    }
  }

  //get a query runner and an action task
  val actionRunner = FDAActionRunner(slick.jdbc.H2Profile)
  def runActionRow: FDAUserTask[FDAROW] = action => {
    action match {
      case FDAActionRow(q) => actionRunner.fda_execAction(q)(db)
        fda_skip
      case _ => fda_skip
    }
  }


  //start the program
  val streamAllTasks =  streamAQMRaw.appendTask(filterRows)
    .appendTask(toAction)
    .appendTask(runActionRow)

  val streamToRun = streamAllTasks.onError { case e: Exception => println("Error:"+e.getMessage); fda_appendRow(FDAErrorRow(new Exception(e))) }

  streamToRun.startRun

  //aggregate-task demo: get count and sum of value for each state and year
  val orderedAQMRPT = AQMRPTQuery.sortBy(r => (r.state,r.year))
  //TableElementType conversion. must declare implicit
  implicit def toAQMRPT(row: AQMRPTTable#TableElementType) =
    AQMRPTModel(row.rid,row.mid,row.state,row.county,row.year,row.value,row.total,row.valid)
  val aqmrStreamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toAQMRPT _)
  val aqmrStream: FDAPipeLine[FDAROW] = aqmrStreamLoader.fda_typedStream(orderedAQMRPT.result)(db)(512,512)()
  //user defined aggregator type.
  case class Accu(state: String, county: String, year: Int, count: Int, sumOfValue: Int)
  //user defined aggregation task
  def aggregateValue: FDAAggrTask[Accu,FDAROW] = (accu,row) => {
    row match {
      case aqmr: AQMRPTModel =>
        if (accu.state == "" || (aqmr.state == accu.state && aqmr.year == accu.year))
        //same condition: inc count and add sum, pass no row downstream
          (Accu(aqmr.state,aqmr.county,aqmr.year,accu.count+1, accu.sumOfValue+aqmr.value),fda_skip)
        else
        //reset accumulator, create a new aggregated row and pass downstream
          (Accu(aqmr.state,aqmr.county,aqmr.year,1, aqmr.value)
            ,fda_next(AQMRPTModel(0,9999,accu.state,accu.county,accu.year
            ,accu.count,accu.sumOfValue/accu.count,true)))
      case FDANullRow =>
        //last row encountered. create and pass new aggregated row
        (Accu(accu.state,accu.county,accu.year,1, 0)
          ,fda_next(AQMRPTModel(0,9999,accu.state,accu.county,accu.year
          ,accu.count,accu.sumOfValue/accu.count,true)))
      //incorrect row type, do nothing
      case _ => (accu,fda_skip)
    }
  }


  aqmrStream.aggregateTask(Accu("","",0,0,0),aggregateValue)
    .appendTask(toAction)
    .appendTask(runActionRow)
    .startRun


}
