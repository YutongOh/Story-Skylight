package com.example.designlab.playgrounds.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytedance.tux.R as TuxR
import com.bytedance.tux.compose.TuxIcon
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.res.VectorResource
import com.example.designlab.playgrounds.R
import com.example.designlab.playgrounds.creation.AlbumV4Dimens
import com.example.designlab.playgrounds.creation.FigmaAlbumV4Screen

private enum class FeedTopTab { Following, Friends, ForYou }

private const val FeedAlbumFeedFadeMs = 90
private const val FeedAlbumEmergenceMs = 160
private val FeedAlbumEmergenceOffset = 40.dp
private const val FeedAlbumStartScale = 0.94f

@Composable
fun FeedScreen(
    onBack: () -> Unit,
    effectCoverLoadMs: Int = AlbumV4Dimens.EffectCoverLoadMs,
    onInboxTabClick: (() -> Unit)? = null,
    manageSystemBars: Boolean = true,
    backEnabled: Boolean = true,
    albumContent: @Composable (onAlbumBack: () -> Unit, startLoad: Boolean) -> Unit = { onAlbumBack, startLoad ->
        FigmaAlbumV4Screen(
            onBack = onAlbumBack,
            manageSystemBars = false,
            effectCoverLoadMs = effectCoverLoadMs,
            startEffectCoverLoad = startLoad,
        )
    },
) {
    var showAlbum by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val emergencePx = with(density) { FeedAlbumEmergenceOffset.toPx() }

    val feedAlpha by animateFloatAsState(
        targetValue = if (showAlbum) 0f else 1f,
        animationSpec = tween(durationMillis = FeedAlbumFeedFadeMs, easing = FastOutSlowInEasing),
        label = "feedAlpha",
    )
    val albumProgress by animateFloatAsState(
        targetValue = if (showAlbum) 1f else 0f,
        animationSpec = tween(durationMillis = FeedAlbumEmergenceMs, easing = FastOutSlowInEasing),
        label = "albumProgress",
    )

    BackHandler(enabled = backEnabled) {
        if (showAlbum) showAlbum = false else onBack()
    }

    if (manageSystemBars) {
        FeedDarkScreenSystemBars()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FeedColors.PageFlat1),
    ) {
        FeedMainContent(
            onCreateClick = { showAlbum = true },
            feedScrollEnabled = !showAlbum && albumProgress <= 0.001f,
            onInboxTabClick = onInboxTabClick,
            modifier = Modifier.graphicsLayer { alpha = feedAlpha },
        )

        if (showAlbum || albumProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = albumProgress
                        alpha = p
                        scaleX = FeedAlbumStartScale + (1f - FeedAlbumStartScale) * p
                        scaleY = scaleX
                        translationY = (1f - p) * emergencePx
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            ) {
                // 线上逻辑：点击 creation 入口即开始加载，转场与加载并行
                albumContent({ showAlbum = false }, showAlbum)
            }
        }
    }
}

