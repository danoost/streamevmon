package nz.net.wand.amp.analyser.measurements

case class ICMPMeta(
    stream: Int,
    source: String,
    destination: String,
    family: String,
    packet_size: String
) extends MeasurementMeta