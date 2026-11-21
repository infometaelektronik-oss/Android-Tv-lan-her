package com.example.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.model.YouTubeVideoItem
import com.example.util.TvImageLoader
import kotlinx.coroutines.CoroutineScope

class YouTubeVideoAdapter(
    private val scope: CoroutineScope,
    private val onVideoClick: (YouTubeVideoItem) -> Unit,
    private val onVideoLongClick: () -> Unit
) : RecyclerView.Adapter<YouTubeVideoAdapter.VideoViewHolder>() {

    private val items = mutableListOf<YouTubeVideoItem>()
    var isLargeText: Boolean = false
        private set

    fun submitVideos(videos: List<YouTubeVideoItem>, largeText: Boolean) {
        items.clear()
        items.addAll(videos)
        isLargeText = largeText
        notifyDataSetChanged()
    }

    fun setLargeText(largeText: Boolean) {
        if (isLargeText != largeText) {
            isLargeText = largeText
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_youtube_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.videoCardRoot)
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivVideoThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvVideoTitle)
        private val tvChannel: TextView = itemView.findViewById(R.id.tvVideoChannel)

        fun bind(video: YouTubeVideoItem) {
            val density = itemView.resources.displayMetrics.density
            val cardWidthPx = ((if (isLargeText) 248 else 212) * density).toInt()
            val thumbHeightPx = ((if (isLargeText) 128 else 110) * density).toInt()

            root.layoutParams = (root.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = cardWidthPx
            }
            ivThumb.layoutParams = ivThumb.layoutParams.apply {
                height = thumbHeightPx
            }

            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLargeText) 16f else 13f)
            tvChannel.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLargeText) 13f else 11f)

            tvTitle.text = video.title
            tvChannel.text = video.channelTitle

            TvImageLoader.loadInto(
                imageView = ivThumb,
                url = video.thumbnailUrl,
                targetWidth = 320,
                targetHeight = 180,
                scope = scope
            )

            root.setOnClickListener { onVideoClick(video) }
            root.setOnLongClickListener {
                onVideoLongClick()
                true
            }
        }
    }
}
