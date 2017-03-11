package com.bayakala.funda.fdarows
import com.bayakala.funda._
import scala.concurrent.duration._
import scala.concurrent.Await
import slick.driver.JdbcProfile

/**
  * loading of data rows and running of action rows
  */
trait FDARowOperator {

  /**
    * read view, an entire collection of data as result of Slick DBIO action
    * convert collection data type if required
    * run FDAAction row as Slick run(DBIOAction)
    * @param slickProfile  Slick jdbcprofile such as 'slick.driver.H2Driver'
    * @param convert       a defined implicit type conversion function.
    *                      from SOURCE type to TARGET type, set to null if not required
    * @tparam SOURCE       source type, result type of DBIOAction, most likely a tuple type
    * @tparam TARGET       final converted type, most likely a case class type
    */
  class FDAViewLoader[SOURCE, TARGET](slickProfile: JdbcProfile, convert: SOURCE => TARGET) {

    import slickProfile.api._

    /**
      * return collection of TARGET type data.
      * run slickAction and apply convert result from collection of SOURCE type
      * to collection of TARGET type
      * @example {{{
      *       val viewLoader = FDAViewLoader(slick.driver.H2Driver)(toTypedRow _)
      *       val dataSeq = viewLoader.fda_typedRows(aqmQuery.result)(db).toSeq
      * }}}
      * @param slickAction   Slick DBIOAction for query data results
      * @param slickDB       Slick database object
      * @param converter     just a measure to guarantee conversion function is defined
      *                      when this function is used there has to be a converter defined
      *                      implicitly in compile time
      * @return              a collection of TARGET type data
      */
    def fda_typedRows(slickAction: DBIO[Iterable[SOURCE]])(slickDB: Database)(
                      implicit converter: SOURCE => TARGET): Iterable[TARGET] = {
      Await.result(slickDB.run(slickAction), Duration.Inf).map(raw => convert(raw))
    }

    /**
      * load view without type conversion of result collection data type
      * @example {{{
      *       val viewLoader = FDAViewLoader(slick.driver.H2Driver)()
      *       val dataSeq = viewLoader.fda_plainRows(aqmQuery.result)(db).toSeq
      * }}}
      * @param slickAction   Slick DBIOAction for query data results
      * @param slickDB       Slick database object
      * @return              a collection of SOURCE type data
      */
    def fda_plainRows(slickAction: DBIO[Iterable[SOURCE]])(slickDB: Database): Iterable[SOURCE] = {
      Await.result(slickDB.run(slickAction), Duration.Inf)
    }
  }

  /**
    * constructor given slickprofile and converter function
    */
  object FDAViewLoader{
    /**
      * FDAViewLoader constructor
      * @example {{{
      *    val viewLoader = FDAViewLoader(slick.driver.H2Driver)(toTypedRow _)
      *    val untypedLoader = FDAViewLoader(slick.driver.H2Driver)()
      * }}}
      * @param slickProfile  Slick jdbcprofile such as 'slick.driver.H2Driver'
      * @param converter     type conversion function. from SOURCE type to TARGET type
      * @tparam SOURCE       source type, result type of DBIOAction, most likely a tuple type
      * @tparam TARGET       final converted type, most likely a case class type
      * @return              a new FDAViewLoader object
      */
    def apply[SOURCE, TARGET](slickProfile: JdbcProfile)(
      converter: SOURCE => TARGET = null): FDAViewLoader[SOURCE, TARGET] =
      new FDAViewLoader[SOURCE, TARGET](slickProfile, converter)
  }

  /**
    * method to run action rows of type FDAAction
    * @param slickProfile  Slick jdbcprofile such as 'slick.driver.H2Driver'
    */
  class FDAActionRunner(slickProfile: JdbcProfile) {

    import slickProfile.api._

    /**
      * run a Slick DBIOAction action.
      * @param action    a Slick DBIOAction
      * @param slickDB   Slick database object
      * @return          rows affected
      */
    def fda_execAction(action: FDAAction)(slickDB: Database): Int =
      Await.result(slickDB.run(action), Duration.Inf)
  }

  /**
    * constructor given jdbcprofile
    */
  object FDAActionRunner {
    /**
      * construct a FDAActionRunner object given slickProfile
      * @param slickProfile  Slick jdbcprofile such as 'slick.driver.H2Driver'
      * @return              a new FDAActionRunner object
      */
    def apply(slickProfile: JdbcProfile): FDAActionRunner = new FDAActionRunner(slickProfile)
  }
}

/**
  * for global imports
  */
object FDARowOperator extends FDARowOperator
