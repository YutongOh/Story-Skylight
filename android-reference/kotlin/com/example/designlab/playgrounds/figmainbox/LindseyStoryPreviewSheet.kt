package com.example.designlab.playgrounds.figmainbox

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bytedance.tux.R as TuxR
import com.bytedance.tux.compose.TuxIcon
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.darkColors
import com.bytedance.tux.compose.res.VectorResource
import com.example.designlab.playgrounds.R
import kotlinx.coroutines.launch

private object LindseyStoryDimens {
    val ScreenCorner = 16.dp
    val StoryScreenTop = 44.dp

    val TopUiPadH = 12.dp // total progress width = screenWidth - 24dp
    val ProgressGap = 4.dp
    val ProgressH = 4.dp
    const val StoryDurationMs = 3500
    val TopBarH = 44.dp
    val HeaderAvatar = 32.dp
    val HeaderGap = 8.dp
    val HeaderNameGap = 4.dp
    val HeaderTextBlockH = 22.dp
    val TopActionGap = 16.dp
    val TopActionPadStart = 12.dp
    val TopActionPadEnd = 16.dp
    val TopActionPadV = 10.dp

    val BottomInteractionPadH = 12.dp
    val BottomInteractionBottom = 36.dp
    val MessageRowH = 64.dp
    val MessageBubbleH = 48.dp
    val MessageBubbleRadius = 189.dp
    val MessagePadStart = 16.dp
    val MessagePadEnd = 12.dp
    val MessageEmojiGap = 10.dp
    val MessageActionGap = 16.dp
    val MessageContainerPadEnd = 6.dp
    val SwipeThreshold = 48.dp

}

internal object InboxStoryPreviewMotion {
    const val EnterMs = 180
    const val ExitMs = 150
    const val PhotoCrossfadeMs = 250
}

data class InboxStoryPreviewContent(
    val label: String,
    val avatarRes: Int,
    val storyPhotos: List<Int>,
    val timeText: String = "3h ago",
)

internal object InboxStoryPreviews {
    val Lindsey = InboxStoryPreviewContent(
        label = "Lindsey",
        avatarRes = R.drawable.inbox_story_lindsey,
        storyPhotos = listOf(
            R.drawable.lindsey_story_photo_1,
            R.drawable.lindsey_story_photo_2,
            R.drawable.lindsey_story_photo_3,
            R.drawable.lindsey_story_photo_4,
        ),
    )
    val Maren = InboxStoryPreviewContent(
        label = "Maren",
        avatarRes = R.drawable.inbox_story_maren,
        storyPhotos = listOf(
            R.drawable.maren_story_photo_1,
            R.drawable.maren_story_photo_2,
            R.drawable.maren_story_photo_3,
        ),
    )
    val Alena = InboxStoryPreviewContent(
        label = "Alena",
        avatarRes = R.drawable.inbox_story_alena,
        storyPhotos = listOf(
            R.drawable.alena_story_photo_1,
            R.drawable.alena_story_photo_2,
            R.drawable.alena_story_photo_3,
        ),
    )
    val Rayna = InboxStoryPreviewContent(
        label = "Rayna",
        avatarRes = R.drawable.inbox_story_rayna,
        storyPhotos = listOf(
            R.drawable.rayna_story_photo_1,
            R.drawable.rayna_story_photo_2,
            R.drawable.rayna_story_photo_3,
        ),
    )

    fun forLabel(label: String): InboxStoryPreviewContent? = when (label) {
        Lindsey.label -> Lindsey
        Maren.label -> Maren
        Alena.label -> Alena
        Rayna.label -> Rayna
        else -> null
    }
}

