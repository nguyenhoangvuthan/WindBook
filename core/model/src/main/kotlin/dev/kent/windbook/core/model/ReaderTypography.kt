package dev.kent.windbook.core.model

enum class ReaderFont { PUBLISHER, LITERATA, SERIF, SANS }
enum class ReaderColors { SYSTEM, PAPER, SEPIA, DARK }

/** Null inherits a global field; explicit publisher font/zero line height remove that override. */
data class ReaderTypography(
    val font: ReaderFont? = null,
    val size: Int? = null,
    val lineHeight: Double? = null,
    val margin: Int? = null,
    val colors: ReaderColors? = null,
    /** True keeps the publisher's own formatting; null/false lets the app lay the text out (default). */
    val originalLayout: Boolean? = null,
) {
    fun normalized() = copy(size=size?.coerceIn(80,200),
        lineHeight=lineHeight?.takeIf { it.isFinite() }?.let { if(it==0.0) 0.0 else it.coerceIn(1.2,2.0) },
        margin=margin?.coerceIn(0,2))
    fun resolveOver(global: ReaderTypography): ReaderTypography = ReaderTypography(
        font ?: global.font, size ?: global.size, lineHeight ?: global.lineHeight,
        margin ?: global.margin, colors ?: global.colors, originalLayout ?: global.originalLayout).normalized()
}
