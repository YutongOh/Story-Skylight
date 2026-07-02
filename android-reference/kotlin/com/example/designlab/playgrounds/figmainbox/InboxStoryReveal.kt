package com.example.designlab.playgrounds.figmainbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.bytedance.tux.compose.TuxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object StoryRevealMotion {
    const val ExpandMs = 450
    const val CollapseMs = 350
    const val StorySlideMs = 300
    const val SettleBackMs = 250
    const val AutoExpandDelayMs = 400
    const val TabRefreshExpandMs = 200
    const val TabRefreshPullMs = 340
    const val TabRefreshSequenceMs = 460
    const val RefreshDurationMs = 1200
    const val PushFlingVelocity = 1500f
    // Drag damping: how much the list moves per finger pixel (< 1 = feels natural, not 1:1)
    const val PullDamping = 0.65f
    val PullThreshold = 32.dp   // Minimum drag to trigger expand on release
    val PushThreshold = 30.dp   // Minimum upward swipe to trigger collapse
    val RefreshThreshold = 32.dp
    val RefreshIndicatorHeight = 56.dp
    val RefreshMaxPullDistance = 72.dp
    val MaxHeight = 118.dp      // Fully-revealed story height
    val V1MaxPullDistance = 72.dp // V1 manual pull cap; expand still reaches MaxHeight
}

@Stable
class StoryRefreshState internal constructor(
    val refreshOffsetPx: Float,
    val refreshProgress: Float,
    val isRefreshing: Boolean,
)

/**
 * [listOffsetPx]: how far the list is shifted down.
 *   0            → story hidden behind list (collapsed)
 *   maxHeightPx  → story fully visible above list (expanded)
 */
@Stable
class StoryRevealState internal constructor(
    val nestedScrollConnection: NestedScrollConnection,
    val listOffsetPx: Float,
    val storyVisible: Boolean,
    val storySlideProgress: Float,
    val releaseHintVisible: Boolean,
    val releaseHintAlpha: Float,
    val refreshState: StoryRefreshState,
    val triggerTabRefresh: (CoroutineScope) -> Unit,
)

@Stable
class IntegratedStoryRevealState internal constructor(
    val nestedScrollConnection: NestedScrollConnection,
    val headerHeightPx: Float,
    val storySlideProgress: Float,
    val storyAlpha: Float,
    val refreshState: StoryRefreshState,
    val triggerTabRefresh: (CoroutineScope) -> Unit,
)

