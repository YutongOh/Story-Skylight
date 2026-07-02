package com.example.designlab.playgrounds.figmainbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bytedance.compose.preview.tiktokenv.TikTokEnvTheme
import com.bytedance.tux.compose.TuxAlertBadge
import com.bytedance.tux.compose.TuxAlertBadgeVariant
import com.bytedance.tux.compose.TuxButton
import com.bytedance.tux.compose.TuxDualBall
import com.bytedance.tux.compose.TuxNavBar
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.androidview.ButtonSizeDefaults
import com.bytedance.tux.compose.androidview.ButtonVariantDefaults
import com.example.designlab.playgrounds.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Figma Inbox tab (node 3242:27879).
 * Assets live under playgrounds/src/main/res/drawable/inbox_*.png (exported from Figma MCP).
 */

internal object InboxDimens {
    val CellPadStart = 16.dp
    val CellPadEnd = 8.dp
    val CellPadV = 8.dp
    val CellLeadingEndPad = 12.dp
    val CellTrailingStartPad = 12.dp
    val CellTextGap = 2.dp
    val CellTitleIconGap = 4.dp
    val CellTimestampGap = 4.dp

    val LeadingIcon = 56.dp
    val LeadingCategoryIcon = 28.dp
    val TrailingBadgeArea = 42.dp
    val TrailingActionPadV = 12.dp
    val TrailingActionPadEnd = 8.dp

    val SectionTitlePadTop = 20.dp
    val SectionTitlePadBottom = 6.dp
    val SectionTitleIcon = 12.dp

    val AccountChipH = 16.dp
    val AccountChipRadius = 4.dp
    val AccountChipPadStart = 4.dp
    val AccountChipPadEnd = 3.dp
    val AccountDot = 8.dp
    val AccountChevron = 8.dp

    val FollowButtonW = 88.dp
    val FollowButtonH = 32.dp
    val InfoIconPadStart = 13.dp
    val InfoIconPadEnd = 5.dp
    val InfoIcon = 16.dp

    val StackAvatar = 16.dp
    val StackOverlap = (-2).dp

    val BottomNavBarH = 49.dp
    val BottomNavGap = 2.dp
    val BottomNavSeparator = 0.5.dp
    val BottomNavIconFrame = 32.dp
    val BottomNavIconSize = 24.dp
    val BottomNavIconTop = 2.dp
    val BottomNavLabelTop = 33.dp
    val BottomNavCreateFrame = 48.dp
    val BottomNavCreateTop = 0.dp
}

private object InboxAssets {
    val NavCirclePlus = R.drawable.inbox_nav_circle_plus
    val AccountDot = R.drawable.inbox_account_status_dot
    val AccountChevron = R.drawable.inbox_account_chevron
    val SectionChevron = R.drawable.inbox_section_chevron
    val SectionInfo = R.drawable.inbox_section_info
    val AvatarBorder = R.drawable.inbox_avatar_border
    val AvatarOnlineDot = R.drawable.inbox_avatar_online_dot
    val TimestampDot = R.drawable.inbox_timestamp_dot
    val InfoIcon = R.drawable.inbox_info_icon
    val CategoryFollowersIcon = R.drawable.inbox_category_followers_icon
    val CategoryActivityIcon = R.drawable.inbox_category_activity_icon
    val CategorySystemIcon = R.drawable.inbox_category_system_icon
    val CategoryPartnersIcon = R.drawable.inbox_category_partners_icon
}