@Composable
private fun FeedMainContent(
    onCreateClick: () -> Unit,
    feedScrollEnabled: Boolean = true,
    onInboxTabClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(FeedTopTab.ForYou.ordinal) }
    var selectedBottomTab by remember { mutableStateOf(FeedBottomNavTab.Home) }
    val feedItems = FeedVideos.presets
    val pagerState = rememberPagerState(pageCount = { feedItems.size })
    val currentItem = feedItems[pagerState.settledPage]

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = feedScrollEnabled,
            ) { page ->
                FeedVideoPlayer(
                    videoRes = feedItems[page].videoRes,
                    isActive = pagerState.settledPage == page,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FeedDimens.BottomOverlayH)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.28f to Color.Black.copy(alpha = 0.12f),
                                0.62f to Color.Black.copy(alpha = 0.48f),
                                1f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )

            FeedInteractionColumn(
                item = currentItem,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = FeedDimens.BottomInfoAboveNav),
            )

            FeedBottomInfo(
                item = currentItem,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = FeedDimens.BottomInfoPadStart,
                        end = FeedDimens.InteractionWidth,
                        bottom = FeedDimens.BottomInfoAboveNav,
                    ),
            )

            FeedTopBar(
                selectedTab = FeedTopTab.entries[selectedTab],
                onTabSelected = { selectedTab = it.ordinal },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 1.dp),
            )
        }

        FeedBottomNavBar(
            selectedTab = selectedBottomTab,
            onTabSelected = { tab ->
                if (tab == FeedBottomNavTab.Inbox && onInboxTabClick != null) {
                    onInboxTabClick()
                } else {
                    selectedBottomTab = tab
                }
            },
            onCreateClick = onCreateClick,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun FeedTopBar(
    selectedTab: FeedTopTab,
    onTabSelected: (FeedTopTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FeedDimens.TopTabNavH)
            .padding(horizontal = FeedDimens.TopTabNavPadH),
    ) {
        Box(
            modifier = Modifier
                .size(FeedDimens.TopTabLeadingTapW, FeedDimens.TopTabLeadingTapH)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            TuxIcon(
                icon = VectorResource(TuxR.raw.icon_live_entrance),
                tint = FeedColors.TextInverse,
                modifier = Modifier.size(FeedDimens.TopTabIconFrame),
                contentDescription = stringResource(R.string.feed_live_entrance),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedTopTabItem(
                label = stringResource(R.string.feed_tab_following),
                selected = selectedTab == FeedTopTab.Following,
                showBadge = true,
                onClick = { onTabSelected(FeedTopTab.Following) },
            )
            FeedTopTabItem(
                label = stringResource(R.string.feed_tab_friends),
                selected = selectedTab == FeedTopTab.Friends,
                onClick = { onTabSelected(FeedTopTab.Friends) },
            )
            FeedTopTabItem(
                label = stringResource(R.string.feed_tab_for_you),
                selected = selectedTab == FeedTopTab.ForYou,
                onClick = { onTabSelected(FeedTopTab.ForYou) },
            )
        }

        Box(
            modifier = Modifier
                .size(FeedDimens.TopTabLeadingTapW, FeedDimens.TopTabLeadingTapH)
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center,
        ) {
            TuxIcon(
                icon = VectorResource(TuxR.raw.icon_magnifying_glass),
                tint = FeedColors.TextInverse,
                modifier = Modifier.size(FeedDimens.TopTabIconFrame),
                contentDescription = stringResource(R.string.feed_search),
            )
        }
    }
}

@Composable
private fun FeedTopTabItem(
    label: String,
    selected: Boolean,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = FeedDimens.TopTabItemPadH)
            .height(FeedDimens.TopTabItemH),
        contentAlignment = Alignment.Center,
    ) {
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 5.dp)
                    .size(FeedDimens.FollowingBadgeSize)
                    .clip(CircleShape)
                    .background(FeedColors.ShapePrimary),
            )
        }
        TuxText(
            text = label,
            style = if (selected) {
                TuxTheme.typography.h3_bold.copy(shadow = FeedColors.TabShadow)
            } else {
                TuxTheme.typography.h3_semibold.copy(shadow = FeedColors.TabShadow)
            },
            color = if (selected) FeedColors.Text1 else FeedColors.Text3,
            maxLines = 1,
            softWrap = false,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FeedDimens.TabIndicatorBottom)
                    .width(FeedDimens.TabIndicatorW)
                    .height(FeedDimens.TabIndicatorH)
                    .clip(RoundedCornerShape(FeedDimens.TabIndicatorRadius))
                    .background(FeedColors.Text1),
            )
        }
    }
}

