package com.bayakala.funda.fdapipes

import fs2._

/**
  * methods to output row to a Pull object
  */
object FDAJoints {  //数据发送方法
  /**
    * send row to Pull
    * @param row     row to be sent
    * @tparam ROW    type of row
    * @return        new state of Pull
    */
  def fda_pushRow[ROW](row: ROW) = Pull.output1(row)

  /**
    * send list of rows to Pull
    * @param rows   list of rows to be sent
    * @tparam ROW   type of target row
    * @return       new state of Pull
    */
  def fda_pushRows[ROW](rows: List[ROW]) = Pull.output(Chunk.seq(rows))

  /**
    * end output to Pull
    * @return  end of Pull
    */
  def fda_halt = Pull.done
}
