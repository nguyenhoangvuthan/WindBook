package dev.kent.windbook.reader

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Locator JSON is opaque outside this module; these helpers are the only way in or out. */
object LocatorCodec {
    /** UI seek uses the same first/last endpoints as position lookup, without changing saved progress. */
    fun seekFraction(locatorJson: String?, positionCount: Int, fallback: Double): Double {
        val position=locatorJson?.toLocatorOrNull()?.locations?.position
        if(positionCount>0 && position!=null && position in 1..positionCount)
            return if(positionCount==1) 0.0 else (position-1).toDouble()/(positionCount-1)
        return if(fallback.isFinite()) fallback.coerceIn(0.0,1.0) else 0.0
    }
    fun progression(locatorJson: String): Double? =
        locatorJson.toLocatorOrNull()?.locations?.totalProgression
}

internal fun String.toLocatorOrNull(): Locator? =
    runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

internal fun Locator.toJsonString(): String = toJSON().toString()

/**
 * Progress shown to the reader and used for "Mark finished". Readium's totalProgression is the
 * start of the current position ((page - 1) / pages for PDF), so the last page never reaches 1.0;
 * position / count does.
 */
fun readingProgression(position: Int?, positionCount: Int, totalProgression: Double?): Double =
    if (position != null && positionCount > 0) {
        (position.toDouble() / positionCount).coerceIn(0.0, 1.0)
    } else {
        totalProgression ?: 0.0
    }
