package com.example.designlab.playgrounds.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

/** Figma 7483:4560 design tokens — colors & shadows. */
object FeedColors {
    val PageFlat1 = Color(0xFF000000)
    val Text1 = Color(0xFFF6F6F6)
    val Text3 = Color(0x99FFFFFF)
    val TextInverse = Color(0xFFFFFFFF)
    val TextCaption = Color(0xE6FFFFFF)
    val ShapePrimary = Color(0xFFFE2C55)
    /** UI/Shape/Neutral 3 — divider rgba(255,255,255,0.19) */
    val NavSeparator = Color(0x30FFFFFF)
    val ShapeNeutral3 = NavSeparator

    val TabShadow = Shadow(color = Color(0x80000000), blurRadius = 2f, offset = Offset(0f, 1f))
    val TabIndicatorShadow = Shadow(color = Color(0x80000000), blurRadius = 2f, offset = Offset(0f, 1f))
    val OverlayShadow = Shadow(color = Color(0x26000000), blurRadius = 3f, offset = Offset(0f, 1f))
    val CaptionShadow = Shadow(color = Color(0x33000000), blurRadius = 3f, offset = Offset(0f, 1f))
    val ActionShadowPrimary = Shadow(color = Color(0x33161823), blurRadius = 3f, offset = Offset(0f, 1f))
    val ActionShadowSecondary = Shadow(color = Color(0x33000000), blurRadius = 1f, offset = Offset(0f, 1f))
}