@Composable
fun InboxStoryPreviewSheet(
    visible: Boolean,
    story: InboxStoryPreviewContent = InboxStoryPreviews.Lindsey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStoryIndex by remember { mutableIntStateOf(0) }
    val progressAnim = remember { Animatable(0f) }
    val activeProgress = progressAnim.value
    val scope = rememberCoroutineScope()
    val storyCount = story.storyPhotos.size
    val linearEasing = TuxTheme.animation.linear

    fun jumpToIndex(index: Int) {
        if (storyCount <= 0) return
        val targetIndex = ((index % storyCount) + storyCount) % storyCount
        if (targetIndex == currentStoryIndex) return
        scope.launch {
            // Reset progress before index so the new segment never inherits the old fill.
            progressAnim.snapTo(0f)
            currentStoryIndex = targetIndex
        }
    }

    // Reset to first frame when opening (or when switching story identity).
    LaunchedEffect(visible, story.label) {
        if (!visible) return@LaunchedEffect
        progressAnim.snapTo(0f)
        currentStoryIndex = 0
    }

    LaunchedEffect(visible, story.label, currentStoryIndex) {
        if (!visible) {
            // Do not reset during fade-out, otherwise exit animation can flicker.
            return@LaunchedEffect
        }
        if (storyCount <= 0) return@LaunchedEffect
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LindseyStoryDimens.StoryDurationMs,
                easing = linearEasing,
            ),
        )
        progressAnim.snapTo(0f)
        currentStoryIndex = (currentStoryIndex + 1) % storyCount
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = InboxStoryPreviewMotion.EnterMs,
                easing = TuxTheme.animation.easeOutStandard,
            ),
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = InboxStoryPreviewMotion.ExitMs,
                easing = TuxTheme.animation.easeOutStandard,
            ),
        ),
    ) {
        TuxTheme(colors = darkColors()) {
            StoryPreviewSystemBars(darkBackground = true)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(TuxTheme.colors.UIPageFlat1),
            ) {
                StoryScreen(
                    story = story,
                    onDismiss = onDismiss,
                    currentStoryIndex = currentStoryIndex,
                    activeProgress = activeProgress,
                    onNext = { jumpToIndex(currentStoryIndex + 1) },
                    onPrev = { jumpToIndex(currentStoryIndex - 1) },
                )
                StoryBottomInteraction()
            }
        }
    }
}

@Composable
private fun StoryPreviewSystemBars(darkBackground: Boolean) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevLightStatus = controller.isAppearanceLightStatusBars
        val prevLightNav = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = prevLightStatus
            controller.isAppearanceLightNavigationBars = prevLightNav
        }
    }
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        // true => dark icons for light background; false => light icons for dark background
        val lightSystemIcons = !darkBackground
        controller.isAppearanceLightStatusBars = lightSystemIcons
        controller.isAppearanceLightNavigationBars = lightSystemIcons
    }
}

@Composable
private fun StoryScreen(
    story: InboxStoryPreviewContent,
    onDismiss: () -> Unit,
    currentStoryIndex: Int,
    activeProgress: Float,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LindseyStoryDimens.SwipeThreshold.toPx()
    }
    var dragAccum by remember { mutableStateOf(0f) }
    val tapInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LindseyStoryDimens.StoryScreenTop)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(LindseyStoryDimens.ScreenCorner)),
    ) {
        Crossfade(
            targetState = currentStoryIndex,
            animationSpec = tween(
                durationMillis = InboxStoryPreviewMotion.PhotoCrossfadeMs,
                easing = TuxTheme.animation.linear,
            ),
            label = "storyPhotoCrossfade",
        ) { page ->
            Image(
                painter = painterResource(story.storyPhotos[page]),
                contentDescription = "${story.label} Story ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentStoryIndex) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccum += dragAmount
                        },
                        onDragEnd = {
                            when {
                                dragAccum <= -swipeThresholdPx -> onNext()
                                dragAccum >= swipeThresholdPx -> onPrev()
                            }
                            dragAccum = 0f
                        },
                        onDragCancel = { dragAccum = 0f },
                    )
                },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = tapInteractionSource,
                            indication = null,
                            onClick = onPrev,
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = tapInteractionSource,
                            indication = null,
                            onClick = onNext,
                        ),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )
        StoryTopUi(
            story = story,
            onDismiss = onDismiss,
            currentStoryIndex = currentStoryIndex,
            activeProgress = activeProgress,
        )
    }
}

