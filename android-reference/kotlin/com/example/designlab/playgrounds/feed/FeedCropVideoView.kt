package com.example.designlab.playgrounds.feed

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/** VideoView that center-crops to fill its parent (TikTok-style full bleed). */
internal class FeedCropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : VideoView(context, attrs) {

    private var cropVideoWidth = 0
    private var cropVideoHeight = 0

    fun setCropVideoSize(width: Int, height: Int) {
        cropVideoWidth = width
        cropVideoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = getDefaultSize(0, widthMeasureSpec)
        var height = getDefaultSize(0, heightMeasureSpec)
        if (width > 0 && height > 0 && cropVideoWidth > 0 && cropVideoHeight > 0) {
            if (cropVideoWidth * height > width * cropVideoHeight) {
                width = height * cropVideoWidth / cropVideoHeight
            } else if (cropVideoWidth * height < width * cropVideoHeight) {
                height = width * cropVideoHeight / cropVideoWidth
            }
        }
        setMeasuredDimension(width, height)
    }
}
