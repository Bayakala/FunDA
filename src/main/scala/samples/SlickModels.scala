package com.bayakala.funda.samples

import slick.jdbc.H2Profile.api._
import com.bayakala.funda._

object SlickModels {

  //表字段对应模版
  case class AQMRawModel(mid: String
                         , mtype: String
                         , state: String
                         , fips: String
                         , county: String
                         , year: String
                         , value: String) extends FDAROW

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

  case class AQMRPTModel(rid: Long
                         , mid: Int
                         , state: String
                         , county: String
                         , year: Int
                         , value: Int
                         , total: Int
                         , valid: Boolean) extends FDAROW

  class AQMRPTTable(tag: Tag) extends Table[AQMRPTModel](tag, "AQMRPT") {
    def rid = column[Long]("ROWID",O.AutoInc,O.PrimaryKey)
    def mid = column[Int]("MEASUREID")
    def state = column[String]("STATENAME",O.Length(32))
    def county = column[String]("COUNTYNAME",O.Length(32))
    def year = column[Int]("REPORTYEAR")
    def value = column[Int]("VALUE")
    def total = column[Int]("TOTAL")
    def valid = column[Boolean]("VALID")

    def * = (rid,mid,state,county,year,value,total,valid) <> (AQMRPTModel.tupled, AQMRPTModel.unapply)
  }


  val AQMRPTQuery = TableQuery[AQMRPTTable]

  case class StateModel(id: Int, name: String) extends FDAROW
  class StateTable(tag: Tag) extends Table[StateModel](tag,"STATE") {
    def id = column[Int]("ID",O.AutoInc,O.PrimaryKey)
    def name = column[String]("NAME",O.Length(32))
    def * = (id,name)<>(StateModel.tupled,StateModel.unapply)
  }
  val StateQuery = TableQuery[StateTable]

  case class CountyModel(id: Int, name: String) extends FDAROW
  case class CountyTable(tag: Tag) extends Table[CountyModel](tag,"COUNTY") {
    def id = column[Int]("ID",O.AutoInc,O.PrimaryKey)
    def name = column[String]("NAME",O.Length(64))
    def * = (id,name)<>(CountyModel.tupled,CountyModel.unapply)
  }
  val CountyQuery = TableQuery[CountyTable]

  case class NORMAQMModel(rid: Long
                          , mid: Int
                          , state: Int
                          , county: Int
                          , year: Int
                          , value: Int
                          , average: Int
                         ) extends FDAROW

  class NORMAQMTable(tag: Tag) extends Table[NORMAQMModel](tag, "NORMAQM") {
    def rid = column[Long]("ROWID",O.AutoInc,O.PrimaryKey)
    def mid = column[Int]("MEASUREID")
    def state = column[Int]("STATID")
    def county = column[Int]("COUNTYID")
    def year = column[Int]("REPORTYEAR")
    def value = column[Int]("VALUE")
    def average = column[Int]("AVG")

    def * = (rid,mid,state,county,year,value,average) <> (NORMAQMModel.tupled, NORMAQMModel.unapply)
  }


  val NORMAQMQuery = TableQuery[NORMAQMTable]


}