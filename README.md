# FunDA (Functional Data Access)  

*FunDA* is a functional database access library designed to supplement *FRM* (Functional-Relational-Mapper) tools like *Slick*.  
While *FRM* tools bring type safe language-integrated-query and flexible query composition as well as powerful functional programming paradigm to it's users, the main focus is on data object access and thus short on ability for data persistence support such as data row traversal operations that is so common in *ORM*. This and a brand new functional programming style also make many OOP programmers from the *ORM* world using *FRM* quite uncomfortable or even somewhat awkward. On top of introducing back the familiar recordset operation support, *FunDA* also adds functionalities to support explicit parallel data processing as well as a simple user-defined programming model to lower requirements for functional programming skills such that with little getting-used-to a traditional *OOP* programmer could handle *FunDA* with ease.  
*FunDA* is anologous to *scalaz-streams-fs2*. It is like a workflow pipe with a sequence of any number of work-nodes where user-defined data processing tasks are executed. *FunDA* adopts fs2 to implement a forward-only stream flow of data row or query commands. User-defined tasks at a work-node can intercept rows and process data or run query commands and then perhaps pass some transformed row to the next work-node. Parallel processing abilities are achieved through non-determinism handling of fs2.  
A typical *FunDA* program is as follows:  

```
val streamSource = streamLoader.fda_typedStream(albumsInfo.result)(db)(512, 128)()

streamSource.appendTask(transformData).appendTask(runActionQuery).appendTask(showResults).startRun
```
where streamSource is a *FunDA* stream produced by loading from database. And transformData, runActionQuery and showResults are user-defined-tasks each resposible to achieve some distinctive effect and when composed in a specific order togather they perform a much complexed bigger job. From the semantics of the *FunDA* program above we can make a wild guess that user-defined-task transformData would transform data to query actions and pass them down to runActionQuery for execution.  

## The Principles  

*FunDA*'s workflow *FDAPipeLine* is a *fs2-stream* and therefore is a *free-monad* type. It is highly composible:  

```
val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)      
val source = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()()
val stream = source.filter{r => r.year > "1999"}.take(3).appendTask(showRecord)

stream.startRun
```
as demostrated above, we can compose stream anyway we want before **startRun**
### producing source with strong-typed rows  
  
*FunDA* begins streaming by loading from database through *Slick*. *Slick* as a typical *FRM* returns collection with tuples as result of running a query. To facilitate data accessing we must transform tuples into strong-typed rows like case classes. The following code snippet demostrates how *FunDA* produces data source with strong-typed rows:  

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
  val aqmStream =  fda_staticSource(dataSeq)()()  

// strong typed source is also possible with Slick data streaming
  val streamLoader = FDAStreamLoader(slick.jdbc.H2Profile)(toTypedRow _)      
  val source = streamLoader.fda_typedStream(aqmQuery.result)(db)(512,512)()()
  
    
```   
as demonstrated above, both static collections and dynamic data-stream can be transform into strong-typed-row sources.  

### Control data flow  

*FunDA* stream flow of rows is controled inside user-defined-tasks, in which a row is received from upstream and zero or one or more rows could be passed downstream. This means additional new rows could be created and passed downstream inside these user-defined-tasks as well as skip a row when passing no row at current loop. And user could also halt stream by passing an end-of-stream signal downstream inside these user-defined-tasks. Code samples are as follows:  

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
  
As a functional stream, It seems that some of the data access and processing in *FunDA* could be achieved in some pure functional ways such as:  

```
fdaStream.map(row => transformData(row)).map(action => runQueryAction(action))
```
unfortunately the fact is pure stream combinators lack powerful and flexible flow control abilities that are so crucial for processing stream elements and therefore user-defined-task is introduced to cope with the situation.
user-defined-tasks are functional combinators designed by users each to achieve a single task. And a much more complexed final task could be achieved by composing many of these tiny tasks in a specific order and then ***startRun***. The signature of FDAUserTask[ROW] is as follows:  

```
  type FDAUserTask[ROW] = (ROW) => (Option[List[ROW]])

```
an user-defined-task takes a row as input, use or transform it, and as a way of flow control, signify the state of next step of stream by returning ***Option[List[ROW]]*** as a result of execution of fda_next, fda_skip or fda_break. The input row must be extended from FDAROW and could be either a data-row or action-row.  
##### Strong-typed-rows
FunDA streams are strong-typed, all rows must extend FDAROW. There are several categories of rows:

* data-row: any case class extending FDAROW with parameters representing fields:  
   **case class TypedRow(year: Int, state: String, value: Int) extends FDAROW**  
* action-row: case class extending FDAROW with a **Slick DBIOAction** wrapped inside the parameter as follows:   
   **case class FDAActionRow(action: FDAAction) extends FDAROW**  
* error-row: case class extending FDAROW with a caught Execption object wrapped inside its parameter.   
   **case class FDAErrorRow(e: Exception) extends FDAROW**  
* null-row: a signal used to represend EOS(end-of-stream):  
   **case object FDANullRow extends FDAROW**

##### standard-operation-procedures
User-defined-tasks have standard operation procedures as the following: 

1. determine row type by pattern-matching
2. use row fields to perform data processing
3. control flow of rows downstream 

the following are listings of a few deferent user-defined-tasks:  

```
   //strong typed row type. must extend FDAROW 
  case class StateRow(state: String) extends FDAROW
  
  //a logging task. show name and pass row untouched donwstream
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
            fda_next(FDAErrorRow(e))   //pass the caught excption as a row downstream
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

To run many task as a whole, we composs them and **startRun**:  
  
```  
//compose the program
  val streamAllTasks =  streamAQMRaw.appendTask(filterRows)
    .appendTask(toAction)
    .appendTask(runActionRow)
//run program
  streamToRun.startRun
    
```