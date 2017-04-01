package com.bayakala.funda.fdapars

import fs2._
import com.bayakala.funda._
import com.bayakala.funda.fdapipes.FDAJoints._
/** operation methods for parallel computation */
trait FDAPars {
  /**
    * return a single stream of combined data rows.
    * reading from a list of many streams defined in sources concurrently.
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