@Composable
private fun FeedInteractionColumn(
    item: FeedVideoItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(FeedDimens.InteractionWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeedDimens.InteractionColumnGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FeedDimens.InteractionAvatarSectionH),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = FeedDimens.InteractionAvatarBlockTop)
                    .width(FeedDimens.InteractionFollowBlockW)
                    .height(FeedDimens.InteractionFollowBlockH),
            ) {
                Box(
                    modifier = Modifier
                        .size(FeedDimens.InteractionAvatarRingSize)
                        .align(Alignment.TopCenter)
                        .clip(CircleShape)
                        .border(FeedDimens.InteractionAvatarRingBorder, FeedColors.TextInverse, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.feed_avatar),
                        contentDescription = null,
                        modifier = Modifier
                            .size(FeedDimens.InteractionAvatarSize)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.feed_follow_button),
                    contentDescription = stringResource(R.string.feed_action_follow),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = FeedDimens.InteractionFollowOffsetX,
                            y = FeedDimens.InteractionFollowOffsetY,
                        )
                        .width(FeedDimens.InteractionFollowW)
                        .height(FeedDimens.InteractionFollowH),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FeedActionItem(
                icon = VectorResource(TuxR.raw.icon_color_like_shadow),
                label = stringResource(item.likeCountRes),
                height = FeedDimens.InteractionLikeH,
                iconTop = FeedDimens.InteractionLikeIconTop,
                contentDescription = stringResource(R.string.feed_action_like),
            )
            FeedActionItem(
                icon = VectorResource(TuxR.raw.icon_color_comment_shadow),
                label = stringResource(item.commentCountRes),
                height = FeedDimens.InteractionCommentH,
                iconTop = FeedDimens.InteractionCommentIconTop,
                contentDescription = stringResource(R.string.feed_action_comment),
            )
            FeedActionItem(
                icon = VectorResource(TuxR.raw.icon_color_share_shadow),
                label = stringResource(item.shareCountRes),
                height = FeedDimens.InteractionShareH,
                iconBottom = FeedDimens.InteractionShareIconBottom,
                contentDescription = stringResource(R.string.feed_action_share),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FeedDimens.InteractionMusicSectionH)
                .padding(
                    top = FeedDimens.InteractionMusicPadTop,
                    start = FeedDimens.InteractionMusicPadH,
                    end = FeedDimens.InteractionMusicPadH,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(R.drawable.feed_music_disk),
                contentDescription = stringResource(R.string.feed_sound_label),
                modifier = Modifier.size(FeedDimens.InteractionMusicDisk),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun FeedActionItem(
    icon: VectorResource,
    label: String,
    height: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    iconTop: androidx.compose.ui.unit.Dp? = null,
    iconBottom: androidx.compose.ui.unit.Dp? = null,
) {
    Box(
        modifier = Modifier
            .width(FeedDimens.InteractionWidth)
            .height(height),
    ) {
        when {
            iconBottom != null -> {
                TuxIcon(
                    icon = icon,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = FeedDimens.InteractionIconStart,
                            bottom = iconBottom,
                        )
                        .size(FeedDimens.InteractionIconFrame),
                    contentDescription = contentDescription,
                )
            }
            iconTop != null -> {
                TuxIcon(
                    icon = icon,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = FeedDimens.InteractionIconStart,
                            top = iconTop,
                        )
                        .size(FeedDimens.InteractionIconFrame),
                    contentDescription = contentDescription,
                )
            }
        }
        TuxText(
            text = label,
            style = TuxTheme.typography.p2_semibold.copy(shadow = FeedColors.ActionShadowPrimary),
            color = FeedColors.TextInverse,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FeedDimens.InteractionActionLabelBottom),
        )
    }
}

@Composable
private fun FeedBottomInfo(
    item: FeedVideoItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FeedDimens.BottomInfoInnerGap),
    ) {
        TuxText(
            text = stringResource(item.usernameRes),
            style = TuxTheme.typography.h4_semibold.copy(shadow = FeedColors.OverlayShadow),
            color = FeedColors.TextInverse,
            maxLines = 1,
            softWrap = false,
        )
        TuxText(
            text = stringResource(item.captionRes),
            style = TuxTheme.typography.h4_regular.copy(shadow = FeedColors.CaptionShadow),
            color = FeedColors.TextCaption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = FeedDimens.CaptionMaxWidth),
        )
        TuxText(
            text = stringResource(R.string.feed_see_translation),
            style = TuxTheme.typography.small_text_1_semibold.copy(shadow = FeedColors.OverlayShadow),
            color = FeedColors.TextInverse,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .widthIn(max = FeedDimens.SoundRowMaxWidth)
                .height(FeedDimens.SoundRowH),
        ) {
            TuxIcon(
                icon = VectorResource(TuxR.raw.icon_music_note_s),
                tint = FeedColors.TextInverse,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = FeedDimens.SoundIconBottom)
                    .size(FeedDimens.SoundIconFrame),
                contentDescription = stringResource(R.string.feed_sound_label),
            )
            TuxText(
                text = stringResource(item.soundRes),
                style = TuxTheme.typography.p1_regular.copy(shadow = FeedColors.OverlayShadow),
                color = FeedColors.TextInverse,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = FeedDimens.SoundTextStart),
            )
        }
    }
}