@Composable
fun rememberStoryRevealState(
    scrollState: ScrollState,
    pullThreshold: Dp = StoryRevealMotion.PullThreshold,
    pushThreshold: Dp = StoryRevealMotion.PushThreshold,
    expandOnDrag: Boolean = false,
    startExpanded: Boolean = false,
    autoExpandOnEnter: Boolean = false,
    maxPullDistance: Dp = StoryRevealMotion.MaxHeight,
    inboxActive: Boolean = true,
    lockExpanded: Boolean = false,
    chainRefreshAfterExpand: Boolean = false,
): StoryRevealState {
    val density = LocalDensity.current
    val revealHeightPx = with(density) { StoryRevealMotion.MaxHeight.toPx() }
    val maxPullPx = with(density) { maxPullDistance.toPx() }
    val pullThresholdPx = with(density) { pullThreshold.toPx() }
    val pushThresholdPx = with(density) { pushThreshold.toPx() }
    val refreshThresholdPx = with(density) { StoryRevealMotion.RefreshThreshold.toPx() }
    val refreshIndicatorHeightPx = with(density) { StoryRevealMotion.RefreshIndicatorHeight.toPx() }
    val refreshMaxPullPx = with(density) { StoryRevealMotion.RefreshMaxPullDistance.toPx() }
    val motionEasing = TuxTheme.animation.easeOutStandard
    val scope = rememberCoroutineScope()

    // Animatable is used only for settle animations (release/expand/collapse).
    // Drag phase uses plain state updates to avoid per-frame coroutine launches.
    // autoExpandOnEnter starts collapsed and plays expand motion on enter.
    val initialExpanded = lockExpanded || (startExpanded && !autoExpandOnEnter)
    val initialOffset = if (initialExpanded) revealHeightPx else 0f
    val initialSlideProgress = if (initialExpanded) 1f else 0f
    val listOffset = remember(initialExpanded, revealHeightPx) { Animatable(initialOffset) }
    var dragOffsetPx by remember(initialExpanded, revealHeightPx) { mutableFloatStateOf(initialOffset) }
    val storySlideProgress = remember(initialExpanded) { Animatable(initialSlideProgress) }
    var isExpanded by remember(initialExpanded) { mutableStateOf(initialExpanded) }
    var isAnimating by remember { mutableStateOf(false) }
    var isStoryVisible by remember(initialExpanded) { mutableStateOf(initialExpanded) }
    var pushAccum by remember { mutableFloatStateOf(0f) }
    val refreshOffset = remember { Animatable(0f) }
    var dragRefreshOffsetPx by remember { mutableFloatStateOf(0f) }
    var chainRefreshGestureActive by remember { mutableStateOf(false) }
    var autoExpandOnEnterConsumed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isRefreshSettling by remember { mutableStateOf(false) }
    fun currentOffsetPx(): Float = if (isAnimating) listOffset.value else dragOffsetPx
    fun currentRefreshOffsetPx(): Float = if (isRefreshing || isRefreshSettling) {
        refreshOffset.value
    } else {
        dragRefreshOffsetPx
    }

    suspend fun animateExpand(expandMs: Int = StoryRevealMotion.ExpandMs) {
        val startOffset = dragOffsetPx
        val slideMs = minOf(expandMs, StoryRevealMotion.StorySlideMs)
        isExpanded = true
        isAnimating = true
        isStoryVisible = true
        pushAccum = 0f
        try {
            listOffset.snapTo(startOffset)
            coroutineScope {
                val slideJob = launch {
                    storySlideProgress.snapTo((startOffset / revealHeightPx).coerceIn(0f, 1f))
                    storySlideProgress.animateTo(
                        1f,
                        tween(slideMs, easing = motionEasing),
                    )
                }
                listOffset.animateTo(
                    revealHeightPx,
                    tween(expandMs, easing = motionEasing),
                )
                slideJob.join()
            }
        } finally {
            dragOffsetPx = listOffset.value
            isAnimating = false
        }
    }

    suspend fun animateCollapse() {
        if (lockExpanded) return
        val startOffset = dragOffsetPx
        isExpanded = false
        isAnimating = true
        pushAccum = 0f
        try {
            listOffset.snapTo(startOffset)
            coroutineScope {
                val slideJob = launch {
                    storySlideProgress.snapTo((startOffset / revealHeightPx).coerceIn(0f, 1f))
                    storySlideProgress.animateTo(
                        0f,
                        tween(StoryRevealMotion.CollapseMs, easing = motionEasing),
                    )
                }
                listOffset.animateTo(
                    0f,
                    tween(StoryRevealMotion.CollapseMs, easing = motionEasing),
                )
                slideJob.join()
            }
        } finally {
            dragOffsetPx = listOffset.value
            isAnimating = false
            if (listOffset.value <= 0.5f) {
                storySlideProgress.snapTo(0f)
                isStoryVisible = false
            }
        }
    }

    suspend fun settle() {
        if (isExpanded || isAnimating) return
        if (currentOffsetPx() >= pullThresholdPx && scrollState.value == 0) {
            animateExpand()
        } else {
            // Not enough drag — snap back cleanly.
            val startOffset = dragOffsetPx
            isAnimating = true
            try {
                listOffset.snapTo(startOffset)
                listOffset.animateTo(
                    0f,
                    tween(StoryRevealMotion.SettleBackMs, easing = motionEasing),
                )
            } finally {
                dragOffsetPx = listOffset.value
                isAnimating = false
                storySlideProgress.snapTo(0f)
                isStoryVisible = false
            }
        }
    }

    suspend fun animateTabRefreshPull(durationMs: Int = StoryRevealMotion.TabRefreshPullMs) {
        dragRefreshOffsetPx = 0f
        refreshOffset.snapTo(0f)
        refreshOffset.animateTo(
            refreshIndicatorHeightPx,
            tween(durationMs, easing = motionEasing),
        )
        dragRefreshOffsetPx = refreshOffset.value
    }

    suspend fun runOverlayTabRefreshSequence() {
        val listScrolled = scrollState.value > 0
        val needExpand = !isExpanded
        if (!listScrolled && !needExpand) {
            animateTabRefreshPull()
            settleRefresh()
            return
        }
        coroutineScope {
            val seqMs = StoryRevealMotion.TabRefreshSequenceMs
            val jobs = buildList {
                if (listScrolled) {
                    add(async {
                        scrollState.animateScrollTo(
                            0,
                            tween(seqMs, easing = motionEasing),
                        )
                    })
                }
                if (needExpand) {
                    add(async { animateExpand(seqMs) })
                }
                add(async { animateTabRefreshPull(seqMs) })
            }
            jobs.forEach { it.await() }
        }
        settleRefresh()
    }

    suspend fun settleRefresh() {
        if (isRefreshSettling || isRefreshing) return
        val startOffset = currentRefreshOffsetPx()
        isRefreshSettling = true
        try {
            refreshOffset.snapTo(startOffset)
            if (startOffset >= refreshThresholdPx) {
                isRefreshing = true
                refreshOffset.animateTo(
                    refreshIndicatorHeightPx,
                    tween(StoryRevealMotion.SettleBackMs, easing = motionEasing),
                )
                delay(StoryRevealMotion.RefreshDurationMs.toLong())
            }
            refreshOffset.animateTo(
                0f,
                tween(StoryRevealMotion.SettleBackMs, easing = motionEasing),
            )
        } finally {
            dragRefreshOffsetPx = refreshOffset.value
            isRefreshing = false
            isRefreshSettling = false
        }
    }

    val nestedScrollConnection = remember(scrollState) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y >= 0f) pushAccum = 0f
                if (available.y < 0f) chainRefreshGestureActive = false

                if (isRefreshing) return available

                if (
                    isExpanded &&
                    !isAnimating &&
                    dragRefreshOffsetPx > 0f &&
                    available.y < 0f
                ) {
                    val newOffset = (dragRefreshOffsetPx + available.y).coerceIn(0f, refreshMaxPullPx)
                    val consumedY = newOffset - dragRefreshOffsetPx
                    dragRefreshOffsetPx = newOffset
                    return Offset(x = 0f, y = consumedY)
                }

                // Pulling-down intermediate state: upward drag must first consume
                // the reveal offset back to 0, never scroll the list.
                if (!isExpanded && !isAnimating && dragOffsetPx > 0f && available.y < 0f) {
                    val newOffset = (dragOffsetPx + available.y).coerceIn(0f, maxPullPx)
                    dragOffsetPx = newOffset
                    if (chainRefreshAfterExpand && expandOnDrag && newOffset <= 0.5f) {
                        isStoryVisible = false
                        chainRefreshGestureActive = false
                    }
                    return available
                }

                // Expanded or animating: consume ALL upward scroll.
                // This guarantees story collapses before list can scroll up.
                if (!lockExpanded && (isExpanded || isAnimating) && available.y < 0f) {
                    if (isExpanded && !isAnimating) {
                        pushAccum += -available.y
                        if (pushAccum >= pushThresholdPx) {
                            scope.launch { animateCollapse() }
                        }
                    }
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (isRefreshing) return available
                if ((isExpanded || isAnimating) && available.y < 0f) return available

                if (
                    isExpanded &&
                    !isAnimating &&
                    scrollState.value == 0 &&
                    available.y > 0f
                ) {
                    val newOffset = (dragRefreshOffsetPx + available.y).coerceIn(0f, refreshMaxPullPx)
                    dragRefreshOffsetPx = newOffset
                    if (chainRefreshAfterExpand && expandOnDrag) {
                        chainRefreshGestureActive = true
                    }
                    return available
                }

                // Pull-down at top: track drag offset (list moves down in real time).
                // V2 chain mode reveals story immediately, but delays settle animation
                // so the same gesture can continue into pull-to-refresh.
                if (!isExpanded && !isAnimating && scrollState.value == 0 && available.y > 0f) {
                    if (chainRefreshAfterExpand && expandOnDrag) {
                        val currentOffset = dragOffsetPx
                        val newOffset = (currentOffset + available.y).coerceIn(0f, revealHeightPx)
                        dragOffsetPx = newOffset
                        isStoryVisible = newOffset > 0.5f
                        val consumedFingerPx = newOffset - currentOffset
                        val remainingFingerPx = (available.y - consumedFingerPx).coerceAtLeast(0f)
                        if (newOffset >= revealHeightPx - 0.5f) {
                            isExpanded = true
                            isStoryVisible = true
                            chainRefreshGestureActive = true
                            dragOffsetPx = revealHeightPx
                            if (remainingFingerPx > 0f) {
                                dragRefreshOffsetPx = (dragRefreshOffsetPx + remainingFingerPx)
                                    .coerceIn(0f, refreshMaxPullPx)
                            }
                        }
                    } else {
                        val newOffset = (dragOffsetPx + available.y * StoryRevealMotion.PullDamping)
                            .coerceIn(0f, maxPullPx)
                        dragOffsetPx = newOffset
                        if (expandOnDrag && newOffset >= pullThresholdPx) {
                            scope.launch { animateExpand() }
                        }
                    }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentRefreshOffsetPx() > 0f || isRefreshing) {
                    settleRefresh()
                    chainRefreshGestureActive = false
                    return available
                }
                if (!lockExpanded && (isExpanded || isAnimating) && available.y < 0f) {
                    if (
                        isExpanded &&
                        !isAnimating &&
                        (
                            pushAccum >= pushThresholdPx ||
                                available.y <= -StoryRevealMotion.PushFlingVelocity
                            )
                    ) {
                        scope.launch { animateCollapse() }
                    }
                    return available
                }
                if (!isExpanded && !isAnimating && currentOffsetPx() > 0f) {
                    settle()
                    chainRefreshGestureActive = false
                    // We handled release/settle for reveal; consume fling to avoid rebound.
                    return available
                }
                chainRefreshGestureActive = false
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!lockExpanded && (isExpanded || isAnimating) && available.y < 0f) return available
                // Keep post-fling passive to avoid double-settle bounce.
                return Velocity.Zero
            }
        }
    }

    // Fallback: slow drag release without fling.
    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (wasScrolling && !scrollState.isScrollInProgress) {
            if (currentRefreshOffsetPx() > 0f && !isRefreshing) {
                settleRefresh()
                chainRefreshGestureActive = false
            } else if (!isExpanded && !isAnimating && currentOffsetPx() > 0f) {
                settle()
                chainRefreshGestureActive = false
            } else if (chainRefreshGestureActive) {
                chainRefreshGestureActive = false
            }
        }
        wasScrolling = scrollState.isScrollInProgress
    }

    LaunchedEffect(inboxActive, autoExpandOnEnter, autoExpandOnEnterConsumed) {
        if (lockExpanded) return@LaunchedEffect
        if (!inboxActive || !autoExpandOnEnter || autoExpandOnEnterConsumed) return@LaunchedEffect
        autoExpandOnEnterConsumed = true
        delay(StoryRevealMotion.AutoExpandDelayMs.toLong())
        if (!inboxActive || isExpanded || isAnimating) return@LaunchedEffect
        animateExpand()
    }

    val offsetPx = currentOffsetPx()
    val refreshOffsetPx = currentRefreshOffsetPx()
    val releaseHintVisible = !isExpanded && !isAnimating && offsetPx > 0f
    val releaseHintAlpha = if (pullThresholdPx > 0f) {
        (offsetPx / pullThresholdPx).coerceIn(0f, 1f)
    } else {
        1f
    }

    return StoryRevealState(
        nestedScrollConnection = nestedScrollConnection,
        listOffsetPx = offsetPx,
        storyVisible = isStoryVisible,
        storySlideProgress = if (isAnimating) {
            storySlideProgress.value
        } else {
            (offsetPx / revealHeightPx).coerceIn(0f, 1f)
        },
        releaseHintVisible = releaseHintVisible,
        releaseHintAlpha = releaseHintAlpha,
        refreshState = StoryRefreshState(
            refreshOffsetPx = refreshOffsetPx,
            refreshProgress = (refreshOffsetPx / refreshThresholdPx).coerceIn(0f, 1f),
            isRefreshing = isRefreshing,
        ),
        triggerTabRefresh = { launchScope ->
            launchScope.launch {
                if (isRefreshing || isRefreshSettling || isAnimating) return@launch
                runOverlayTabRefreshSequence()
            }
        },
    )
}

