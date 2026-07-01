package com.example.designlab.playgrounds.feed

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.example.designlab.playgrounds.R

data class FeedVideoItem(
    @RawRes val videoRes: Int,
    @StringRes val usernameRes: Int,
    @StringRes val captionRes: Int,
    @StringRes val soundRes: Int,
    @StringRes val likeCountRes: Int,
    @StringRes val commentCountRes: Int,
    @StringRes val shareCountRes: Int,
)

object FeedVideos {
    val presets = listOf(
        FeedVideoItem(
            videoRes = R.raw.feed_video_01,
            usernameRes = R.string.feed_username,
            captionRes = R.string.feed_caption,
            soundRes = R.string.feed_sound,
            likeCountRes = R.string.feed_like_count,
            commentCountRes = R.string.feed_comment_count,
            shareCountRes = R.string.feed_share_count,
        ),
        FeedVideoItem(
            videoRes = R.raw.feed_video_02,
            usernameRes = R.string.feed_username_2,
            captionRes = R.string.feed_caption_2,
            soundRes = R.string.feed_sound_2,
            likeCountRes = R.string.feed_like_count_2,
            commentCountRes = R.string.feed_comment_count_2,
            shareCountRes = R.string.feed_share_count_2,
        ),
        FeedVideoItem(
            videoRes = R.raw.feed_video_03,
            usernameRes = R.string.feed_username_3,
            captionRes = R.string.feed_caption_3,
            soundRes = R.string.feed_sound_3,
            likeCountRes = R.string.feed_like_count_3,
            commentCountRes = R.string.feed_comment_count_3,
            shareCountRes = R.string.feed_share_count_3,
        ),
        FeedVideoItem(
            videoRes = R.raw.feed_video_04,
            usernameRes = R.string.feed_username_4,
            captionRes = R.string.feed_caption_4,
            soundRes = R.string.feed_sound_4,
            likeCountRes = R.string.feed_like_count_4,
            commentCountRes = R.string.feed_comment_count_4,
            shareCountRes = R.string.feed_share_count_4,
        ),
    )
}
