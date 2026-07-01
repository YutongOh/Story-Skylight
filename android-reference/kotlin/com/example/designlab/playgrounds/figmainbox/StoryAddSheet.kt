package com.example.designlab.playgrounds.figmainbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.TuxNavBar
import com.bytedance.tux.compose.customFontFamily
import com.bytedance.tux.compose.darkColors
import com.example.designlab.playgrounds.R
import kotlinx.coroutines.launch

private object StoryAddDimens {
    val NavTap = 44.dp

    // Nav camera icon switches when the camera entry (top pad + card) is fully obscured.
    // Threshold should be 12dp + 90dp = 104dp, excluding bottom 12dp spacing.
    val CameraNavSwitchH = 104.dp
    val CameraSectionPad = 12.dp
    val CameraCardH = 90.dp
    val CameraCardRadius = 8.dp
    val CameraIcon = 28.dp
    val CameraLabelGap = 6.dp

    val SectionPadH = 16.dp
    val SectionPadV = 8.dp
    val RecentsGap = 2.dp
    val RecentsChevron = 16.dp

    val TabsH = 40.dp
    val TabIndicatorH = 2.dp
    val TabSeparatorH = 0.5.dp
    val GridGap = 1.5.dp
    val TileAspectRatio = 119f / 229f
    val SelectBarH = 60.dp
    val SelectGap = 8.dp
    val CheckboxOuter = 24.dp
    val CheckboxInner = 22.dp
    val CheckboxBorder = 1.5.dp

    const val EnterMs = 320
    const val ExitMs = 260
    const val NavCameraHideMs = 200
    const val NavCameraShowMs = 250
    val NavCameraSlideDistance = 44.dp
}

private data class StoryGridTile(val imageRes: Int)

private val storyTabs = listOf("All", "Photos", "Videos")

private val storyGridTiles = listOf(
    StoryGridTile(R.drawable.story_add_tile_01),
    StoryGridTile(R.drawable.story_add_tile_02),
    StoryGridTile(R.drawable.story_add_tile_03),
    StoryGridTile(R.drawable.story_add_tile_04),
    StoryGridTile(R.drawable.story_add_tile_05),
    StoryGridTile(R.drawable.story_add_tile_06),
    StoryGridTile(R.drawable.story_add_tile_07),
    StoryGridTile(R.drawable.story_add_tile_08),
    StoryGridTile(R.drawable.story_add_tile_09),
    StoryGridTile(R.drawable.story_add_tile_10),
    StoryGridTile(R.drawable.story_add_tile_11),
    StoryGridTile(R.drawable.story_add_tile_12),
)

@Composable
fun StoryAddBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = StoryAddDimens.EnterMs,
                easing = TuxTheme.animation.easeOutStandard,
            ),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = StoryAddDimens.ExitMs,
                easing = TuxTheme.animation.easeOutStandard,
            ),
        ),
    ) {
        TuxTheme(colors = darkColors()) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val cameraSwitchPx = with(density) { StoryAddDimens.CameraNavSwitchH.roundToPx() }
            val cameraObscured by remember(listState, cameraSwitchPx) {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 ||
                        listState.firstVisibleItemScrollOffset >= cameraSwitchPx
                }
            }
            val navCameraProgress by animateFloatAsState(
                targetValue = if (cameraObscured) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (cameraObscured) {
                        StoryAddDimens.NavCameraShowMs
                    } else {
                        StoryAddDimens.NavCameraHideMs
                    },
                    easing = TuxTheme.animation.easeInOut,
                ),
                label = "storyNavCameraProgress",
            )
            var selectedTab by remember { mutableIntStateOf(0) }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(TuxTheme.colors.UIImageOverlayBlack)
                    // Keep nav bar below system status bar area.
                    .statusBarsPadding(),
            ) {
                StoryAddTopBar(
                    onDismiss = onDismiss,
                    navCameraProgress = navCameraProgress,
                    onNavCameraClick = { scope.launch { listState.animateScrollToItem(0) } },
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                ) {
                    item(key = "camera") { StoryCameraCard() }
                    item(key = "recents") { StoryRecentsHeader() }
                    item(key = "tabs") {
                        StoryTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                    }
                    item(key = "grid") { StoryGrid() }
                    item(key = "grid_bottom_gap") {
                        Spacer(modifier = Modifier.height(StoryAddDimens.GridGap))
                    }
                }
                StorySelectBar()
            }
        }
    }
}