@Composable
fun rememberIntegratedStoryRevealState(
    scrollState: ScrollState,
    pullThreshold: Dp = StoryRevealMotion.PullThreshold,
    pushThreshold: Dp = StoryRevealMotion.PushThreshold,
    maxPullDistance: Dp = StoryRevealMotion.MaxHeight,
    startExpanded: Boolean = true,
    autoExpandOnEnter: Boolean = false,
    inboxActive: Boolean = true,
    lockExpanded: Boolean = false,
    expandOnDrag: Boolean = false,
): IntegratedStoryRevealState {
    val density = LocalDensity.current
    val revealHeightPx = with(density) { StoryRevealMotion.MaxHeight.toPx() }
    val pullThresholdPx = with(density) { pullThreshold.toPx() }
    val pushThresholdPx = with(density) { pushThreshold.toPx() }
    val maxPullPx = with(density) { maxPullDistance.toPx() }
    val minHiddenPxForPullCap = (revealHeightPx - maxPullPx)
        .toInt()
        .coerceIn(0, revealHeightPx.toInt())
    val refreshThresholdPx = with(density) { StoryRevealMotion.RefreshThreshold.toPx() }
    val refreshIndicatorHeightPx = with(density) { StoryRevealMotion.RefreshIndicatorHeight.toPx() }
    val refreshMaxPullPx = with(density) { StoryRevealMotion.RefreshMaxPullDistance.toPx() }
    val motionEasing = TuxTheme.animation.easeOutStandard
    var lastDragDown by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    val refreshOffset = remember { Animatable(0f) }
    var dragRefreshOffsetPx by remember { mutableFloatStateOf(0f) }
    var autoExpandOnEnterConsumed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isRefreshSettling by remember { mutableStateOf(false) }
    fun currentRefreshOffsetPx(): Float = if (isRefreshing || isRefreshSettling) {
        refreshOffset.value
    } else {
        dragRefreshOffsetPx
    }

    suspend fun expand() {
        isSettling = true
        try {
            scrollState.animateScrollTo(
                0,
                tween(StoryRevealMotion.ExpandMs, easing = motionEasing),
            )
        } finally {
            isSettling = false
        }
    }

    suspend fun collapse() {
        isSettling = true
        try {
            scrollState.animateScrollTo(
                revealHeightPx.toInt(),
                tween(StoryRevealMotion.CollapseMs, easing = motionEasing),
            )
        } finally {
            isSettling = false
        }
    }

    suspend fun settle() {
        if (isSettling) return
        val hiddenPx = scrollState.value.coerceIn(0, revealHeightPx.toInt()).toFloat()
        val visiblePx = revealHeightPx - hiddenPx
        if (lastDragDown) {
            if (visiblePx >= pullThresholdPx) {
                expand()
            } else {
                collapse()
            }
        } else {
            if (hiddenPx >= pushThresholdPx) {
                collapse()
            } else {
                expand()
            }
        }
    }

    suspend fun animateTabRefreshPull(durationMs: Int = StoryRevealMotion.TabRefreshPullMs) {
        dragRefreshOffsetPx = 0f
        refreshOffset.snapTo(0f)
        refreshOffset.animateTo(
            refreshIndicatorHeightPx,
            tween(durationMs, easing = motionEasing),
        )
        dragRefreshOffsetPx = refreshOffset.value
    }

    suspend fun runIntegratedTabRefreshSequence() {
        if (scrollState.value <= 0) {
            animateTabRefreshPull()
            settleRefresh()
            return
        }
        coroutineScope {
            val seqMs = StoryRevealMotion.TabRefreshSequenceMs
            val scrollJob = async {
                scrollState.animateScrollTo(
                    0,
                    tween(seqMs, easing = motionEasing),
                )
            }
            val pullJob = async { animateTabRefreshPull(seqMs) }
            scrollJob.await()
            pullJob.await()
        }
        settleRefresh()
    }

    suspend fun settleRefresh() {
        if (isRefreshSettling || isRefreshing) return
        val startOffset = currentRefreshOffsetPx()
        isRefreshSettling = true
        try {
            refreshOffset.snapTo(startOffset)
            if (startOffset >= refreshThresholdPx) {
                isRefreshing = true
                refreshOffset.animateTo(
                    refreshIndicatorHeightPx,
                    tween(StoryRevealMotion.SettleBackMs, easing = motionEasing),
                )
                delay(StoryRevealMotion.RefreshDurationMs.toLong())
            }
            refreshOffset.animateTo(
                0f,
                tween(StoryRevealMotion.SettleBackMs, easing = motionEasing),
            )
        } finally {
            dragRefreshOffsetPx = refreshOffset.value
            isRefreshing = false
            isRefreshSettling = false
        }
    }

    val nestedScrollConnection = remember(scrollState) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (isRefreshing) return available
                if (dragRefreshOffsetPx > 0f && available.y < 0f) {
                    val newOffset = (dragRefreshOffsetPx + available.y).coerceIn(0f, refreshMaxPullPx)
                    val consumedY = newOffset - dragRefreshOffsetPx
                    dragRefreshOffsetPx = newOffset
                    return Offset(x = 0f, y = consumedY)
                }
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    lastDragDown = available.y > 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (isRefreshing) return available
                if (scrollState.value == 0 && available.y > 0f) {
                    dragRefreshOffsetPx = (dragRefreshOffsetPx + available.y)
                        .coerceIn(0f, refreshMaxPullPx)
                    lastDragDown = true
                    return available
                }
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    lastDragDown = available.y > 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentRefreshOffsetPx() > 0f || isRefreshing) {
                    settleRefresh()
                    return available
                }
                if (lockExpanded) return Velocity.Zero
                val hiddenPx = scrollState.value.coerceIn(0, revealHeightPx.toInt())
                if (available.y < 0f && hiddenPx < revealHeightPx.toInt()) {
                    lastDragDown = false
                    collapse()
                    return available
                }
                if (hiddenPx in 1 until revealHeightPx.toInt()) {
                    lastDragDown = available.y > 0f
                    settle()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress, expandOnDrag, lockExpanded) {
        if (expandOnDrag || lockExpanded || isSettling || isRefreshing || isRefreshSettling) return@LaunchedEffect
        if (!scrollState.isScrollInProgress || !lastDragDown) return@LaunchedEffect
        val hiddenPx = scrollState.value
        if (hiddenPx in 1 until minHiddenPxForPullCap) {
            scrollState.scrollTo(minHiddenPxForPullCap)
        }
    }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (wasScrolling && !scrollState.isScrollInProgress) {
            val hiddenPx = scrollState.value.coerceIn(0, revealHeightPx.toInt())
            if (currentRefreshOffsetPx() > 0f && !isRefreshing) {
                settleRefresh()
            } else if (!lockExpanded && !isSettling && hiddenPx in 1 until revealHeightPx.toInt()) {
                settle()
            }
        }
        wasScrolling = scrollState.isScrollInProgress
    }

    LaunchedEffect(inboxActive, autoExpandOnEnter, startExpanded, autoExpandOnEnterConsumed) {
        if (lockExpanded) return@LaunchedEffect
        if (!inboxActive || !autoExpandOnEnter || !startExpanded || autoExpandOnEnterConsumed) {
            return@LaunchedEffect
        }
        autoExpandOnEnterConsumed = true
        delay(StoryRevealMotion.AutoExpandDelayMs.toLong())
        if (!inboxActive) return@LaunchedEffect
        expand()
    }

    val hiddenPx = scrollState.value.coerceIn(0, revealHeightPx.toInt()).toFloat()
    val storyProgress = if (lockExpanded) {
        1f
    } else {
        (1f - hiddenPx / revealHeightPx).coerceIn(0f, 1f)
    }
    val refreshOffsetPx = currentRefreshOffsetPx()

    return IntegratedStoryRevealState(
        nestedScrollConnection = nestedScrollConnection,
        headerHeightPx = revealHeightPx,
        storySlideProgress = storyProgress,
        storyAlpha = storyProgress,
        refreshState = StoryRefreshState(
            refreshOffsetPx = refreshOffsetPx,
            refreshProgress = (refreshOffsetPx / refreshThresholdPx).coerceIn(0f, 1f),
            isRefreshing = isRefreshing,
        ),
        triggerTabRefresh = { launchScope ->
            launchScope.launch {
                if (isRefreshing || isRefreshSettling || isSettling) return@launch
                runIntegratedTabRefreshSequence()
            }
        },
    )
}

