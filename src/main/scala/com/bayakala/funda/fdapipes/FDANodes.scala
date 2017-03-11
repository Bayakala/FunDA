package com.bayakala.funda.fdapipes
import com.bayakala.funda._
import FDAJoints._

/** methods to run an user defined function*/
object FDANodes { //作业节点工作方法
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
  def fda_execUserTask[ROW](task: FDAUserTask[ROW]): FDAWorkNode[ROW] = {
    def go: FDAValve[ROW] => FDAPipeJoint[ROW] = h => {
      h.receive1Option {
        case Some((r, h)) => task(r) match {
          case Some(lx) => lx match {
            case Nil => go(h)
            case _ => fda_pushRows(lx) >> go(h)
          }
          case None => {task(FDANullRow.asInstanceOf[ROW]); fda_halt}
        }
        case None => {task(FDANullRow.asInstanceOf[ROW]); fda_halt}
      }
    }
    in => in.pull(go)
  }
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
  def fda_aggregate[AGGR,ROW](aggr: AGGR, task: FDAAggrTask[AGGR,ROW]): FDAWorkNode[ROW] = {
   def go(acc: AGGR): FDAValve[ROW] => FDAPipeJoint[ROW] = h => {
     h.receive1Option {
       case Some((r, h)) => task(acc,r) match {
         case (a,Some(lx)) => lx match {
           case Nil => go(a)(h)
           case _ => fda_pushRows(lx) >> go(a)(h)
         }
         case (a,None) => {task(a,FDANullRow.asInstanceOf[ROW]); fda_halt}
       }
       case None => {task(acc,FDANullRow.asInstanceOf[ROW]); fda_halt}
     }
   }
   in => in.pull(go(aggr))
 }

}
