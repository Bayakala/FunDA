#FunDA (Functional Data Access)
*FunDA* is a functional database access library designed to supplement *FRM* (Functional-Relational-Mapper) tools like *Slick*.  
While *FRM* tools brings type safe language-integrated-query and flexible query composition as well as powerful functional programming paradigm to it's users, the main focus is on data object access and thus short on ability for data persistence support such as data row traversal operations that is so common in *ORM*. This and a brand new functional programming style also make many OOP programmers from the *ORM* world using *FRM* quite uncomfortable or even somewhat awkward. On top of introducing back the familiar recordset operation support, *FunDA* also adds functionalities to support explicit parallel data processing as well as a simple user-defined programming model to lower requirements for functional programming skills such that with little getting-used-to a traditional *OOP* programmer could handle *FunDA* with ease.  
*FunDA* is anologous to *scalaz-streams-fs2*. It is like a workflow pipe with a sequence of any number of work-nodes where user-defined data processing tasks are executed. *FunDA* adopts fs2 to implement a forward-only stream flow of data row or query commands. User-defined tasks at a work-node can intercept rows and process data or run query commands and then perhaps pass some transformed row to the next work-node. Parallel processing abilities are achieved through non-determinism handling of fs2.  
##The Principles
*FunDA*'s workflow *FDAPipeLine* is a *fs2-stream* and therefore is a *free-monad* type. It is highly composible:  

```
val streamLoader = FDAStreamLoader(slick.driver.H2Driver)(toTypedRow _)      
val source = streamLoader.fda_typedStream(aqmQuery.result)(db)(10.seconds,512,512)()()
val stream = source.filter{r => r.year > "1999"}.take(3).appendTask(showRecord)

stream.startRun
```
as demostrated above, we can compose stream anyway we want before **startRun**
