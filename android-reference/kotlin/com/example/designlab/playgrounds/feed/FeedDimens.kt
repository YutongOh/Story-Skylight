package com.example.designlab.playgrounds.feed

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma 7483:4560 — Feed (360×800). All sizes in dp from Dev Mode inspect. */
object FeedDimens {
    const val DesignFrameW = 360f
    const val DesignFrameH = 800f

    // Top tab navigation — frame 7483:4569, y=41, h=56
    val TopTabNavH = 56.dp
    val TopTabNavPadH = 8.dp
    val TopTabItemPadH = 8.dp
    val TopTabIconFrame = 24.dp
    val TopTabLeadingTapW = 40.dp
    val TopTabLeadingTapH = 44.dp
    val TopTabItemH = 44.dp
    val TabIndicatorW = 24.dp
    val TabIndicatorH = 2.dp
    val TabIndicatorBottom = 2.dp
    val TabIndicatorRadius = 0.5.dp
    val FollowingBadgeSize = 8.dp

    // Bottom nav — frame 7484:5024, h=49
    val BottomNavH = 49.dp
    val BottomNavGap = 2.dp
    val BottomNavSeparator = 0.5.dp
    /** Figma 7484:5026 — Icon Frame container */
    val BottomNavIconFrame = 32.dp
    /** Exp_01 icon glyph size, centered inside Icon Frame */
    val BottomNavIconSize = 24.dp
    val BottomNavCreateFrame = 48.dp
    val BottomNavCreateIconW = 43.dp
    val BottomNavCreateIconH = 28.dp
    val BottomNavCreateIconStart = 2.5.dp
    val BottomNavCreateIconTop = 10.dp
    val BottomNavCellPadTop = 2.dp
    val BottomNavCellPadBottom = 3.dp
    /** Figma 7484:5030 — label y=33 within 49dp Bar Cell */
    val BottomNavLabelTop = 33.dp
    val BottomNavLabelPadH = 0.dp
    /** App/Small Text 2-Medium — tracking 0.2287px @ 10sp */
    val BottomNavLabelTracking = 0.23.sp
    val BottomNavLabelLineHeight = 13.sp

    // Bottom overlay — 7483:4562, h=163
    val BottomOverlayH = 163.dp

    // Info — 7483:4564, x=12, w=263, sound row w=195
    val BottomInfoPadStart = 12.dp
    val BottomInfoAboveNav = 14.dp
    val BottomInfoInnerGap = 8.dp
    val CaptionMaxWidth = 263.dp
    val SoundRowH = 18.dp
    val SoundRowMaxWidth = 195.dp
    val SoundIconFrame = 16.dp
    val SoundTextStart = 20.dp
    val SoundIconBottom = 2.dp

    // Interaction — 7483:4592, w=62, bottom aligns with info (y=713, 14dp above nav)
    val InteractionWidth = 62.dp
    val InteractionAboveNav = 14.dp
    val InteractionColumnGap = 4.dp
    val InteractionAvatarSectionH = 62.dp
    val InteractionAvatarBlockTop = 6.dp
    val InteractionAvatarSize = 44.dp
    val InteractionAvatarRingSize = 46.dp
    val InteractionAvatarRingBorder = 1.dp
    val InteractionFollowBlockW = 44.dp
    val InteractionFollowBlockH = 54.dp
    /** Figma 7483:4604 — Follow Button @ (8, 34) in 44×54 block */
    val InteractionFollowW = 28.dp
    val InteractionFollowH = 20.dp
    val InteractionFollowOffsetX = 8.dp
    val InteractionFollowOffsetY = 34.dp
    val InteractionActionGap = 12.dp
    val InteractionIconFrame = 32.dp
    val InteractionIconStart = 15.dp
    val InteractionLikeH = 59.dp
    val InteractionCommentH = 65.dp
    val InteractionShareH = 68.dp
    val InteractionLikeIconTop = 6.dp
    val InteractionCommentIconTop = 12.dp
    val InteractionShareIconBottom = 24.dp
    val InteractionActionLabelBottom = 4.dp
    val InteractionMusicDisk = 40.dp
    val InteractionMusicSectionH = 49.dp
    val InteractionMusicPadTop = 8.dp
    val InteractionMusicPadH = 10.dp
}
