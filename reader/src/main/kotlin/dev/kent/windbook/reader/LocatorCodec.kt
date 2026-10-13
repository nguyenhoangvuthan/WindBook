package dev.kent.windbook.reader

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Locator JSON is opaque outside this module; these helpers are the only way in or out. */
object LocatorCodec {
    fun progression(locatorJson: String): Double? =
        locatorJson.toLocatorOrNull()?.locations?.totalProgression
}

internal fun String.toLocatorOrNull(): Locator? =
    runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

internal fun Locator.toJsonString(): String = toJSON().toString()
