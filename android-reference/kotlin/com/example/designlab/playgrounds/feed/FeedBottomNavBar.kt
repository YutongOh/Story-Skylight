package com.example.designlab.playgrounds.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.bytedance.tux.compose.TuxIcon
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.res.VectorResource
import com.bytedance.tux.kmp.icon.IconExpExp01IconExplore
import com.bytedance.tux.kmp.icon.IconExpExp01IconExploreFill
import com.bytedance.tux.kmp.icon.IconExpExp01IconHome
import com.bytedance.tux.kmp.icon.IconExpExp01IconHomeFill
import com.bytedance.tux.kmp.icon.IconExpExp01IconInbox
import com.bytedance.tux.kmp.icon.IconExpExp01IconInboxFill
import com.bytedance.tux.kmp.icon.IconExpExp01IconProfile
import com.bytedance.tux.kmp.icon.IconExpExp01IconProfileFill
import com.example.designlab.playgrounds.R

enum class FeedBottomNavTab {
    Home,
    Explore,
    Create,
    Inbox,
    Me,
}

private data class FeedBottomNavItem(
    val tab: FeedBottomNavTab,
    val labelRes: Int,
    // Icon_Exp/Exp_01/Icon/*_Fill when selected
    val selectedIcon: VectorResource,
    // Icon_Exp/Exp_01/Icon/* outline when unselected
    val unselectedIcon: VectorResource,
)

private val FeedBottomNavIconFrame = FeedDimens.BottomNavIconFrame
private val FeedBottomNavIconSize = FeedDimens.BottomNavIconSize

private val FeedBottomNavItems = listOf(
    FeedBottomNavItem(
        tab = FeedBottomNavTab.Home,
        labelRes = R.string.feed_nav_home,
        selectedIcon = IconExpExp01IconHomeFill,
        unselectedIcon = IconExpExp01IconHome,
    ),
    FeedBottomNavItem(
        tab = FeedBottomNavTab.Explore,
        labelRes = R.string.feed_nav_explore,
        selectedIcon = IconExpExp01IconExploreFill,
        unselectedIcon = IconExpExp01IconExplore,
    ),
    FeedBottomNavItem(
        tab = FeedBottomNavTab.Inbox,
        labelRes = R.string.feed_nav_inbox,
        selectedIcon = IconExpExp01IconInboxFill,
        unselectedIcon = IconExpExp01IconInbox,
    ),
    FeedBottomNavItem(
        tab = FeedBottomNavTab.Me,
        labelRes = R.string.feed_nav_me,
        selectedIcon = IconExpExp01IconProfileFill,
        unselectedIcon = IconExpExp01IconProfile,
    ),
)

/** Figma 7484:5024 — Components v2 Bottom Nav Bar (Bar Cell × 4 + Create). */
@Composable
fun FeedBottomNavBar(
    selectedTab: FeedBottomNavTab = FeedBottomNavTab.Home,
    onTabSelected: (FeedBottomNavTab) -> Unit = {},
    onCreateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val separatorPx = with(density) { FeedDimens.BottomNavSeparator.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FeedDimens.BottomNavH)
            .background(FeedColors.PageFlat1)
            .drawBehind {
                drawLine(
                    color = FeedColors.NavSeparator,
                    start = Offset(0f, separatorPx / 2f),
                    end = Offset(size.width, separatorPx / 2f),
                    strokeWidth = separatorPx,
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(FeedDimens.BottomNavGap),
            verticalAlignment = Alignment.Top,
        ) {
            FeedBottomNavTab.entries.forEach { tab ->
                if (tab == FeedBottomNavTab.Create) {
                    FeedBottomNavBarCreateCell(
                        onClick = onCreateClick,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val item = FeedBottomNavItems.first { it.tab == tab }
                    FeedBottomNavBarCell(
                        item = item,
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedBottomNavBarCell(
    item: FeedBottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) FeedColors.Text1 else FeedColors.Text3
    val label = stringResource(item.labelRes)
    val interactionSource = remember { MutableInteractionSource() }
    val labelStyle = TuxTheme.typography.small_text_2_regular.copy(
        letterSpacing = FeedDimens.BottomNavLabelTracking,
        lineHeight = FeedDimens.BottomNavLabelLineHeight,
    )

    // Figma 7484:5025 Bar Cell — icon frame y=2, label y=33 (absolute within 49dp cell)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = FeedDimens.BottomNavCellPadTop)
                .size(FeedBottomNavIconFrame),
            contentAlignment = Alignment.Center,
        ) {
            TuxIcon(
                icon = if (selected) item.selectedIcon else item.unselectedIcon,
                tint = contentColor,
                width = FeedBottomNavIconSize,
                height = FeedBottomNavIconSize,
                contentDescription = label,
            )
        }
        TuxText(
            text = label,
            style = labelStyle,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = FeedDimens.BottomNavLabelTop)
                .fillMaxWidth()
                .padding(horizontal = FeedDimens.BottomNavLabelPadH),
        )
    }
}

@Composable
private fun FeedBottomNavBarCreateCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.size(FeedDimens.BottomNavCreateFrame)) {
            Image(
                painter = painterResource(R.drawable.feed_bottom_nav_create),
                contentDescription = stringResource(R.string.feed_nav_create),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = FeedDimens.BottomNavCreateIconStart,
                        y = FeedDimens.BottomNavCreateIconTop,
                    )
                    .width(FeedDimens.BottomNavCreateIconW)
                    .height(FeedDimens.BottomNavCreateIconH),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}
