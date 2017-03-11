package com.bayakala.funda.samples

import slick.driver.H2Driver.api._

object SlickModels {

/* ----- schema  */
//表字段对应模版
case class AlbumModel(id: Long
                      , artist: String
                      , year: Option[Int]
                      , title: String
                      , company: Int
                     )

//表结构: 定义字段类型, * 代表结果集字段
class AlbumTable(tag: Tag) extends Table[AlbumModel](tag, "ALBUMS") {
  def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)

  def title = column[String]("TITLE")

  def year = column[Option[Int]]("YEAR")

  def artist = column[String]("ARTIST", O.Default("Unknown"))

  def company = column[Int]("COMPANY")

  def * = (id, artist, year, title, company) <> (AlbumModel.tupled, AlbumModel.unapply)
}

//库表实例
val albums = TableQuery[AlbumTable]

class CompanyTable(tag: Tag) extends Table[(Int,String)](tag,"COMPANY") {
  def id = column[Int]("ID",O.PrimaryKey)
  def name = column[String]("NAME")
  def * = (id,name)
}
val companies = TableQuery[CompanyTable]


}
