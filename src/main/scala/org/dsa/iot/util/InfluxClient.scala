package org.dsa.iot.util

import com.paulgoldbaum.influxdbclient.Parameter.Consistency.Consistency
import com.paulgoldbaum.influxdbclient.Parameter.Precision.Precision
import com.paulgoldbaum.influxdbclient.{InfluxDB, Point}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * InfluxDB client.
  *
  * @param host
  * @param port
  * @param dbName
  */
class InfluxClient(host: String, port: Int, dbName: String) {

  private val dbConn = InfluxDB.connect(host, port)
  private val db = dbConn.selectDatabase(dbName)

  /**
    * Converts the supplied value into InfluxDB point and writes it to the database.
    *
    * @param value
    * @param retention
    * @param cnv
    * @param precision
    * @param consistency
    * @tparam T
    * @return
    */
  def write[T](value: T, retention: String = null)(implicit cnv: T => Point,
                                                   precision: Precision = null,
                                                   consistency: Consistency = null) =
    db.write(cnv(value), precision, consistency, retention)

  /**
    * Converts the supplied list of values into InfluxDB points and writes them to the database.
    *
    * @param values
    * @param retention
    * @param cnv
    * @param precision
    * @param consistency
    * @tparam T
    * @return
    */
  def bulkWrite[T](values: Seq[T], retention: String = null)(implicit cnv: T => Point,
                                                             precision: Precision = null,
                                                             consistency: Consistency = null) =
    db.bulkWrite(values map cnv, precision, consistency, retention)

  /**
    * Closes the InfluxDB connection.
    */
  def close() = dbConn.close()
}

/**
  * Factory for [[InfluxClient]] instances.
  */
object InfluxClient {

  /**
    * A shared instance of [[InfluxClient]].
    */
  val getInstance: InfluxClient = {
    val cfg = ConfigFactory.load.getConfig("influx")
    val host = cfg.getString("host")
    val port = cfg.getInt("port")
    val dbName = cfg.getString("database")
    new InfluxClient(host, port, dbName)
  }
}