@Composable
fun FigmaInboxScreen(
    onBack: () -> Unit,
    pullThreshold: Dp = StoryRevealMotion.PullThreshold,
    pushThreshold: Dp = StoryRevealMotion.PushThreshold,
    storySlideEnabled: Boolean = true,
    expandOnDrag: Boolean = false,
    startExpanded: Boolean = false,
    autoExpandOnEnter: Boolean = false,
    maxPullDistance: Dp = StoryRevealMotion.MaxHeight,
    wholePageScrollMode: Boolean = false,
    enableCreateNavigation: Boolean = true,
    onHomeTabClick: () -> Unit = {},
    inboxActive: Boolean = true,
    releaseHintEnabled: Boolean = false,
    releaseHintText: String = "Release to show story",
    topDownStoryRevealEnabled: Boolean = false,
    lockStoryExpanded: Boolean = false,
    chainRefreshAfterExpand: Boolean = false,
) {
    TikTokEnvTheme(darkTheme = false) {
        FigmaInboxScreenContent(
            onBack = onBack,
            pullThreshold = pullThreshold,
            pushThreshold = pushThreshold,
            storySlideEnabled = storySlideEnabled,
            expandOnDrag = expandOnDrag,
            startExpanded = startExpanded,
            autoExpandOnEnter = autoExpandOnEnter,
            maxPullDistance = maxPullDistance,
            wholePageScrollMode = wholePageScrollMode,
            enableCreateNavigation = enableCreateNavigation,
            onHomeTabClick = onHomeTabClick,
            inboxActive = inboxActive,
            releaseHintEnabled = releaseHintEnabled,
            releaseHintText = releaseHintText,
            topDownStoryRevealEnabled = topDownStoryRevealEnabled,
            lockStoryExpanded = lockStoryExpanded,
            chainRefreshAfterExpand = chainRefreshAfterExpand,
        )
    }
}

