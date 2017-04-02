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
  private def fda_execUserTask[ROW](task: FDAUserTask[ROW]): FDAWorkNode[ROW] = FDANodes.fda_execUserTask(task)
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
  private def fda_aggregateTask[AGGR,ROW](aggr: AGGR, task: FDAAggrTask[AGGR,ROW]): FDAWorkNode[ROW] = FDANodes.fda_aggregate(aggr,task)
  /**
    * send row to Pull
    * @param row     row to be sent
    * @tparam ROW    type of row
    * @return        new state of Pull
    */
  private def fda_pushRow[ROW](row: ROW): FDAPipeJoint[ROW] = FDAJoints.fda_pushRow(row)
  /**
    * send list of rows to Pull
    * @param rows   list of rows to be sent
    * @tparam ROW   type of target row
    * @return       new state of Pull
    */
  private def fda_pushRows[ROW](rows: List[ROW]): FDAPipeJoint[ROW] = FDAJoints.fda_pushRows(rows)
  /**
    * end output to Pull
    * @return  end of Pull
    */
  private def fda_halt = FDAJoints.fda_halt
  /**
    * skip current row by sending a Some(Nil)
    * @example {{{
    *    def trackRows: FDAUserTask[FDAROW] = row => {
    *      row match {
    *        case m@StateModel(id,name) =>
    *          println(s"State: $id $name")
    *          println( "----------------")
    *          fda_next(m)
    *        case DivideZeroError(msg, e) => //error row
    *          println(s"***Error:$msg***")
    *          fda_skip
    *        case m@_ => fda_next(m)
    *       }
    *     }
    * }}}
    * @return   Some(Nil) to signify skipping current row
    */
  def fda_skip[ROW]: Option[List[ROW]] = FDAValves.fda_skip[ROW]
  /**
    * send row downstream
    * @example {{{
    *    def trackRows: FDAUserTask[FDAROW] = row => {
    *      row match {
    *        case m@StateModel(id,name) =>
    *          println(s"State: $id $name")
    *          println( "----------------")
    *          fda_next(m)
    *        case DivideZeroError(msg, e) => //error row
    *          println(s"***Error:$msg***")
    *          fda_skip
    *        case m@_ => fda_next(m)
    *       }
    *     }
    * }}}
    * @param row    target row
    * @tparam ROW   type of target row
    * @return       a single row to be sent downstream
    */
  def fda_next[ROW](row: ROW): Option[List[ROW]] = FDAValves.fda_next(row)
  /**
    * send a list of rows downstream in a chunk
    * @example {{{
    *    def trackRows: FDAUserTask[FDAROW] = row => {
    *      row match {
    *        case m@StateModel(id,name) =>
    *          println(s"State: $id $name")
    *          println( "----------------")
    *          fda_next(m)
    *        case DivideZeroError(msg, e) => //error row
    *          println(s"***Error:$msg***")
    *          fda_skip
    *        case m@_ => fda_next(List(m,m)) //send double row
    *       }
    *     }
    * }}}
    * @param lr     list of rows to be sent
    * @tparam ROW   type of target row
    * @return       a list of many rows
    */
  def fda_next[ROW](lr: List[ROW]): Option[List[ROW]] = FDAValves.fda_next(lr)
  /**
    * halt the current stream
    * @example {{{
    *    //loading rows by year
    *    def loadRowsByYear: FDASourceLoader = row => {
    *      row match {
    *        case Years(y) => loadRowsInYear(y) //produce stream of the year
    *        case _ => //something wrong break and terminate this process
    *           fda_break
    *      }
    *    }
    * }}}
    * @return  a None indicating end of current stream process
    */
  def fda_break = FDAValves.fda_break
}

/**
  * for global imports
  */
object FDAPipes extends FDAPipes