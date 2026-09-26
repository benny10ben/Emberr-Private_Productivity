package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasNodeEntity
import kotlinx.serialization.Serializable

const val DEFAULT_TEXT_FONT_FAMILY = "fuzzybubbles"
const val DEFAULT_TEXT_FONT_WEIGHT = 400
const val DEFAULT_TEXT_FONT_SIZE = 16f

@Serializable
data class CanvasTextStyle(
    val fontFamily: String? = DEFAULT_TEXT_FONT_FAMILY,
    val fontWeight: Int = DEFAULT_TEXT_FONT_WEIGHT,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val fontSize: Float = DEFAULT_TEXT_FONT_SIZE,
    val textAlign: String? = null
)

val CanvasNodeEntity.textStyle: CanvasTextStyle
    get() = CanvasTextStyle(
        fontFamily = fontFamily,
        fontWeight = fontWeight ?: DEFAULT_TEXT_FONT_WEIGHT,
        textColor = textColor,
        backgroundColor = color,
        fontSize = fontSize ?: DEFAULT_TEXT_FONT_SIZE,
        textAlign = textAlign
    )

fun CanvasNodeEntity.withTextStyle(style: CanvasTextStyle): CanvasNodeEntity = copy(
    fontFamily = style.fontFamily,
    fontWeight = style.fontWeight,
    textColor = style.textColor,
    color = style.backgroundColor,
    fontSize = style.fontSize,
    textAlign = style.textAlign
)
