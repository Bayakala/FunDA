package examples
import slick.jdbc.meta._
import com.bayakala.funda._
import api._
import scala.language.implicitConversions
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import slick.jdbc.H2Profile.api._
import com.bayakala.funda.samples.SlickModels._


object ParallelExecution extends App {

  val db = Database.forConfig("h2db")

  //drop original table schema
  val futVectorTables = db.run(MTable.getTables)

  val futDropTable = futVectorTables.flatMap{ tables => {
    val tableNames = tables.map(t => t.name.name)
    if (tableNames.contains(NORMAQMQuery.baseTableRow.tableName))
      db.run(NORMAQMQuery.schema.drop)
    else Future(():Unit)
  }
  }.andThen {
    case Success(_) => println(s"Table ${NORMAQMQuery.baseTableRow.tableName} dropped successfully! ")
    case Failure(e) => println(s"Failed to drop Table ${NORMAQMQuery.baseTableRow.tableName}, it may not exist! Error: ${e.getMessage}")
  }
  Await.ready(futDropTable,Duration.Inf)

  //create new table to refine AQMRawTable
  val actionCreateTable = NORMAQMQuery.schema.create
  val futCreateTable = db.run(actionCreateTable).andThen {
    case Success(_) => println("Table created successfully!")
    case Failure(e) => println(s"Table may exist already! Error: ${e.getMessage}")
  }
  //would carry on even fail to create table
  Await.ready(futCreateTable,Duration.Inf)


  //truncate data, only available in slick 3.2.1
  val futTruncateTable = futVectorTables.flatMap{ tables => {
    val tableNames = tables.map(t => t.name.name)
    if (tableNames.contains(NORMAQMQuery.baseTableRow.tableName))
      db.run(NORMAQMQuery.schema.truncate)
    else Future(():Unit)
  }
  }.andThen {
    case Success(_) => println(s"Table ${NORMAQMQuery.baseTableRow.tableName} truncated successfully!")
    case Failure(e) => println(s"Failed to truncate Table ${NORMAQMQuery.baseTableRow.tableName}! Error: ${e.getMessage}")
  }
  Await.ready(futDropTable,Duration.Inf)

  //a conceived task for the purpose of resource consumption
  //getting id with corresponding name from STATES table
  def getStateID(state: String): Int = {
    //create a stream for state id with state name
    implicit def toState(row:  StateTable#TableElementType) = StateModel(row.id,row.name)
    val stateLoader = FDAViewLoader(slick.jdbc.H2Profile)(toState _)
    val stateSeq = stateLoader.fda_typedRows(StateQuery.result)(db).toSeq
    //constructed a Stream[Task,String]
    val stateStream =  fda_staticSource(stateSeq)()
    var id  = -1
    def getid: FDAUserTask[FDAROW] = row => {
      row match {
        case StateModel(stid,stname) =>   //target row type
          if (stname.contains(state)) {
            id = stid
            fda_break      //exit
          }
          else fda_skip   //take next row
        case _ => fda_skip
      }
    }
    stateStream.appendTask(getid).startRun
    id
  }
  //another conceived task for the purpose of resource consumption
  //getting id with corresponding names from COUNTIES table
  def getCountyID(state: String, county: String): Int = {
    //create a stream for county id with state name and county name
    implicit def toCounty(row:  CountyTable#TableElementType) = CountyModel(row.id,row.name)
    val countyLoader = FDAViewLoader(slick.jdbc.H2Profile)(toCounty _)
    val countySeq = countyLoader.fda_typedRows(CountyQuery.result)(db).toSeq
    //constructed a Stream[Task,String]
    val countyStream =  fda_staticSource(countySeq)()
    var id  = -1
    def getid: FDAUserTask[FDAROW] = row => {
      row match {
        case CountyModel(cid,cname) =>   //target row type
          if (cname.contains(state) && cname.contains(county)) {
            id = cid
            fda_break      //exit
          }
          else fda_skip   //take next row
        case _ => fda_skip
      }
    }
    countyStream.appendTask(getid).startRun
    id
  }

  //process input row and produce action row to insert into NORMAQM
  def getIdsThenInsertAction: FDAUserTask[FDAROW] = row => {
    row match {
      case aqm: AQMRPTModel =>
        if (aqm.valid) {
          val stateId = getStateID(aqm.state)
          val countyId = getCountyID(aqm.state,aqm.county)
          val action = NORMAQMQuery += NORMAQMModel(0,aqm.mid, stateId, countyId, aqm.year,aqm.value,aqm.total)
          fda_next(FDAActionRow(action))
        }
        else fda_skip
      case _ => fda_skip
    }
  }
  //runner for the action rows
  val runner = FDAActionRunner(slick.jdbc.H2Profile)
  def runInsertAction: FDAUserTask[FDAROW] = row =>
    row match {
      case FDAActionRow(action) =>
        runner.fda_execAction(action)(db)
        fda_skip
      case _ => fda_skip
    }

  //create parallel sources
  //get a stream of years
  val qryYears = AQMRPTQuery.map(_.year).distinct
  case class Years(year: Int) extends FDAROW

  implicit def toYears(y: Int) = Years(y)

  val yearViewLoader = FDAViewLoader(slick.jdbc.H2Profile)(toYears _)
  val yearSeq = yearViewLoader.fda_typedRows(qryYears.result)(db).toSeq
  val yearStream = fda_staticSource(yearSeq)()

  //strong row type
  implicit def toAQMRPT(row: AQMRPTTable#TableElementType) =
    AQMRPTModel(row.rid, row.mid, row.state, row.county, row.year, row.value, row.total, row.valid)

  //shared stream loader when operate in parallel mode
  val AQMRPTLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toAQMRPT _)

  //loading rows with year yr
  def loadRowsInYear(yr: Int) = {
    //a new query
    val query = AQMRPTQuery.filter(row => row.year === yr)
    //reuse same loader
    AQMRPTLoader.fda_typedStream(query.result)(db)(256, 256)(println(s"End of stream ${yr}!!!!!!"))
  }

  //loading rows by year
  def loadRowsByYear: FDASourceLoader = row => {
    row match {
      case Years(y) => loadRowsInYear(y) //produce stream of the year
      case _ => fda_appendRow(FDANullRow)
    }

  }


  //start counter
  val cnt_start = System.currentTimeMillis()

  def showRecord: FDAUserTask[FDAROW] = row => {
    row match {
      case Years(y) => println(y); fda_skip
      case aqm: AQMRPTModel =>
        println(s"${aqm.year}  $aqm")
        fda_next(aqm)
      case FDAActionRow(action) =>
        println(s"${action}")
        fda_skip
      case _ => fda_skip
    }
  }

  //the following is a process of composition of stream combinators
  //get parallel source constructor
  val parSource = yearStream.toParSource(loadRowsByYear)
  //implicit val strategy = Strategy.fromCachedDaemonPool("cachedPool")
  //produce a stream from parallel sources
  val source = fda_par_source(parSource)(4)
  //turn getIdsThenInsertAction into parallel task
  val parTasks = source.toPar(getIdsThenInsertAction)
  //runPar to produce a new stream
  val actionStream =fda_runPar(parTasks)(4)
  //turn runInsertAction into parallel task
  val parRun = actionStream.toPar(runInsertAction)
  //runPar and carry out by startRun
  fda_runPar(parRun)(2).startRun

  println(s"processing 219400 rows parallelly  in ${(System.currentTimeMillis - cnt_start)/1000} seconds")



}
