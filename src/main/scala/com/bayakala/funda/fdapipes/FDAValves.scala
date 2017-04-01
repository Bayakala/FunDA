package com.bayakala.funda.fdapipes

/**
  * methods to send data downstream
  * Some(Nil)           : skip sending the current row
  * Some(List(r1,r2...)): send r1,r2... downstream
  * None                : halt stream, end of process
  *
  */
object FDAValves {  //流动控制方法
  /** 跳过本行（不向下游发送）
    * skip current row by sending a Some(Nil)
    * @return   Some(Nil) to signify skipping current row
    */
  def fda_skip[ROW]: Option[List[ROW]] = Some(List[ROW]())

  /** 将本行发送至下游连接管道
    * send row downstream
    * @param row    target row
    * @tparam ROW   type of target row
    * @return       a single row to be sent downstream
    */
  def fda_next[ROW](row: ROW): Option[List[ROW]] = Some(List[ROW](row))

  /**
    * send a list of rows downstream in a chunk
    * @param lr     list of rows to be sent
    * @tparam ROW   type of target row
    * @return       a list of many rows
    */
  def fda_next[ROW](lr: List[ROW]): Option[List[ROW]] = Some(lr)

  /** 终止流动
    * halt the current stream
    * @return  a None indicating end of current stream process
    */
  def fda_break = None

}

