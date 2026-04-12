package com.fitshare.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses a GPX file (as exported by Gadgetbridge) into a list of LatLon coordinates.
 *
 * Gadgetbridge GPX format:
 *   <trk><trkseg><trkpt lat="48.1374" lon="11.5754">...</trkpt>...</trkseg></trk>
 *
 * Uses Android's built-in XmlPullParser — no external dependencies needed.
 */
object GpxParser {

    fun parseFromUri(context: Context, uri: Uri): List<LatLon> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parse(stream)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parse(inputStream: InputStream): List<LatLon> {
        val points = mutableListOf<LatLon>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                if (lat != null && lon != null) {
                    points.add(LatLon(lat, lon))
                }
            }
            eventType = parser.next()
        }

        return points
    }
}