@Composable
private fun FigmaInboxScreenContent(
    onBack: () -> Unit,
    pullThreshold: Dp,
    pushThreshold: Dp,
    storySlideEnabled: Boolean,
    expandOnDrag: Boolean,
    startExpanded: Boolean,
    autoExpandOnEnter: Boolean,
    maxPullDistance: Dp,
    wholePageScrollMode: Boolean,
    enableCreateNavigation: Boolean,
    onHomeTabClick: () -> Unit,
    inboxActive: Boolean,
    releaseHintEnabled: Boolean,
    releaseHintText: String,
    topDownStoryRevealEnabled: Boolean,
    lockStoryExpanded: Boolean,
    chainRefreshAfterExpand: Boolean,
) {
    val colors = TuxTheme.colors
    var showStoryAddSheet by remember { mutableStateOf(false) }
    var activeStoryPreview by remember { mutableStateOf<InboxStoryPreviewContent?>(null) }
    var storyPreviewVisible by remember { mutableStateOf(false) }
    var skylightStories by remember { mutableStateOf(DefaultInboxSkylightStories) }
    var readStoryLabels by remember { mutableStateOf(setOf<String>()) }
    var pendingMoveStoryLabel by remember { mutableStateOf<String?>(null) }
    var pendingReadStoryLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var inboxTabRefresh by remember { mutableStateOf<(kotlinx.coroutines.CoroutineScope) -> Unit>({}) }

    fun moveStoryToTail(label: String) {
        val storyIndex = skylightStories.indexOfFirst { it.label == label }
        if (storyIndex < 0) return
        val movedStory = skylightStories[storyIndex]
        skylightStories = buildList {
            skylightStories.forEachIndexed { index, story ->
                if (index != storyIndex) add(story)
            }
            add(movedStory)
        }
    }

    fun closeStoryPreview() {
        pendingReadStoryLabel?.let { readStoryLabels = readStoryLabels + it }
        pendingReadStoryLabel = null
        pendingMoveStoryLabel?.let { moveStoryToTail(it) }
        pendingMoveStoryLabel = null
        storyPreviewVisible = false
    }

    // Keep content stable during fade-out, then clear it after animation.
    LaunchedEffect(storyPreviewVisible, activeStoryPreview?.label) {
        if (storyPreviewVisible || activeStoryPreview == null) return@LaunchedEffect
        delay(InboxStoryPreviewMotion.ExitMs.toLong())
        activeStoryPreview = null
    }

    BackHandler(onBack = onBack)
    BackHandler(enabled = storyPreviewVisible) { closeStoryPreview() }
    BackHandler(enabled = showStoryAddSheet && enableCreateNavigation) { showStoryAddSheet = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.UIPageFlat1),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            InboxNavBar()

            if (topDownStoryRevealEnabled) {
                val density = LocalDensity.current
                val collapsedStoryScrollPx = with(density) {
                    StoryRevealMotion.MaxHeight.toPx().roundToInt()
                }
                val initialScrollPx = if (startExpanded && !autoExpandOnEnter) {
                    0
                } else {
                    collapsedStoryScrollPx
                }
                val scrollState = rememberScrollState(initial = initialScrollPx)
                val storyReveal = rememberIntegratedStoryRevealState(
                    scrollState = scrollState,
                    pullThreshold = pullThreshold,
                    pushThreshold = pushThreshold,
                    maxPullDistance = maxPullDistance,
                    startExpanded = startExpanded,
                    autoExpandOnEnter = autoExpandOnEnter,
                    inboxActive = inboxActive,
                    lockExpanded = lockStoryExpanded,
                    expandOnDrag = expandOnDrag,
                )
                inboxTabRefresh = storyReveal.triggerTabRefresh
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colors.UIPageFlat1)
                ) {
                    InboxStoryRefreshIndicator(refreshState = storyReveal.refreshState)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = storyReveal.refreshState.refreshOffsetPx.roundToInt(),
                                )
                            }
                            .nestedScroll(storyReveal.nestedScrollConnection)
                            .verticalScroll(scrollState),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { storyReveal.headerHeightPx.toDp() })
                                .clipToBounds()
                                .background(colors.UIPageFlat1),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            InboxStoryRevealSlot(
                                storyVisible = true,
                                storySlideProgress = storyReveal.storySlideProgress,
                                storySlideEnabled = false,
                                stories = skylightStories,
                                readStoryLabels = readStoryLabels,
                                onCreateClick = if (enableCreateNavigation) {
                                    { showStoryAddSheet = true }
                                } else {
                                    null
                                },
                                onStoryClick = { storyLabel ->
                                    val isAlreadyRead = storyLabel in readStoryLabels
                                    pendingReadStoryLabel = if (isAlreadyRead) null else storyLabel
                                    pendingMoveStoryLabel = if (isAlreadyRead) null else storyLabel
                                    activeStoryPreview = InboxStoryPreviews.forLabel(storyLabel)
                                    storyPreviewVisible = activeStoryPreview != null
                                },
                                modifier = Modifier.integratedStoryEdgeFadeMask(
                                    progress = storyReveal.storySlideProgress,
                                    maxHeightPx = storyReveal.headerHeightPx,
                                    enabled = !storySlideEnabled,
                                ),
                            )
                        }
                        InboxNotificationCells()
                        InboxSectionTitle(title = "Suggested account")
                        InboxSuggestedCells()
                    }
                }
            } else if (wholePageScrollMode) {
                val pageScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colors.UIPageFlat1)
                        .verticalScroll(pageScrollState),
                ) {
                    InboxStoryRevealSlot(
                        storyVisible = true,
                        storySlideProgress = 1f,
                        storySlideEnabled = false,
                        stories = skylightStories,
                        readStoryLabels = readStoryLabels,
                        onCreateClick = if (enableCreateNavigation) {
                            { showStoryAddSheet = true }
                        } else {
                            null
                        },
                        onStoryClick = { storyLabel ->
                            val isAlreadyRead = storyLabel in readStoryLabels
                            pendingReadStoryLabel = if (isAlreadyRead) null else storyLabel
                            pendingMoveStoryLabel = if (isAlreadyRead) null else storyLabel
                            activeStoryPreview = InboxStoryPreviews.forLabel(storyLabel)
                            storyPreviewVisible = activeStoryPreview != null
                        },
                    )
                    InboxNotificationCells()
                    InboxSectionTitle(title = "Suggested account")
                    InboxSuggestedCells()
                }
            } else {
                val scrollState = rememberScrollState()
                val storyReveal = rememberStoryRevealState(
                    scrollState = scrollState,
                    pullThreshold = pullThreshold,
                    pushThreshold = pushThreshold,
                    expandOnDrag = expandOnDrag,
                    startExpanded = startExpanded,
                    autoExpandOnEnter = autoExpandOnEnter,
                    maxPullDistance = maxPullDistance,
                    inboxActive = inboxActive,
                    lockExpanded = lockStoryExpanded,
                    chainRefreshAfterExpand = chainRefreshAfterExpand,
                )
                inboxTabRefresh = storyReveal.triggerTabRefresh

                // Box overlay: story slot is fixed behind; list slides down to reveal it.
                // clipToBounds prevents the offset list from overflowing onto BottomNavBar.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    InboxStoryRefreshIndicator(refreshState = storyReveal.refreshState)
                    InboxStoryRevealSlot(
                        storyVisible = storyReveal.storyVisible,
                        storySlideProgress = storyReveal.storySlideProgress,
                        storySlideEnabled = storySlideEnabled,
                        stories = skylightStories,
                        readStoryLabels = readStoryLabels,
                        onCreateClick = if (enableCreateNavigation) {
                            { showStoryAddSheet = true }
                        } else {
                            null
                        },
                        onStoryClick = { storyLabel ->
                            val isAlreadyRead = storyLabel in readStoryLabels
                            pendingReadStoryLabel = if (isAlreadyRead) null else storyLabel
                            pendingMoveStoryLabel = if (isAlreadyRead) null else storyLabel
                            activeStoryPreview = InboxStoryPreviews.forLabel(storyLabel)
                            storyPreviewVisible = activeStoryPreview != null
                        },
                        modifier = Modifier.offset {
                            IntOffset(
                                x = 0,
                                y = storyReveal.refreshState.refreshOffsetPx.roundToInt(),
                            )
                        },
                    )
                    if (releaseHintEnabled && storyReveal.releaseHintVisible) {
                        InboxStoryReleaseHint(
                            text = releaseHintText,
                            heightPx = storyReveal.listOffsetPx,
                            alpha = storyReveal.releaseHintAlpha,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (
                                        storyReveal.listOffsetPx +
                                            storyReveal.refreshState.refreshOffsetPx
                                        ).roundToInt(),
                                )
                            }
                            .background(colors.UIPageFlat1)
                            .nestedScroll(storyReveal.nestedScrollConnection)
                            .verticalScroll(scrollState),
                    ) {
                        InboxNotificationCells()
                        InboxSectionTitle(title = "Suggested account")
                        InboxSuggestedCells()
                    }
                }
            }
            InboxBottomNavBar(
                onHomeClick = onHomeTabClick,
                onInboxClick = { inboxTabRefresh(scope) },
            )
        }

        if (enableCreateNavigation) {
            StoryAddBottomSheet(
                visible = showStoryAddSheet,
                onDismiss = { showStoryAddSheet = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        InboxStoryPreviewSheet(
            visible = storyPreviewVisible,
            story = activeStoryPreview ?: InboxStoryPreviews.Lindsey,
            onDismiss = { closeStoryPreview() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun InboxStoryReleaseHint(
    text: String,
    heightPx: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = TuxTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.coerceAtLeast(0f).toDp() })
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        TuxText(
            text = text,
            style = TuxTheme.typography.small_text_1_semibold,
            color = colors.UIText3,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun InboxStoryRefreshIndicator(
    refreshState: StoryRefreshState,
    modifier: Modifier = Modifier,
) {
    if (refreshState.refreshOffsetPx <= 0.5f && !refreshState.isRefreshing) return
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { refreshState.refreshOffsetPx.coerceAtLeast(0f).toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        TuxDualBall(
            isAnimating = refreshState.isRefreshing,
            progress = refreshState.refreshProgress,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun InboxNavBar() {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography

    TuxNavBar(
        backgroundColor = colors.UIPageFlat1,
        startAction = {
            Box(
                modifier = Modifier
                    .size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(InboxAssets.NavCirclePlus),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        centerAction = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TuxText(
                    text = "Inbox",
                    style = typography.h3_bold,
                    color = colors.UIText1,
                )
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .height(InboxDimens.AccountChipH)
                        .clip(RoundedCornerShape(InboxDimens.AccountChipRadius))
                        .background(colors.UIShapeNeutral4)
                        .padding(
                            start = InboxDimens.AccountChipPadStart,
                            end = InboxDimens.AccountChipPadEnd,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Image(
                        painter = painterResource(InboxAssets.AccountDot),
                        contentDescription = null,
                        modifier = Modifier.size(InboxDimens.AccountDot),
                    )
                    Image(
                        painter = painterResource(InboxAssets.AccountChevron),
                        contentDescription = "Switch account",
                        modifier = Modifier.size(InboxDimens.AccountChevron),
                    )
                }
            }
        },
    )
}

@Composable
private fun InboxNotificationCells() {
    mockInboxRows.forEach { row ->
        when (row.kind) {
            InboxRowKind.Category -> InboxCategoryCell(row)
            InboxRowKind.Avatar -> InboxAvatarCell(row)
        }
    }
}

@Composable
private fun InboxCategoryCell(row: InboxRow) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography
    val category = row.category ?: return

    InboxRichCellLayout(
        trailing = row.badgeCount?.let { { InboxCountBadge(it) } },
        leading = {
            InboxCategoryLeading(
                iconRes = category.iconRes,
                backgroundColor = category.kind.backgroundColor(),
            )
        },
        title = {
            TuxText(
                text = row.title,
                style = typography.h4_medium,
                color = colors.UIText1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        message = {
            InboxMessageLine(
                message = row.message,
                timestamp = row.timestamp,
                messageStyle = typography.h4_regular,
            )
        },
    )
}

@Composable
private fun InboxAvatarCell(row: InboxRow) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography
    val avatarRes = row.avatarRes ?: return

    InboxRichCellLayout(
        trailing = row.badgeCount?.let { { InboxCountBadge(it) } },
        leading = {
            InboxDmAvatarLeading(
                avatarRes = avatarRes,
                showOnline = row.showOnline,
            )
        },
        title = {
            TuxText(
                text = row.title,
                style = if (row.badgeCount != null) typography.h4_medium else typography.h4_semibold,
                color = colors.UIText1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        message = {
            InboxMessageLine(
                message = row.message,
                timestamp = row.timestamp,
                messageStyle = if (row.badgeCount != null) typography.h4_regular else typography.p1_regular,
            )
        },
    )
}

@Composable
private fun InboxSuggestedCells() {
    mockSuggestedRows.forEach { row ->
        val colors = TuxTheme.colors
        val typography = TuxTheme.typography

        InboxRichCellLayout(
            trailingFixedSize = false,
            trailing = {
                Row(
                    modifier = Modifier.padding(
                        start = InboxDimens.CellTrailingStartPad,
                        end = InboxDimens.TrailingActionPadEnd,
                        top = InboxDimens.TrailingActionPadV,
                        bottom = InboxDimens.TrailingActionPadV,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TuxButton(
                        onClick = {},
                        text = "Follow",
                        modifier = Modifier
                            .width(InboxDimens.FollowButtonW)
                            .height(InboxDimens.FollowButtonH),
                        buttonVariant = ButtonVariantDefaults.primary(),
                        buttonSize = ButtonSizeDefaults.small(),
                    )
                    Spacer(Modifier.width(InboxDimens.InfoIconPadStart))
                    Image(
                        painter = painterResource(InboxAssets.InfoIcon),
                        contentDescription = "Info",
                        modifier = Modifier
                            .size(InboxDimens.InfoIcon)
                            .padding(end = InboxDimens.InfoIconPadEnd),
                    )
                }
            },
            leading = {
                InboxSuggestedAvatarLeading(avatarRes = row.avatarRes)
            },
            title = {
                TuxText(
                    text = row.title,
                    style = typography.h4_medium,
                    color = colors.UIText1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            message = {
                if (row.avatarStack != null) {
                    InboxSuggestedSubtitleWithStack(
                        subtitle = row.subtitle,
                        stack = row.avatarStack,
                    )
                } else {
                    TuxText(
                        text = row.subtitle,
                        style = typography.p1_regular,
                        color = colors.UIText3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}

@Composable
private fun InboxCategoryLeading(iconRes: Int, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(InboxDimens.LeadingIcon)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(InboxDimens.LeadingCategoryIcon),
        )
    }
}

@Composable
private fun InboxDmAvatarLeading(avatarRes: Int, showOnline: Boolean) {
    Box(modifier = Modifier.size(InboxDimens.LeadingIcon)) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(InboxAssets.AvatarBorder),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        if (showOnline) {
            Image(
                painter = painterResource(InboxAssets.AvatarOnlineDot),
                contentDescription = "Online",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun InboxSuggestedAvatarLeading(avatarRes: Int) {
    val colors = TuxTheme.colors

    Box(modifier = Modifier.size(InboxDimens.LeadingIcon)) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .border(0.5.dp, colors.UIShapeNeutral3, CircleShape),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(InboxAssets.AvatarBorder),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun InboxSuggestedSubtitleWithStack(
    subtitle: String,
    stack: InboxAvatarStack,
) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InboxDimens.CellTimestampGap),
    ) {
        TuxText(
            text = subtitle,
            style = typography.p1_regular,
            color = colors.UIText3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        InboxAvatarStackRow(stack = stack)
    }
}

@Composable
private fun InboxAvatarStackRow(stack: InboxAvatarStack) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InboxDimens.StackOverlap),
    ) {
        stack.avatars.forEach { avatarRes ->
            Image(
                painter = painterResource(avatarRes),
                contentDescription = null,
                modifier = Modifier
                    .size(InboxDimens.StackAvatar)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        if (stack.overflowText != null) {
            Box(
                modifier = Modifier.size(InboxDimens.StackAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(stack.overflowBackgroundRes),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds,
                )
                TuxText(
                    text = stack.overflowText,
                    style = typography.small_text_2_semibold,
                    color = colors.UIText1,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (stack.trailingAvatarRes != null) {
            Image(
                painter = painterResource(stack.trailingAvatarRes),
                contentDescription = null,
                modifier = Modifier
                    .size(InboxDimens.StackAvatar)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun InboxSectionTitle(title: String) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = InboxDimens.CellPadStart,
                end = InboxDimens.CellPadStart,
                top = InboxDimens.SectionTitlePadTop,
                bottom = InboxDimens.SectionTitlePadBottom,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InboxDimens.CellTitleIconGap),
    ) {
        TuxText(
            text = title,
            style = typography.h4_bold,
            color = colors.UIText1,
        )
        Image(
            painter = painterResource(InboxAssets.SectionInfo),
            contentDescription = null,
            modifier = Modifier.size(InboxDimens.SectionTitleIcon),
        )
    }
}

@Composable
private fun InboxRichCellLayout(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    message: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    trailingFixedSize: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = InboxDimens.CellPadStart,
                end = InboxDimens.CellPadEnd,
                top = InboxDimens.CellPadV,
                bottom = InboxDimens.CellPadV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(end = InboxDimens.CellLeadingEndPad)) {
            leading()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(InboxDimens.CellTextGap),
        ) {
            title()
            message()
        }
        trailing?.let {
            if (trailingFixedSize) {
                Box(
                    modifier = Modifier
                        .padding(start = InboxDimens.CellTrailingStartPad)
                        .size(InboxDimens.TrailingBadgeArea),
                    contentAlignment = Alignment.Center,
                ) {
                    it()
                }
            } else {
                it()
            }
        }
    }
}

@Composable
private fun InboxMessageLine(
    message: String,
    timestamp: String?,
    messageStyle: TextStyle,
) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InboxDimens.CellTimestampGap),
    ) {
        TuxText(
            text = message,
            style = messageStyle,
            color = colors.UIText1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (timestamp != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InboxDimens.CellTimestampGap),
            ) {
                Image(
                    painter = painterResource(InboxAssets.TimestampDot),
                    contentDescription = null,
                    modifier = Modifier.size(2.dp),
                )
                TuxText(
                    text = timestamp,
                    style = typography.p1_regular,
                    color = colors.UIText3,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InboxCountBadge(count: Int) {
    val colors = TuxTheme.colors

    TuxAlertBadge(
        variant = TuxAlertBadgeVariant.Number(count = count),
        backgroundColor = colors.UIShapePrimary,
        contentColor = colors.UIShapeText1OnPrimary,
    )
}

// region Mock Data

private enum class InboxRowKind { Category, Avatar }

private enum class InboxCategoryKind {
    Followers,
    Activity,
    System,
    Partners,
}

@Composable
private fun InboxCategoryKind.backgroundColor(): Color = when (this) {
    InboxCategoryKind.Followers -> colorResource(R.color.inbox_category_followers_bg)
    InboxCategoryKind.Activity -> colorResource(R.color.inbox_category_activity_bg)
    InboxCategoryKind.System -> colorResource(R.color.inbox_category_system_bg)
    InboxCategoryKind.Partners -> TuxTheme.colors.UIShapeInfo
}

private data class InboxCategory(
    val iconRes: Int,
    val kind: InboxCategoryKind,
)

private data class InboxAvatarStack(
    val avatars: List<Int>,
    val overflowBackgroundRes: Int = R.drawable.inbox_stack_overflow,
    val overflowText: String? = null,
    val trailingAvatarRes: Int? = null,
)

private data class InboxRow(
    val kind: InboxRowKind,
    val title: String,
    val message: String,
    val timestamp: String? = null,
    val badgeCount: Int? = null,
    val category: InboxCategory? = null,
    val avatarRes: Int? = null,
    val showOnline: Boolean = false,
)

private data class InboxSuggestedRow(
    val title: String,
    val subtitle: String,
    val avatarRes: Int,
    val avatarStack: InboxAvatarStack? = null,
)

private val mockInboxRows = listOf(
    InboxRow(
        kind = InboxRowKind.Category,
        title = "New Followers",
        message = "Rhianna started following you.",
        badgeCount = 9,
        category = InboxCategory(InboxAssets.CategoryFollowersIcon, InboxCategoryKind.Followers),
    ),
    InboxRow(
        kind = InboxRowKind.Category,
        title = "Activity",
        message = "Jaela liked your video.",
        badgeCount = 1,
        category = InboxCategory(InboxAssets.CategoryActivityIcon, InboxCategoryKind.Activity),
    ),
    InboxRow(
        kind = InboxRowKind.Category,
        title = "System notifications",
        message = "TikTok:Updates to your post performance",
        timestamp = "1h",
        badgeCount = 1,
        category = InboxCategory(InboxAssets.CategorySystemIcon, InboxCategoryKind.System),
    ),
    InboxRow(
        kind = InboxRowKind.Category,
        title = "Featured partners",
        message = "Here comes a new brand{brand_name}",
        timestamp = "1h",
        badgeCount = 1,
        category = InboxCategory(InboxAssets.CategoryPartnersIcon, InboxCategoryKind.Partners),
    ),
    InboxRow(
        kind = InboxRowKind.Avatar,
        title = "Cenis Grimm",
        message = "Hello how r u recently",
        timestamp = "1h",
        badgeCount = 1,
        avatarRes = R.drawable.inbox_avatar_cenis,
        showOnline = true,
    ),
    InboxRow(
        kind = InboxRowKind.Avatar,
        title = "Tommy Tang",
        message = "Hello how r u recently",
        timestamp = "1h",
        avatarRes = R.drawable.inbox_avatar_tommy,
    ),
    InboxRow(
        kind = InboxRowKind.Avatar,
        title = "summer",
        message = "Hello how r u recently",
        timestamp = "1h",
        avatarRes = R.drawable.inbox_avatar_summer,
    ),
)

private val mockSuggestedRows = listOf(
    InboxSuggestedRow(
        title = "🦆🦆",
        subtitle = "Friends with",
        avatarRes = R.drawable.inbox_suggested_duck,
        avatarStack = InboxAvatarStack(
            avatars = listOf(
                R.drawable.inbox_stack_avatar_1,
                R.drawable.inbox_stack_avatar_2,
            ),
            overflowText = "+6",
        ),
    ),
    InboxSuggestedRow(
        title = "Sunsun77",
        subtitle = "Follows",
        avatarRes = R.drawable.inbox_suggested_sunsun,
        avatarStack = InboxAvatarStack(
            avatars = listOf(
                R.drawable.inbox_stack_avatar_1,
                R.drawable.inbox_stack_avatar_2,
            ),
            overflowBackgroundRes = R.drawable.inbox_stack_overflow_alt,
            trailingAvatarRes = R.drawable.inbox_stack_avatar_3,
        ),
    ),
    InboxSuggestedRow(
        title = "wanda",
        subtitle = "You may know Wang",
        avatarRes = R.drawable.inbox_suggested_wanda,
    ),
)

// endregion