@Composable
private fun StoryAddTopBar(
    onDismiss: () -> Unit,
    navCameraProgress: Float,
    onNavCameraClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TuxNavBar(
            backgroundColor = TuxTheme.colors.UIImageOverlayBlack,
            startAction = {
                Box(
                    modifier = Modifier
                        .size(StoryAddDimens.NavTap)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.album_v4_ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            centerAction = {
                TuxText(
                    text = "Add to Story",
                    style = TuxTheme.typography.h3_bold,
                    color = TuxTheme.colors.UIText1,
                )
            },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(StoryAddDimens.NavTap)
                .graphicsLayer {
                    alpha = navCameraProgress
                    val slide = StoryAddDimens.NavCameraSlideDistance.toPx()
                    translationY = (1f - navCameraProgress) * slide
                }
                .clickable(
                    enabled = navCameraProgress > 0.5f,
                    onClick = onNavCameraClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.album_v4_ic_nav_camera),
                contentDescription = "Camera",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun StoryCameraCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(StoryAddDimens.CameraSectionPad),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StoryAddDimens.CameraCardH)
                .clip(RoundedCornerShape(StoryAddDimens.CameraCardRadius))
                .background(TuxTheme.colors.UIImageOverlayDarkGrayA60)
                .border(
                    width = 1.dp,
                    color = TuxTheme.colors.UIImageOverlayDarkGrayA60,
                    shape = RoundedCornerShape(StoryAddDimens.CameraCardRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StoryAddDimens.CameraLabelGap),
            ) {
                Image(
                    painter = painterResource(R.drawable.album_v4_ic_camera),
                    contentDescription = null,
                    modifier = Modifier.size(StoryAddDimens.CameraIcon),
                )
                TuxText(
                    text = "Camera",
                    style = TuxTheme.typography.p3_regular.copy(
                        fontFamily = customFontFamily(
                            opticalSize = 12.sp,
                            weight = FontWeight.Medium,
                        ),
                        fontWeight = FontWeight.Medium,
                    ),
                    color = TuxTheme.colors.UIImageOverlayWhite,
                )
            }
        }
    }
}

@Composable
private fun StoryRecentsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(
                horizontal = StoryAddDimens.SectionPadH,
                vertical = StoryAddDimens.SectionPadV,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StoryAddDimens.RecentsGap),
    ) {
        TuxText(
            text = "Recents",
            style = TuxTheme.typography.h4_bold,
            color = TuxTheme.colors.UIImageOverlayWhite,
        )
        Image(
            painter = painterResource(R.drawable.album_v4_ic_chevron_down),
            contentDescription = "Choose album",
            modifier = Modifier.size(StoryAddDimens.RecentsChevron),
        )
    }
}

@Composable
private fun StoryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StoryAddDimens.TabsH),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = StoryAddDimens.SectionPadH),
        ) {
            storyTabs.forEachIndexed { idx, label ->
                val selected = idx == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    TuxText(
                        text = label,
                        style = TuxTheme.typography.h4_semibold,
                        color = if (selected) {
                            TuxTheme.colors.UIImageOverlayWhite
                        } else {
                            TuxTheme.colors.UIText3
                        },
                        textAlign = TextAlign.Center,
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(StoryAddDimens.TabIndicatorH)
                                .background(TuxTheme.colors.UIImageOverlayWhite),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(StoryAddDimens.TabSeparatorH)
                .background(TuxTheme.colors.UIImageOverlayWhiteA20),
        )
    }
}

@Composable
private fun StoryGrid(modifier: Modifier = Modifier) {
    val rows = remember(storyGridTiles) { storyGridTiles.chunked(3) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StoryAddDimens.GridGap),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StoryAddDimens.GridGap),
            ) {
                row.forEachIndexed { colIndex, tile ->
                    val absoluteIndex = rowIndex * 3 + colIndex
                    StoryGridTileItem(
                        tile = tile,
                        index = absoluteIndex,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StoryGridTileItem(
    tile: StoryGridTile,
    index: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(StoryAddDimens.TileAspectRatio),
    ) {
        Image(
            painter = painterResource(tile.imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (index == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TuxTheme.colors.UIImageOverlayDarkGrayA60)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                TuxText(
                    text = "00:07",
                    style = TuxTheme.typography.p3_regular,
                    color = TuxTheme.colors.UIImageOverlayWhite,
                )
            }
        }
    }
}

@Composable
private fun StorySelectBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Keep select bar clear of system navigation/home indicator area.
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(TuxTheme.colors.UIShapeNeutral3),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StoryAddDimens.SelectBarH)
                .padding(horizontal = StoryAddDimens.SectionPadH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StoryAddDimens.SelectGap),
        ) {
            Box(
                modifier = Modifier
                    .size(StoryAddDimens.CheckboxOuter)
                    .padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(StoryAddDimens.CheckboxInner)
                        .border(
                            StoryAddDimens.CheckboxBorder,
                            TuxTheme.colors.UIImageOverlayWhite,
                            CircleShape,
                        ),
                )
            }
            TuxText(
                text = "Multiple select",
                style = TuxTheme.typography.h4_semibold,
                color = TuxTheme.colors.UIImageOverlayWhite,
            )
        }
    }
}
