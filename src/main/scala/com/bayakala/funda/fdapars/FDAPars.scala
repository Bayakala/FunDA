package com.bayakala.funda.fdapars

import fs2._
import com.bayakala.funda._
import com.bayakala.funda.fdapipes.FDAJoints._
/** operation methods for parallel computation */
trait FDAPars {
  /**
    * return a single stream of combined data rows.
    * reading from a list of many streams defined in sources concurrently.
    * @example {{{
    *    //3 separate streams to extract county names from the same database table AQMRPT
    *    val countiesA_KStream = countyLoader.fda_typedStream(qryCountiesA_K.result)(db_b)(64,64)()
    *    val countiesK_PStream = countyLoader.fda_typedStream(qryCountiesK_P.result)(db_b)(64,64)()
    *    val countiesP_ZStream = countyLoader.fda_typedStream(qryCountiesP_Z.result)(db_b)(64,64)()
    *
    *    //obtain a combined stream with parallel loading with max of 4 open computation
    *    val combinedStream = fda_par_load(statesStream,countiesA_KStream,countiesK_PStream,countiesP_ZStream)(4)
    * }}}
    * @param sources     list of many individual streams
    * @param maxOpen     maximum number of concurrently open computation.
    *                    could be a number near and less than the cpu cores
    * @return            a combined data stream with nondeterministic element order
    */
  def fda_par_load(sources: FDAPipeLine[FDAROW]*)(maxOpen: Int): FDAPipeLine[FDAROW] = {
     concurrent.join(maxOpen)(Stream(sources: _*))
  }
  /**
    * run user defined function on a single stream many ways in parallel.
    * return a single stream as result of user function application.
    * @example {{{
    *    //turn getIdsThenInsertAction into parallel task
    *    val parTasks = source.toPar(getIdsThenInsertAction)
    *    //runPar to produce a new stream
    *    val actionStream =fda_runPar(parTasks)(4)
    * }}}
    * @param parTask     user defined function turned into par style by toPar
    * @param maxOpen     maximum number of concurrently open computation.
    *                    could be a number near and less than the cpu cores
    * @return            a single stream with nondeterministic element order
    */
  def fda_runPar(parTask: FDAParTask)(maxOpen: Int) =
    concurrent.join(maxOpen)(parTask).through(fda_afterPar)

  /**
    * load many sources in parallel.
    * return a single combined stream with non-deterministic row order.
    * @example {{{
    *  //loading rows with year yr
    *  def loadRowsInYear(yr: Int) = {
    *    //a new query
    *    val query = AQMRPTQuery.filter(row => row.year === yr)
    *    //reuse same loader
    *    AQMRPTLoader.fda_typedStream(query.result)(db)(256, 256)(println(s"End of stream ${yr}!!!!!!"))
    *  }
    *
    *  //loading rows by year
    *  def loadRowsByYear: FDASourceLoader = row => {
    *   row match {
    *    case Years(y) => loadRowsInYear(y) //produce stream of the year
    *    case _ => fda_appendRow(FDANullRow)
    *   }
    *  }
    *
    *  //get parallel source constructor
    *  val parSource = yearStream.toParSource(loadRowsByYear)
    *  //produce a stream from parallel sources
    *  val source = fda_par_source(parSource)(4)
    * }}}
    * @param parSource   user defined source constructor turned into
    *                    par style by toParSource
    * @param maxOpen     maximum number of concurrently open computation.
    *                    could be a number near and less than the cpu cores
    * @return            a single stream with nondeterministic element order
    */
  def fda_par_source(parSource: FDAParSource)(maxOpen: Int) =
    concurrent.join(maxOpen)(parSource)


/** flatten the Option[List[FDAROW]]] type returned by fda_runPar to plain FDAROW */
  private def fda_afterPar: Pipe[Task,Option[List[FDAROW]],FDAROW] = {
    def go: Handle[Task,Option[List[FDAROW]]] => FDAPipeJoint[FDAROW] = h => {
      h.receive1Option {
        case Some((r, h)) => r match {
          case Some(xr) =>
            fda_pushRows(xr) >> go(h)
          case None => go(h)
        }
        case None => fda_halt
      }
    }
    in => in.pull(go)
  }

}

/**
  * for global imports
  */
object FDAPars extends FDAPars