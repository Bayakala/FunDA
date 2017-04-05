# FunDA (Functional Data Access)  

*FunDA* is a functional database access library designed to supplement *FRM* (*Functional-Relational-Mapper*) tools like *Slick*.  
While *FRM* tools bring type safe *language-integrated-query* and flexible query composition as well as powerful functional programming paradigm to it's users, the main focus is on data object access and thus in ways short on strength for data persistence support such as data row traversal operations that are so common in *ORM*. This short-fall plus a brand new functional programming style also make many *OOP* programmers from the *ORM* world using *FRM* quite uncomfortable or even somewhat awkward. On top of bringing back the familiar recordset operations to support data row processing, *FunDA* also adds in explicit parallel data processing capabilities as well as a simple user-defined programming model to lower requirements for functional programming skills such that with little getting-used-to a traditional *OOP* programmer could handle *FRM* so to make *FunDA* as a much practically productive tool.
The core of *FunDA* is implemented with *scalaz-streams-fs2*. *FunDA* can be depicted as a workflow pipe with a sequence of work-nodes where user-defined data processing tasks could be plugged in. *FunDA* is implemented as a forward-only stream of rows representing pure data or query actions. User-defined-tasks at a work-node can intercept rows and run some processes with the context of each row. And these user-defined-tasks can be run parallelly through *FunDA*'s Parallelism.  
A typical *FunDA* program consists of a **source** and many **user-defined-tasks**  as follows:  

```
val streamSource = streamLoader.fda_typedStream(albumsInfo.result)(db)(512, 128)()

streamSource.appendTask(transformData).appendTask(runActionQuery).appendTask(showResults).startRun
```
where "streamSource" is a *FunDA* stream **source** produced by loading data from database, and "transformData", "runActionQuery" and "showResults" are all user-defined-tasks each responsible to achieve some minimal distinctive effect. As the unique flavor of functional programming, these are functional combinators and can be composed in a specific order to perform a much bigger and complexed task. From the semantics of the *FunDA* program above we can make a wild guess that "transformData" would transform each data row to query actions and these query actions are executed by "runActionQuery" at the next work-node.  
#### how to use  

*FunDA* artifacts are currently published on Bintray. Add following in your build.sbt:  

```
resolvers +=  Resolver.bintrayRepo("bayakala","maven")
libraryDependencies += "com.bayakala" %% "funda" % "1.0.0-RC-01" withSources() withJavadoc()


```  
for your info, *FunDA* allready includes the following dependencies:  

