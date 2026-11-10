package org.radarbase.push.integration.google.tcx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.radarbase.push.integration.google.tcx.model.TcxTrackpoint
import java.time.Instant

class TcxParserTest {
    @Test
    fun `parses repeated trackpoints with namespaced extensions and skips bad time`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
                xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-03-08T01:29:00Z</Id>
                  <Lap StartTime="2026-03-08T01:29:00Z">
                    <TotalTimeSeconds>120</TotalTimeSeconds>
                    <Track>
                      <Trackpoint>
                        <Time>2026-03-08T01:29:00Z</Time>
                        <Position>
                          <LatitudeDegrees>51.5</LatitudeDegrees>
                          <LongitudeDegrees>-0.12</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>10.5</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>120</Value></HeartRateBpm>
                        <Cadence>85</Cadence>
                        <Extensions><ns3:TPX><ns3:Speed>2.5</ns3:Speed></ns3:TPX></Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-03-08T01:29:10Z</Time>
                        <Position>
                          <LatitudeDegrees>51.5001</LatitudeDegrees>
                          <LongitudeDegrees>-0.1201</LongitudeDegrees>
                        </Position>
                        <DistanceMeters>25.0</DistanceMeters>
                        <HeartRateBpm><Value>122</Value></HeartRateBpm>
                        <Extensions><ns3:TPX><ns3:Speed>2.7</ns3:Speed><ns3:RunCadence>90</ns3:RunCadence></ns3:TPX></Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>not-a-time</Time>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>"""

        val points = TcxParser.parse(xml.toByteArray())

        // Repeated <Trackpoint> are all kept (the POJO-mapping pitfall this guards against); the
        // third is dropped because its <Time> is unparseable.
        assertEquals(2, points.size)

        val first = points[0]
        assertEquals(Instant.parse("2026-03-08T01:29:00Z"), first.time)
        assertEquals(51.5, first.latitude)
        assertEquals(-0.12, first.longitude)
        assertEquals(10.5f, first.altitudeMeters)
        assertEquals(120, first.heartRateBpm)
        assertEquals(85, first.cadence)
        assertEquals(2.5f, first.speedMetersPerSecond)

        val second = points[1]
        assertEquals(122, second.heartRateBpm)
        assertEquals(2.7f, second.speedMetersPerSecond)
        assertEquals(90, second.cadence) // falls back to RunCadence extension
        assertNull(second.altitudeMeters)
    }

    @Test
    fun `empty input yields no trackpoints`() {
        assertEquals(emptyList<TcxTrackpoint>(), TcxParser.parse(ByteArray(0)))
    }
}
