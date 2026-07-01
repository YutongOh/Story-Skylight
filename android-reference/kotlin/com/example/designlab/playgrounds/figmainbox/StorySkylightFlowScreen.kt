package com.example.designlab.playgrounds.figmainbox

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.designlab.playgrounds.abulmv1.AbulmV1Screen
import com.example.designlab.playgrounds.feed.FeedScreen

private const val StorySkylightTabCrossfadeMs = 150

/** Tab 切换只走 SideEffect，避免 mount/unmount 触发导航栏图标回弹闪烁。 */
@Composable
private fun StorySkylightFlowSystemBars(showFeed: Boolean) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val entryLightStatus = controller.isAppearanceLightStatusBars
        val entryLightNav = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = entryLightStatus
            controller.isAppearanceLightNavigationBars = entryLightNav
        }
    }
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val lightSystemIcons = !showFeed
        controller.isAppearanceLightStatusBars = lightSystemIcons
        controller.isAppearanceLightNavigationBars = lightSystemIcons
    }
}

/**
 * Story Skylight playground flow: Feed (default) ↔ Inbox (V1/V2/V3/V4).
 * Feed matches V1 Effect Loading 1.2s; Inbox tab returns from Feed.
 */
@Composable
fun StorySkylightFlowScreen(
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
    startOnFeed: Boolean = true,
    releaseHintEnabled: Boolean = false,
    releaseHintText: String = "Release to show story",
    topDownStoryRevealEnabled: Boolean = false,
    lockStoryExpanded: Boolean = false,
    chainRefreshAfterExpand: Boolean = false,
) {
    var showFeed by remember { mutableStateOf(startOnFeed) }
    val feedVisibility by animateFloatAsState(
        targetValue = if (showFeed) 1f else 0f,
        animationSpec = tween(
            durationMillis = StorySkylightTabCrossfadeMs,
            easing = FastOutSlowInEasing,
        ),
        label = "storySkylightFeedVisibility",
    )

    StorySkylightFlowSystemBars(showFeed = showFeed)

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - feedVisibility)
                .zIndex(if (showFeed) 0f else 1f),
        ) {
            FigmaInboxScreen(
                onBack = onBack,
                onHomeTabClick = { showFeed = true },
                inboxActive = !showFeed,
                pullThreshold = pullThreshold,
                pushThreshold = pushThreshold,
                storySlideEnabled = storySlideEnabled,
                expandOnDrag = expandOnDrag,
                startExpanded = startExpanded,
                autoExpandOnEnter = autoExpandOnEnter,
                maxPullDistance = maxPullDistance,
                wholePageScrollMode = wholePageScrollMode,
                enableCreateNavigation = enableCreateNavigation,
                releaseHintEnabled = releaseHintEnabled,
                releaseHintText = releaseHintText,
                topDownStoryRevealEnabled = topDownStoryRevealEnabled,
                lockStoryExpanded = lockStoryExpanded,
                chainRefreshAfterExpand = chainRefreshAfterExpand,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(feedVisibility)
                .zIndex(if (showFeed) 1f else 0f),
        ) {
            FeedScreen(
                onBack = onBack,
                onInboxTabClick = { showFeed = false },
                manageSystemBars = false,
                backEnabled = showFeed,
                albumContent = { onAlbumBack, _ ->
                    AbulmV1Screen(onBack = onAlbumBack, manageSystemBars = false)
                },
            )
        }
    }
}
