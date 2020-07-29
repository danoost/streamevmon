package nz.net.wand.streamevmon.connectors.esmond.schema

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyOrder}

@JsonPropertyOrder(alphabetic = true)
class PacketTraceTimeSeriesEntry extends AbstractTimeSeriesEntry {
  @JsonProperty("val")
  val value: Iterable[PacketTraceEntry] = Seq()

  def canEqual(other: Any): Boolean = other.isInstanceOf[PacketTraceTimeSeriesEntry]

  override def equals(other: Any): Boolean = other match {
    case that: PacketTraceTimeSeriesEntry =>
      (that canEqual this) &&
        value == that.value
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(value)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

@JsonPropertyOrder(alphabetic = true)
class ASEntry extends Serializable {
  @JsonProperty("owner")
  val owner: String = null
  @JsonProperty("number")
  val number: Int = Int.MinValue

  def canEqual(other: Any): Boolean = other.isInstanceOf[ASEntry]

  override def equals(other: Any): Boolean = other match {
    case that: ASEntry =>
      (that canEqual this) &&
        owner == that.owner &&
        number == that.number
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(owner, number)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

@JsonPropertyOrder(alphabetic = true)
class PacketTraceEntry extends Serializable {
  @JsonProperty("success")
  val success: Int = Int.MinValue
  @JsonProperty("ip")
  val ip: Option[String] = None
  @JsonProperty("hostname")
  val hostname: Option[String] = None
  @JsonProperty("rtt")
  val rtt: Option[Double] = None
  @JsonProperty("as")
  val as: Option[ASEntry] = None
  @JsonProperty("ttl")
  val ttl: Int = Int.MinValue
  @JsonProperty("query")
  val query: Int = Int.MinValue
  @JsonProperty("error-message")
  val errorMessage: Option[String] = None

  def canEqual(other: Any): Boolean = other.isInstanceOf[PacketTraceEntry]

  override def equals(other: Any): Boolean = other match {
    case that: PacketTraceEntry =>
      (that canEqual this) &&
        success == that.success &&
        ip == that.ip &&
        hostname == that.hostname &&
        rtt == that.rtt &&
        as == that.as &&
        ttl == that.ttl &&
        query == that.query
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(success, ip, hostname, rtt, as, ttl, query)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}