@Composable
private fun StoryTopUi(
    story: InboxStoryPreviewContent,
    onDismiss: () -> Unit,
    currentStoryIndex: Int,
    activeProgress: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DynamicStoryProgress(
            story = story,
            currentStoryIndex = currentStoryIndex,
            activeProgress = activeProgress,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LindseyStoryDimens.TopBarH)
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.HeaderGap),
            ) {
                Image(
                    painter = painterResource(story.avatarRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(LindseyStoryDimens.HeaderAvatar)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    modifier = Modifier.height(LindseyStoryDimens.HeaderTextBlockH),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.HeaderNameGap),
                ) {
                    TuxText(
                        text = story.label,
                        style = TuxTheme.typography.h3_regular.copy(fontWeight = FontWeight.Medium),
                        color = TuxTheme.colors.UIImageOverlayWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TuxText(
                        text = "· ${story.timeText}",
                        style = TuxTheme.typography.h4_semibold,
                        color = TuxTheme.colors.UIImageOverlayWhite.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.TopActionGap),
                modifier = Modifier.padding(
                    start = LindseyStoryDimens.TopActionPadStart,
                    end = LindseyStoryDimens.TopActionPadEnd,
                    top = LindseyStoryDimens.TopActionPadV,
                    bottom = LindseyStoryDimens.TopActionPadV,
                ),
            ) {
                TuxIcon(
                    icon = VectorResource(TuxR.raw.icon_story_camera_rounded),
                    tint = TuxTheme.colors.UIImageOverlayWhite,
                    modifier = Modifier.size(24.dp),
                    contentDescription = null,
                )
                TuxIcon(
                    icon = VectorResource(TuxR.raw.icon_x_mark),
                    tint = TuxTheme.colors.UIImageOverlayWhite,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                    contentDescription = "Close story",
                )
            }
        }
    }
}

@Composable
private fun DynamicStoryProgress(
    story: InboxStoryPreviewContent,
    currentStoryIndex: Int,
    activeProgress: Float,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LindseyStoryDimens.TopUiPadH),
    ) {
        val totalWidth = maxWidth
        val segmentCount = story.storyPhotos.size
        val totalGap = LindseyStoryDimens.ProgressGap * (segmentCount - 1)
        val segmentWidth = (totalWidth - totalGap) / segmentCount
        Row(horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.ProgressGap)) {
            repeat(segmentCount) { index ->
                val fill = when {
                    index < currentStoryIndex -> 1f
                    index == currentStoryIndex -> activeProgress.coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(LindseyStoryDimens.ProgressH)
                        .clip(RoundedCornerShape(999.dp))
                        .background(TuxTheme.colors.UIImageOverlayWhite.copy(alpha = 0.34f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .height(LindseyStoryDimens.ProgressH)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TuxTheme.colors.UIShapeNeutral),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.StoryBottomInteraction() {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
                start = LindseyStoryDimens.BottomInteractionPadH,
                end = LindseyStoryDimens.BottomInteractionPadH,
                bottom = LindseyStoryDimens.BottomInteractionBottom,
            )
            .padding(end = LindseyStoryDimens.MessageContainerPadEnd)
            .height(LindseyStoryDimens.MessageRowH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.MessageActionGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(LindseyStoryDimens.MessageBubbleH)
                .clip(RoundedCornerShape(LindseyStoryDimens.MessageBubbleRadius))
                .background(TuxTheme.colors.UIShapeNeutral3)
                .padding(start = LindseyStoryDimens.MessagePadStart, end = LindseyStoryDimens.MessagePadEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TuxText(
                text = "Message....",
                style = TuxTheme.typography.h4_regular,
                color = TuxTheme.colors.UIText3,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LindseyStoryDimens.MessageEmojiGap)) {
                TuxText(text = "😍", style = TuxTheme.typography.h4_regular, color = TuxTheme.colors.UIImageOverlayWhite)
                TuxText(text = "😂", style = TuxTheme.typography.h4_regular, color = TuxTheme.colors.UIImageOverlayWhite)
                TuxText(text = "😳", style = TuxTheme.typography.h4_regular, color = TuxTheme.colors.UIImageOverlayWhite)
            }
        }
        TuxIcon(
            icon = VectorResource(TuxR.raw.icon_color_like_shadow_alt_3),
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp),
            contentDescription = "Like",
        )
        TuxIcon(
            icon = VectorResource(TuxR.raw.icon_color_share_shadow_alt_3),
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp),
            contentDescription = "Share",
        )
    }
}