```
   libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % "3.2.0",
      "com.h2database" % "h2" % "1.4.191",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0",
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "co.fs2" %% "fs2-core" % "0.9.4",
      "co.fs2" %% "fs2-io" % "0.9.4",
      "com.typesafe.play" % "play-iteratees-reactive-streams_2.11" % "2.6.0"
    )

```  
**Remarks:** users should set up their own *Slick* database configuration file application.conf in the resources directory.
#### to run the examples
There is a sample applicaion "funda-demo" located on github here: [www.github.com/bayakala/funda-demo](http://www.github.com/bayakala/funda-demo/) . It includes sample data located under resources/testdata/ and it is a bare cvs file. Import this file in your database before you run the examples. The examples should be run in the following order:  

```
1. StrongTypedRows.scala  
2. UserDefinedTasks.scala
3. ParallelLoading.scala
4. ParallelTasks.scala
5. ParallelExecution.scala
6. ExceptionsAndFinalizers.scala
```
*download and try it. good luck and have fun!*
## The Principles  

*FunDA*'s workflow *FDAPipeLine* is a *scalaz-streams-fs2* and therefore is a *free-monad*. It is highly composable:  

```
val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)      
val source = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()
val stream = source.filter{r => r.year > "1999"}.take(3).appendTask(showRecord)

stream.startRun
```
as demonstrated above, we can compose stream anyway we want before **startRun**
### FunDA stream (The progrm)
#####  strong-typed rows
As mentioned above, FunDA programs are just composition of a **source** and a string of **user-defined-tasks** as a stream with data produced by **source** as rows. To facilitate stream operations we must convert data loaded from database into strong-typed rows. A practical case is that *Slick* usually returns query results in a collection of tuples. Thus we must take an extra step to convert them into user defined strong-typed case classes. 
The following code snippet demonstrates such conversion:

```
// aqmQuery.result returns Seq[(String,String,String,String)]
  val aqmQuery = aqmraw.map {r => (r.year,r.state,r.county,r.value)}
// user designed strong typed resultset type. must extend FDAROW
  case class TypedRow(year: String, state: String, county: String, value: String) extends FDAROW
// strong typed resultset conversion function. declared implicit to remind during compilation
  implicit def toTypedRow(row: (String,String,String,String)): TypedRow =
    TypedRow(row._1,row._2,row._3,row._4)  

// loader to read from database and convert result collection to strong typed collection
  val viewLoader = FDAViewLoader(slick.jdbc.H2Profile)(toTypedRow _)
  val dataSeq = viewLoader.fda_typedRows(aqmQuery.result)(db).toSeq
// turn Seq collection into FunDA stream with strong-typed rows
  val aqmStream: FDAPipeLine[TypedRow] =  fda_staticSource(dataSeq)()    
    
``` 
##### static view and dynamic streaming sources  
Static sources or views are data structures completely loaded into memory after returning from running a query. Stream sources are data streams as returned query results that are *reactive-streams* conformant. In other words stream sources are backend cached and motivated by back-pressure. *FunDA* provides functions to produce sources. The following is a demonstration of static view producing:  

```
// loader to read from database and convert result collection to strong typed collection
  val viewLoader = FDAViewLoader(slick.jdbc.H2Profile)(toTypedRow _)
  val dataSeq = viewLoader.fda_typedRows(aqmQuery.result)(db).toSeq
// turn Seq collection into FunDA stream with strong-typed rows
  val aqmView: FDAPipeLine[TypedRow] =  fda_staticSource(dataSeq)()    

``` 
stream source can be constructed as follows:  

```
// strong typed source is also possible with Slick data streaming
  val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)      
  val aqmStream: FDAPipeLine[TypedRow] = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()
```  
as demonstrated above, both static collections and dynamic data streams can be transform into strong-typed-row sources.  

### Control data flow  

Flow of rows in *FunDA* streams are controlled inside user-defined-tasks, in which a row is received from upstream and zero or one or more rows could be passed downstream. This means additional new rows could be constructed instantly and passed downstream inside these user-defined-tasks which makes *FunDA* logics much more flexible and powerful. Passing no row in a receive-send-loop is represented by skip. User could also halt stream by passing an end-of-stream signal downstream inside these user-defined-tasks. The following are some code samples:  

```
   user-defined-task type is defined as follows:
   type FDAUserTask[ROW] = (ROW) => (Option[List[ROW]])
    /* user define function to be performed at a FDAWorkNode
    * given a row from upstream, return Option[List[ROW]] as follows:
    *    fda_skip  -> Some(Nil)           : skip sending the current row
    *    fda_next  -> Some(List(r1,r2...)): send r1,r2... downstream
    *    fda_break -> None                : halt stream, end of process
    * @tparam ROW   type of row
    */
  
// a example of user-defined-task  
  def dancing: FDAUserTask[FDAROW] = row => {
    row match {
      case qmr: TypedRow =>
        qmr.year match {
          case a if a < 1960 =>
          // pass downstream untouched
           fda_next(qmr)
          case b if b < 1970 => 
          // transform row
           fda_next(qmr.copy(year = 1970))
          case c if c < 1980 =>
          // pass alone with a new row. TypedRow is a case class
           fda_next(List(qmr,TypedRow(qmr.stare,"countyQQ",0.3,1980)))
          case d if d < 2000 =>
          // do not process this row
           fda_skip
          case _ =>
          // stop stream 
           fda_break    
        }
      // encounter unknown row type, break out  
      case _ => fda_break
    }
  }
```

### defining user-defined-task  
  
As a functional stream, It seems that some of the data access and processing in *FunDA* could be achieved in some pure functional ways like the following:  

```
fdaStream.map(row => transformData(row)).map(action => runQueryAction(action))
```
unfortunately the fact is pure stream combinators lack powerful and flexible flow control abilities that are so crucial for processing stream elements, therefore **user-defined-task** is introduced as a programming model to deal with the situation.
User-defined-tasks are functional combinators designed by users each to achieve a single minimal task, and a much more complexed final task could be assembled by composing many of these tiny tasks in a specific order and then ***startRun***. The signature of *FDAUserTask[ROW]* is as follows:  

```
  type FDAUserTask[ROW] = (ROW) => (Option[List[ROW]])

```
the above reads: an **user-defined-task** takes a row as input, use or transform it, and as a way of flow control, signify the state of next step of stream by returning **Option[List[ROW]]** as a result of execution of fda_next, fda_skip or fda_break. With the strong-typed-row requirement in place, the involved row types must extend from **FDAROW** and could be either a data-row or action-row.  
##### types of rows
*FunDA* streams are strong-typed, all rows must extend **FDAROW**. There are several categories of rows:

* data-row: any case class extending **FDAROW** with parameters representing fields:  
   **case class TypedRow(year: Int, state: String, value: Int) extends FDAROW**  
* action-row: case class extending **FDAROW** with a **Slick DBIOAction** wrapped inside the parameter as follows:   
   **case class FDAActionRow(action: FDAAction) extends FDAROW**
   sometimes we need to target an action row to be run in different database context. In that case we can just define any case class and extend **FDAROW**:  
   **case class MyActionRow(action: FDAAction) extends FDAROW**  
* error-row: case class extending **FDAROW** with a caught Exception object wrapped inside its parameter.   
   **case class FDAErrorRow(e: Exception) extends FDAROW**  
   user can define their own error row for different exceptions as long as they extend **FDAROW**:  
   **case class MyErrorRow(msg: String, e: Exception) extends FDAROW**
* null-row: a signal object used to represent EOS(end-of-stream):  
   **case object FDANullRow extends FDAROW**

##### standard-operation-procedures
User-defined-tasks have standard operation procedures as the following: 

1. determine row type by pattern-matching
2. use row fields to perform data processing and transformation
3. control flow of rows downstream 

the following are samples of a few different purposed user-defined-tasks:  

```
   //strong typed row type. must extend FDAROW 
  case class StateRow(state: String) extends FDAROW
  
  //a logging task. show name and pass row untouched downstream
  def showState: FDAUserTask[FDAROW] = row => {
    row match {
      case StateRow(sname) =>  //this is my row
        println(s"Name of state is：$sname")
        fda_next(row)
      case _ => fda_skip    //not my row, do not pass it
    }
  }
```

```
//a filter and processing task.
//filter out rows with inconvertible value strings and out of ranged value and year
  def filterRows: FDAUserTask[FDAROW] = row => {
    row match {
      case r: AQMRaw => {  //this is the correct row
        try {  //process this row and catch exceptions
          val yr = r.year.toInt
          val v = r.value.toInt
          val vlu = if ( v > 10  ) 10 else v  //max value allowed
          //construct a new row 
          val data = AQMRPTModel(0,r.mid.toInt,r.state,r.county,yr,vlu,0,true)
          if ((yr > 1960 && yr < 2018))  //filtering
            fda_next(data)   //this row ok. pass downstream
          else
            fda_skip    //filter out this row
        } catch {
          case e: Exception =>
            fda_next(FDAErrorRow(e))   //pass the caught exception as a row downstream
        }
      }
      case _ => fda_skip   //wrong type, skip
    }
  }
```  

```
//a row transformation task
//transform data to action for later execution
  def toAction: FDAUserTask[FDAROW] = row => {
    row match {
      case r: AQMRPTModel =>  //this is my row
        val queryAction = AQMRPTQuery += r  //slick action
        fda_next(FDAActionRow(queryAction))
      case _ => fda_skip
    }
  }
```

```
//a query action runner task
//get a query runner and an action task
  val actionRunner = FDAActionRunner(slick.jdbc.H2Profile)
  def runActionRow: FDAUserTask[FDAROW] = action => {
    action match {
      case FDAActionRow(q) =>   //this is a query action row
         actionRunner.fda_execAction(q)(db)  //run it
         fda_skip
      case other@_ => fda_next(other) //don't touch it, just pass down
      //someone else downstream could process it
    }
  }

```

to run many task as a whole, we compose them and **startRun**:  
  
```  
//compose the program
  val streamAllTasks =  streamAQMRaw.appendTask(filterRows)
    .appendTask(toAction)
    .appendTask(runActionRow)
//run program
  streamToRun.startRun
    
```
##### aggregation  
In stream style processing, many times we need to aggregate over rows, this is where **user-aggregate-task** is designed to fit in. An **user-aggregate-task** has the following signature:  
```
 type FDAAggrTask[AGGR,ROW] = (AGGR,ROW) => (AGGR,Option[List[ROW]])
```  
*AGGR* could be any user defined type to represent the state of aggregation. From the above type signature, we can see it is a typical functional style function represented by input a state and output new state. The following is an example of **user-aggregate-task**:  

```
//define a structure to represent aggregator type
  case class Accu(state: String, county: String, year: Int, count: Int, sumOfValue: Int)

//user defined aggregation task. only pass aggregated row downstream
  def countingAverage: FDAAggrTask[Accu,FDAROW] = (accu,row) => {
    row match {
      case aqmr: AQMRPTModel =>  //this is the target row type
        if (accu.state == "" || (aqmr.state == accu.state && aqmr.year == accu.year))
          //same condition: inc count and add sum, no need to pass row downstream
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

```

the following demonstrates how it is executed:  

```
  aqmrStream.aggregateTask(Accu("","",0,0,0),countingAverage)
    .appendTask(toAction)
    .appendTask(runActionRow)
    .startRun

```  
"aqmrStream" is a **source** with rows to be aggregated.  
### Running programs inside user-defined-task  
A *FunDA* program consist of a **source** and multiple **user-defined-tasks**. It is possible to execute a *FunDA* program inside these *user_defined-tasks*. This means we have to call **startRun** inside the *user-defined-task* and some effect would inevitably be produced rending the calling *user-defined-task* impure. A complete example of *FunDA* program inside a *user-defined-task* is given below:  

```
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
 
```  
"getStateID" is a *user-defined-function* in which function "getid" is physically executed. Because "getid" is local, we are still confident to use the calling *user-defined-function* in composition with other combinators as following:  

```
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
```  
in this case "getStateID" is called within another *user-defined-task*.
### Parallel Processing  
*FunDA* borrows its parallelism capabilities from *scalaz-streams-fs2*. There are two areas of parallel data processing application:  


 * parallel loading of multiple **sources**  
 * parallel execution of a single **user-defined-task**  
 
##### parallel loading  
Parallel loading of many sources is achieved by calling function **fda-par-load** provided in *FunDA*. These sources could be constructed from tables on many separate database-servers or by spliting huge data tables into smaller un-repeated data chunks like the following:  

```
  //define query for extracting State names from AQMRPT
  val qryStates = AQMRPTQuery.map(_.state).distinct.sorted  
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
  val countiesA_KStream: FDAPipeLine[County] = countyLoader.fda_typedStream(qryCountiesA_K.result)(db_b)(64,64)()
  val countiesK_PStream: FDAPipeLine[County] = countyLoader.fda_typedStream(qryCountiesK_P.result)(db_b)(64,64)()
  val countiesP_ZStream: FDAPipeLine[County] = countyLoader.fda_typedStream(qryCountiesP_Z.result)(db_b)(64,64)()

``` 
once these **sources** are all constructed, we then load them parallelly:  

```
  //obtain a combined stream with parallel loading with max of 4 open computation
  val combinedStream: FDAPipeLine[FDAROW] = fda_par_load(statesStream,countiesA_KStream,countiesK_PStream,countiesP_ZStream)(4)

``` 
doing parallel loading would most likely to produce a stream with multiple types of rows, in the above case **States** and **Counties** represent two different types of rows respectively. Therefore *user-defined-tasks* each targeting different type of row are designed to handle rows of its target type, like the following:  

```
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
```
###### parallel loading a stream of sources  
The above demonstration of parallel loading started with known number of sources. This is especially convenient for users to manually arrange  sources of different row types in a parallel loading operation. But, when a list of sources is itself a stream, then to parallelly load the stream of sources we need to first convert the stream into **FDAParSource** over a **FDASourceLoader** function as follows:  

```
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
  
  //get parallel source constructor
  val parSource: FDAParSource = yearStream.toParSource(loadRowsByYear)
  
```  
the following demonstrates loading of this parallel source:  

```
  //produce a stream from parallel source
  val stream: FDAPipeLine[FDAROW] = fda_par_source(parSource)(4)
```
**fda_par_source** is actually a parallel execution function analogous to **fda_runPar** which is described in the following section.
##### parallel execution  
*FunDA* provides a function **fda_runPar** as a parallel task runner. A parallel task has the following signature:  

```
/** Parallel task type
    * stream of streams type for parallel running user action
    * use stream.toPar to convert from FDAUserTask
    */
  type FDAParTask = Stream[Task,Stream[Task,Option[List[FDAROW]]]]

```  
and a **FDAUserTask** can be converted to **FDAParTask** as bellow:  

```
AQMRPTStream.toPar(getIdsThenInsertAction)
```  
where "AQMRPTStream" is a *FunDA* **source** and **toPar** is its method to turn "getIdsThenInsertAction" into a parallel task of many instances running in different threads. The principle of parallel execution is by scrambling rows in a single input stream into several un-ordered streams as inputs to many instances of a single task running parallelly in different threads. A **FDAParTask** requires a special runner to be carried out as shown below:  

```
fda_runPar(AQMRPTStream.toPar(getIdsThenInsertAction))(8)
```  
**fda_runPar** has a signature as follows:  

```
def fda_runPar(parTask: FDAParTask)(maxOpen: Int): FDAPipleLine[FDAROW]

```  
**maxOpen** designates the maximum number of open computations or degree of parallelism and the actual number of open computations depends on a number of factors including CPU cores, size of thread-pool and no more than user suggested maximum number of open computations. Thread-pool can be adjusted from default values by declaring implicit instance of Strategy:  

```
      implicit val strategy = Strategy.fromCachedDaemonPool("cachedPool")
//      implicit val strategy = Strategy.fromFixedDaemonPool(6)

```  
the actual performance of parallelism requires thorough tuning of thread-pool strategies with respect to number of CPU cores. For whatever configurations, the performance gain through parallelism over single-thread task demonstrates great significance.  A complete composition of parallel loading and parallel execution has the following model:   

```
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

```
##### remarks when writing parallel processing programs  
According to experiments, **FunDA** concurrent combinators are quite sensitive to thread management. The **maxOpen** parameters of both **fda_runPar** and **fda_par_source** in the examples given above had to be tuned to avoid thread contention and process-hang. The example used **HikariCP** with parameters set below:  

```
h2db {
    url = "jdbc:h2:tcp://localhost/~/slickdemo;mv_store=false"
    driver = "org.h2.Driver"
    connectionPool = HikariCP
    numThreads = 48
    maxConnections = 48
    minConnections = 12
    keepAliveConnection = true
}

```
### Exceptions handling and Finalizers  
**FunDA** provides a mechanism that guarantees a **finalizer** be called upon termination of the stream, no matter if it is naturally end of stream or break-out caused by interruptions or exceptions. **Finalizers** are in fact call-back-functions hooked-up to a *FunDA* program during source construction, like the following:  

```
  val view = fda_staticSource(stateSeq)(println("***Finally*** the end of view!!!"))
  val stream = streamLoader.fda_typedStream(StateQuery.result)(db)(64,64)(println("***Finally*** the end of stream!!!"))

```  
exceptions can be caught by **onError** call-backs that are hooked-up at the **very end** of **FunDA** stream in order to catch exceptions from all work-nodes as follows:  

```
   val v = viewState.appendTask(errorRow).appendTask(trackRows)
   val v1 = v.onError {case e: Exception => println(s"Caught Error in viewState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
   v1.startRun

   val s = streamState.appendTask(errorRow).appendTask(trackRows)
   val s1 = s.onError {case e: Exception => println(s"Caught Error in streamState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
   s1.startRun

```  
#####user defined exceptions  
Sometimes we wish to watch some particular events and take corresponding actions when they take place. This can be achieved by user-defined-exceptions. User-defined-exceptions are special rows extending from **FDAROW** that can be caught by pattern matching. The following is an example of user-defined-exception and its handling:  

```
  case class DivideZeroError(msg: String, e: Exception) extends FDAROW
  def catchError: FDAUserTask[FDAROW] = row => {
    row match {
      case StateModel(id,name) =>
        try {
          val idx = id / (id - 3)
          fda_next(StateModel(idx, name))
        } catch {
          case e: Exception => //pass an error row
            fda_next(DivideZeroError(s"Divide by zero exception at ${id}",e))
        }
      case m@_ => fda_next(m)
    }
  }
  
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

  val s = streamState.take(5).appendTask(catchError).appendTask(trackRows)
  val s1 = s.onError {case e: Exception => println(s"Caught Error in streamState!!![${e.getMessage}]"); fda_appendRow(FDANullRow)}
  s1.startRun

```