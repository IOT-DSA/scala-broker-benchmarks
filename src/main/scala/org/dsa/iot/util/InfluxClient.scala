package org.dsa.iot.util

import com.paulgoldbaum.influxdbclient.Parameter.Consistency.Consistency
import com.paulgoldbaum.influxdbclient.Parameter.Precision.Precision
import com.paulgoldbaum.influxdbclient._
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
    * Runs an InfluxDB query and returns a Future of the result.
    *
    * @param q
    * @param precision
    * @return
    */
  def query(q: String)(implicit precision: Precision = null) = db.query(q, precision)

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
    * Converts the supplied value into InfluxDB points and writes them to the database.
    *
    * @param values
    * @param retention
    * @param cnv
    * @param precision
    * @param consistency
    * @tparam T
    * @return
    */
  def bulkWrite[T](values: T, retention: String = null)(implicit cnv: T => Seq[Point],
                                                        precision: Precision = null,
                                                        consistency: Consistency = null) =
    db.bulkWrite(cnv(values), precision, consistency, retention)

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

  /* converters to simplify Field creation */
  implicit def tupleToStringField(tuple: (String, String)) = StringField(tuple._1, tuple._2)

  implicit def tupleToDoubleField(tuple: (String, Double)) = DoubleField(tuple._1, tuple._2)

  implicit def tupleToLongField(tuple: (String, Long)) = LongField(tuple._1, tuple._2)

  implicit def tupleToIntField(tuple: (String, Int)) = LongField(tuple._1, tuple._2)

  implicit def tupleToBooleanField(tuple: (String, Boolean)) = BooleanField(tuple._1, tuple._2)

  /**
    * Creates a sequence of tags.
    */
  def tags(tuples: (String, String)*): Seq[Tag] = tuples map (Tag.apply _).tupled

  /**
    * Creates a sequence of fields.
    */
  def fields(flds: Field*) = Seq(flds: _*)
}