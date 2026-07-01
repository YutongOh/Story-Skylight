package com.example.designlab.playgrounds.figmainbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Figma 3242:27918 — light-mode bottom nav (49dp bar; system home indicator handled by OS). */
@Composable
fun InboxBottomNavBar(
    onHomeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = TuxTheme.colors
    val density = LocalDensity.current
    val separatorPx = with(density) { InboxDimens.BottomNavSeparator.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.UIPageFlat1)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(InboxDimens.BottomNavBarH)
                .drawBehind {
                    drawLine(
                        color = colors.UIShapeNeutral3,
                        start = Offset(0f, separatorPx / 2f),
                        end = Offset(size.width, separatorPx / 2f),
                        strokeWidth = separatorPx,
                    )
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(InboxDimens.BottomNavGap),
                verticalAlignment = Alignment.Top,
            ) {
                InboxBottomNavCell(
                    label = stringResource(R.string.feed_nav_home),
                    selected = false,
                    selectedIcon = IconExpExp01IconHomeFill,
                    unselectedIcon = IconExpExp01IconHome,
                    onClick = onHomeClick,
                    modifier = Modifier.weight(1f),
                )
                InboxBottomNavCell(
                    label = stringResource(R.string.feed_nav_explore),
                    selected = false,
                    selectedIcon = IconExpExp01IconExploreFill,
                    unselectedIcon = IconExpExp01IconExplore,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                InboxBottomNavCreateCell(modifier = Modifier.weight(1f))
                InboxBottomNavCell(
                    label = stringResource(R.string.feed_nav_inbox),
                    selected = true,
                    selectedIcon = IconExpExp01IconInboxFill,
                    unselectedIcon = IconExpExp01IconInbox,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                InboxBottomNavCell(
                    label = stringResource(R.string.feed_nav_me),
                    selected = false,
                    selectedIcon = IconExpExp01IconProfileFill,
                    unselectedIcon = IconExpExp01IconProfile,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InboxBottomNavCell(
    label: String,
    selected: Boolean,
    selectedIcon: VectorResource,
    unselectedIcon: VectorResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography
    val contentColor = if (selected) colors.UIText1 else colors.UIText3
    val labelStyle = if (selected) {
        typography.small_text_2_semibold
    } else {
        typography.small_text_2_regular
    }.copy(
        letterSpacing = 0.23.sp,
        lineHeight = 13.sp,
    )
    val interactionSource = remember { MutableInteractionSource() }

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
                .offset(y = InboxDimens.BottomNavIconTop)
                .size(InboxDimens.BottomNavIconFrame),
            contentAlignment = Alignment.Center,
        ) {
            TuxIcon(
                icon = if (selected) selectedIcon else unselectedIcon,
                tint = contentColor,
                width = InboxDimens.BottomNavIconSize,
                height = InboxDimens.BottomNavIconSize,
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
                .offset(y = InboxDimens.BottomNavLabelTop)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun InboxBottomNavCreateCell(modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {},
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(R.drawable.inbox_bottom_nav_create),
            contentDescription = stringResource(R.string.feed_nav_create),
            modifier = Modifier
                .size(InboxDimens.BottomNavCreateFrame)
                .offset(y = InboxDimens.BottomNavCreateTop),
            contentScale = ContentScale.Fit,
        )
    }
}
