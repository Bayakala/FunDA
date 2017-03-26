package examples
import com.bayakala.funda._
import api._
import scala.language.implicitConversions
import slick.jdbc.H2Profile.api._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import com.bayakala.funda.samples.SlickModels._
import scala.concurrent.ExecutionContext.Implicits.global

object ParallelLoading extends App {

  //assume two distinct db objects
  val db_a = Database.forConfig("h2db")
  //another db object
  val db_b = Database.forConfig("h2db")


  //create STATE table
  val actionCreateState = StateQuery.schema.create
  val futCreateState = db_a.run(actionCreateState).andThen {
    case Success(_) => println("State Table created successfully!")
    case Failure(e) => println(s"State Table may exist already! Error: ${e.getMessage}")
  }
  //would carry on even fail to create table
  Await.ready(futCreateState,Duration.Inf)

  //create COUNTY table
  val actionCreateCounty = CountyQuery.schema.create
  val futCreateCounty = db_a.run(actionCreateCounty).andThen {
    case Success(_) => println("County Table created successfully!")
    case Failure(e) => println(s"County Table may exist already! Error: ${e.getMessage}")
  }
  //would carry on even fail to create table
  Await.ready(futCreateCounty,Duration.Inf)

  //define query for extracting State names from AQMRPT
  val qryStates = AQMRPTQuery.map(_.state).distinct.sorted  //     .distinctOn(r => r)
  case class States(name: String) extends FDAROW
  implicit def toStates(row: String) = States(row)
  val stateLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toStates _)
  val statesStream = stateLoader.fda_typedStream(qryStates.result)(db_a)(64,64)()


  //define query for extracting County names from AQMRPT in separate chunks
  //query with state name >A and <K
  val qryCountiesA_K = AQMRPTQuery.filter(r => (r.state.toUpperCase > "A" &&
    r.state.toUpperCase < "K")).map(r => (r.state,r.county))
    .distinctOn(r => (r._1,r._2))
    .sortBy(r => (r._1,r._2))

  //query with state name >K and <P
  val qryCountiesK_P = AQMRPTQuery.filter(r => (r.state.toUpperCase > "K" &&
    r.state.toUpperCase < "P")).map(r => (r.state,r.county))
    .distinctOn(r => (r._1,r._2))
    .sortBy(r => (r._1,r._2))

  //query with state name >P
  val qryCountiesP_Z = AQMRPTQuery.filter(r => r.state.toUpperCase > "P")
    .map(r => (r.state,r.county))
    .distinctOn(r => (r._1,r._2))
    .sortBy(r => (r._1,r._2))

  case class Counties(state: String, name: String) extends FDAROW
  implicit def toCounties(row: (String,String)) = Counties(row._1,row._2)
  val countyLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toCounties _)
  //3 separate streams to extract county names from the same database table AQMRPT
  val countiesA_KStream = countyLoader.fda_typedStream(qryCountiesA_K.result)(db_b)(64,64)()
  val countiesK_PStream = countyLoader.fda_typedStream(qryCountiesK_P.result)(db_b)(64,64)()
  val countiesP_ZStream = countyLoader.fda_typedStream(qryCountiesP_Z.result)(db_b)(64,64)()

  //obtain a combined stream with parallel loading with max of 4 open computation
  val combinedStream = fda_par_load(statesStream,countiesA_KStream,countiesK_PStream,countiesP_ZStream)(4)


  //define separate rows for different actions
  case class StateActionRow(action: FDAAction) extends FDAROW
  case class CountyActionRow(action: FDAAction) extends FDAROW
  val actionRunner = FDAActionRunner(slick.jdbc.H2Profile)

  //user-task to catch rows of States type and transform them into db insert actions
  def processStates: FDAUserTask[FDAROW] = row => {
    row match {
      //catch states row and transform it into insert action
      case States(stateName) =>  //target row type
        println(s"State name: ${stateName}")
        val action = StateQuery += StateModel(0,stateName)
        fda_next(StateActionRow(action))
      case others@ _ => //pass other types to next user-defined-tasks
        fda_next(others)
    }
  }
  //user-task to catch rows of Counties type and transform them into db insert actions
  def processCounties: FDAUserTask[FDAROW] = row => {
    row match {
      //catch counties row and transform it into insert action
      case Counties(stateName,countyName) =>  //target row type
        println(s"County ${countyName} of ${stateName}")
        val action = CountyQuery += CountyModel(0,countyName+ " of "+stateName)
        fda_next(CountyActionRow(action))
      case others@ _ => //pass other types to next user-defined-tasks
        fda_next(others)
    }
  }

  //user-task to catch States insert action rows and run them
  def runStateAction: FDAUserTask[FDAROW] = row  => {
    row match {
      case StateActionRow(action) => //this is a state action row type
        println(s"runstate: ${action}")
        actionRunner.fda_execAction(action)(db_a)  //run this query with db_a context
        fda_skip
      case others@ _ => //otherwise pass alone to next user-defined-tasks
        fda_next(others)
    }
  }

  //user-task to catch Counties insert action rows and run them
  def runCountyAction: FDAUserTask[FDAROW] = row  => {
    row match {
      case CountyActionRow(action) => //this is a county action row type
        actionRunner.fda_execAction(action)(db_b)  //run this query with db_b context
        fda_skip
      case others@ _ => //otherwise pass alone to next user-defined-tasks
        fda_next(others)
    }
  }



  def showRows: FDAUserTask[FDAROW] = row => {
    row match {
      case States(nm) =>
        println("")
        println(s"State: $nm")
        println("************")
        fda_skip
      case Counties(s,c) =>
        println("")
        println(s"County: $c")
        println(s"state of $s")
        println("------------")
        fda_skip
      case _ => fda_skip
    }
  }

  combinedStream.appendTask(processStates)
    .appendTask(processCounties)
    .appendTask(runStateAction)
    .appendTask(runCountyAction)
    .startRun

}
