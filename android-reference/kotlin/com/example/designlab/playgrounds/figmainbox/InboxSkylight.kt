package com.example.designlab.playgrounds.figmainbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytedance.tux.compose.TuxColors
import com.bytedance.tux.compose.TuxText
import com.bytedance.tux.compose.TuxTheme
import com.bytedance.tux.compose.TuxTypography
import com.example.designlab.playgrounds.R

/**
 * Figma Skylight (3243:25472).
 *
 * Layout slot (64×72 / 64×64) is separate from content (ring 72, avatar 64) so
 * parent width constraints never squash circular assets.
 */
internal object SkylightDimens {
    val PadH = 16.dp
    val PadV = 12.dp
    val ItemGap = 20.dp
    val ItemW = 64.dp
    val Avatar = 64.dp
    val RingFrame = 72.dp
    val LabelGap = 10.dp
    val CreatePlusBadge = 18.dp
    val CreatePlusIcon = 10.8.dp
}

private object SkylightAssets {
    val CreateAvatar = R.drawable.inbox_story_create
    val StoryRing = R.drawable.inbox_story_ring
    val PlusIcon = R.drawable.inbox_story_plus
    val PlusStroke = R.drawable.inbox_story_plus_stroke
}

private val CreatePlusGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF15C0F9),
        Color(0xFF20D5EC),
        Color(0xFF1AE3C6),
    ),
)

data class InboxSkylightStory(val label: String, val avatarRes: Int)

val DefaultInboxSkylightStories = listOf(
    InboxSkylightStory("Lindsey", R.drawable.inbox_story_lindsey),
    InboxSkylightStory("Maren", R.drawable.inbox_story_maren),
    InboxSkylightStory("Alena", R.drawable.inbox_story_alena),
    InboxSkylightStory("Rayna", R.drawable.inbox_story_rayna),
)

@Composable
fun InboxSkylight(
    modifier: Modifier = Modifier,
    stories: List<InboxSkylightStory> = DefaultInboxSkylightStories,
    readStoryLabels: Set<String> = emptySet(),
    onCreateClick: (() -> Unit)? = null,
    onStoryClick: ((String) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = SkylightDimens.PadH,
                vertical = SkylightDimens.PadV,
            ),
        horizontalArrangement = Arrangement.spacedBy(SkylightDimens.ItemGap),
        verticalAlignment = Alignment.Top,
    ) {
        SkylightCreateItem(onClick = onCreateClick)
        stories.forEach { story ->
            SkylightStoryItem(
                label = story.label,
                avatarRes = story.avatarRes,
                isRead = story.label in readStoryLabels,
                onClick = if (onStoryClick != null) {
                    { onStoryClick(story.label) }
                } else {
                    null
                },
            )
        }
    }
}

/** Figma layout slot: column w 64, avatar area h 64. */
@Composable
private fun SkylightCreateItem(onClick: (() -> Unit)?) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(SkylightDimens.ItemW)
            .clickable(
                enabled = onClick != null,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkylightDimens.LabelGap),
    ) {
        Box(
            modifier = Modifier
                .width(SkylightDimens.ItemW)
                .height(SkylightDimens.Avatar),
            contentAlignment = Alignment.Center,
        ) {
            SkylightCreateAvatarContent(colors = colors)
        }
        SkylightItemLabel(text = "Create", typography = typography, colors = colors)
    }
}

@Composable
private fun SkylightCreateAvatarContent(colors: TuxColors) {
    Box(
        modifier = Modifier.requiredSize(SkylightDimens.Avatar),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(SkylightAssets.CreateAvatar),
            contentDescription = "Create",
            modifier = Modifier
                .requiredSize(SkylightDimens.Avatar)
                .clip(CircleShape)
                .border(1.dp, colors.UIShapeNeutral3, CircleShape),
            contentScale = ContentScale.Crop,
        )
        SkylightCreatePlusBadge(
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun SkylightCreatePlusBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.requiredSize(SkylightDimens.CreatePlusBadge),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(SkylightDimens.CreatePlusBadge)
                .clip(CircleShape)
                .background(CreatePlusGradient),
        )
        Image(
            painter = painterResource(SkylightAssets.PlusIcon),
            contentDescription = null,
            modifier = Modifier.requiredSize(SkylightDimens.CreatePlusIcon),
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(SkylightAssets.PlusStroke),
            contentDescription = null,
            modifier = Modifier.requiredSize(SkylightDimens.CreatePlusBadge * 1.1667f),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Figma layout slot: column w 64, avatar slot h 64; 72dp ring overflows via inset -6.25%. */
@Composable
private fun SkylightStoryItem(
    label: String,
    avatarRes: Int,
    isRead: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = TuxTheme.colors
    val typography = TuxTheme.typography
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(SkylightDimens.ItemW)
            .clickable(
                enabled = onClick != null,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkylightDimens.LabelGap),
    ) {
        Box(
            modifier = Modifier
                .width(SkylightDimens.ItemW)
                .height(SkylightDimens.Avatar),
            contentAlignment = Alignment.Center,
        ) {
            SkylightStoryAvatarContent(
                avatarRes = avatarRes,
                contentDescription = label,
                isRead = isRead,
                colors = colors,
            )
        }
        SkylightItemLabel(text = label, typography = typography, colors = colors)
    }
}

/** Content layer: fixed 72 ring + 64 avatar, never inherits slot width squeeze. */
@Composable
private fun SkylightStoryAvatarContent(
    avatarRes: Int,
    contentDescription: String,
    isRead: Boolean,
    colors: TuxColors,
) {
    Box(
        modifier = Modifier.requiredSize(SkylightDimens.RingFrame),
        contentAlignment = Alignment.Center,
    ) {
        if (isRead) {
            Box(
                modifier = Modifier
                    .requiredSize(SkylightDimens.RingFrame)
                    .clip(CircleShape)
                    .border(2.dp, colors.UIShapeNeutral2, CircleShape),
            )
        } else {
            Image(
                painter = painterResource(SkylightAssets.StoryRing),
                contentDescription = null,
                modifier = Modifier.requiredSize(SkylightDimens.RingFrame),
                contentScale = ContentScale.Fit,
            )
        }
        Image(
            painter = painterResource(avatarRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .requiredSize(SkylightDimens.Avatar)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun SkylightItemLabel(
    text: String,
    typography: TuxTypography,
    colors: TuxColors,
) {
    TuxText(
        text = text,
        style = typography.p3_regular,
        color = colors.UIText1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
