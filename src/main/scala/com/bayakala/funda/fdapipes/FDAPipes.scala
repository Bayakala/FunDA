package com.bayakala.funda.fdapipes
import com.bayakala.funda._

trait FDAPipes {
  /**
    * returns state of next worknode. using fs2 Handle of Pull object,
    * take the next element and apply function task and determine new state of stream
    * @param task   user defined function: ROW => Option[List[ROW]]
    *               returns an Option[List[ROW]]] value signifying movement downstream
    *               as follows:
    *                  Some(Nil)           : skip sending the current row
    *                  Some(List(r1,r2...)): send r1,r2... downstream
    *                  None                : halt stream, end of process
    * @tparam ROW   row type: FDAROW or FDAActionROW
    * @return       new state of stream
    */
  def fda_execUserTask[ROW](task: FDAUserTask[ROW]): FDAWorkNode[ROW] = FDANodes.fda_execUserTask(task)
  /**
    * returns state of next worknode and some aggregation defined inside user function.
    * execute user defined function with internal aggregation mechanism by means of
    * functional state transition style of passing in state and return new state.
    * take in current aggregation and next row, apply user function on both
    * and determine new state of stream
    * @param aggr    user selected type of aggregation such as Int, (Int,Int) ...
    * @param task    user defined function: (AGGR,ROW) => (AGGR,Option[List[ROW]])
    *                take in current aggregation and row,
    *                and return new aggregation and Option[List[ROW]] with meaning of:
    *                  Some(Nil)           : skip sending the current row
    *                  Some(List(r1,r2...)): send r1,r2... downstream
    *                  None                : halt stream, end of process
    * @tparam AGGR   type of aggr
    * @tparam ROW    type of row
    * @return        new state of stream
    */
  def fda_aggregateTask[AGGR,ROW](aggr: AGGR, task: FDAAggrTask[AGGR,ROW]): FDAWorkNode[ROW] = FDANodes.fda_aggregate(aggr,task)
  /**
    * send row to Pull
    * @param row     row to be sent
    * @tparam ROW    type of row
    * @return        new state of Pull
    */
  def fda_pushRow[ROW](row: ROW) = FDAJoints.fda_pushRow(row)
  /**
    * send list of rows to Pull
    * @param rows   list of rows to be sent
    * @tparam ROW   type of target row
    * @return       new state of Pull
    */
  def fda_pushRows[ROW](rows: List[ROW]) = FDAJoints.fda_pushRows(rows)
  /**
    * end output to Pull
    * @return  end of Pull
    */
  def fda_halt = FDAJoints.fda_halt
  /**
    * skip current row by sending a Some(Nil)
    * @return   Some(Nil) to signify skipping current row
    */
  def fda_skip[ROW] = FDAValves.fda_skip[ROW]
  /**
    * send row downstream
    * @param row    target row
    * @tparam ROW   type of target row
    * @return       a single row to be sent downstream
    */
  def fda_next[ROW](row: ROW) = FDAValves.fda_next(row)
  /**
    * send a list of rows downstream in a chunk
    * @param lr     list of rows to be sent
    * @tparam ROW   type of target row
    * @return       a list of many rows
    */
  def fda_next[ROW](lr: List[ROW]) = FDAValves.fda_next(lr)
  /**
    * halt the current stream
    * @return  a None indicating end of current stream process
    */
  def fda_break = FDAValves.fda_break
}

/**
  * for global imports
  */
object FDAPipes extends FDAPipes