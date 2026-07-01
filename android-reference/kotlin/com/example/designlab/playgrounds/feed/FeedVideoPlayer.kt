package com.example.designlab.playgrounds.feed

import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun FeedVideoPlayer(
    @RawRes videoRes: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActiveState by rememberUpdatedState(isActive)
    val videoUri = remember(videoRes) {
        Uri.parse(
            "android.resource://${context.resources.getResourcePackageName(videoRes)}/$videoRes",
        )
    }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                clipChildren = true
                clipToPadding = true
                val view = FeedCropVideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        setCropVideoSize(player.videoWidth, player.videoHeight)
                        if (isActiveState) {
                            start()
                        }
                    }
                    setVideoURI(videoUri)
                }
                addView(view)
                videoView = view
            }
        },
        update = { container ->
            val view = (container as FrameLayout).getChildAt(0) as FeedCropVideoView
            videoView = view
            if (isActiveState) {
                if (!view.isPlaying) view.start()
            } else {
                view.pause()
                view.seekTo(0)
            }
        },
    )

    DisposableEffect(lifecycleOwner, videoView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> videoView?.pause()
                Lifecycle.Event.ON_RESUME -> if (isActiveState) videoView?.start()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            videoView?.stopPlayback()
        }
    }
}
