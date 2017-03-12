package com.bayakala.funda.samples

import slick.driver.H2Driver.api._

object SlickModels {

  //表字段对应模版
  case class AQMRawModel(mid: String
                         , mtype: String
                         , state: String
                         , fips: String
                         , county: String
                         , year: String
                         , value: String)

  //表结构: 定义字段类型, * 代表结果集字段
  class AQMRawTable(tag: Tag) extends Table[AQMRawModel](tag, "AIRQM") {
    def mid = column[String]("MEASUREID")
    def mtype = column[String]("MEASURETYPE")
    def state = column[String]("STATENAME")
    def fips = column[String]("COUNTYFIPS")
    def county = column[String]("COUNTYNAME")
    def year = column[String]("REPORTYEAR")
    def value = column[String]("VALUE")

    def * = (mid,mtype,state,fips,county,year,value) <> (AQMRawModel.tupled, AQMRawModel.unapply)
  }

  //库表实例
  val AQMRawQuery = TableQuery[AQMRawTable]

}