/**
 * Story slot rendered at a fixed [StoryRevealMotion.MaxHeight] — always behind the list.
 * Height never animates; the list slides over it to reveal/hide.
 */
@Composable
fun InboxStoryRevealSlot(
    storyVisible: Boolean,
    storySlideProgress: Float,
    storySlideEnabled: Boolean,
    stories: List<InboxSkylightStory> = DefaultInboxSkylightStories,
    readStoryLabels: Set<String> = emptySet(),
    onCreateClick: (() -> Unit)? = null,
    onStoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = TuxTheme.colors
    val density = LocalDensity.current
    // Full-distance emergence from list top boundary:
    // start fully hidden behind the boundary, then slide to final position.
    val startOffset = with(density) { StoryRevealMotion.MaxHeight.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(StoryRevealMotion.MaxHeight)
            .clipToBounds()
            .background(colors.UIPageFlat1),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (storyVisible) {
            InboxSkylight(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = if (storySlideEnabled) {
                            startOffset * (1f - storySlideProgress)
                        } else {
                            0f
                        }
                    },
                stories = stories,
                readStoryLabels = readStoryLabels,
                onCreateClick = onCreateClick,
                onStoryClick = onStoryClick,
            )
        }
    }
}

internal fun integratedStoryEdgeFadeEndFraction(progress: Float, maxHeightPx: Float): Float? {
    if (progress <= 0.001f || progress >= 0.999f) return null
    val boundaryPct = progress * 100f
    val maxFadeBandPct = minOf(34f, maxOf(12f, (40f / maxHeightPx) * 100f))
    val fadeBandPct = maxFadeBandPct * (1f - progress)
    if (fadeBandPct < 0.05f) return null
    return minOf(boundaryPct, fadeBandPct) / 100f
}

fun Modifier.integratedStoryEdgeFadeMask(
    progress: Float,
    maxHeightPx: Float,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val fadeEndFraction = integratedStoryEdgeFadeEndFraction(progress, maxHeightPx) ?: return this
    return drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    fadeEndFraction to Color.Black,
                    1f to Color.Black,
                ),
                startY = 0f,
                endY = size.height,
            ),
            topLeft = Offset.Zero,
            size = size,
            blendMode = BlendMode.DstIn,
        )
    }
}
