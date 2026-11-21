package com.example.ui

import android.content.Context
import android.util.DisplayMetrics
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/**
 * Lightweight horizontal LinearLayoutManager tuned for ~150 ms card-to-card translation
 * without heavy scale, shadow, or overshoot effects.
 */
class SmoothTvLayoutManager(context: Context) :
    LinearLayoutManager(context, HORIZONTAL, false) {

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State?,
        position: Int
    ) {
        val scroller = object : LinearSmoothScroller(recyclerView.context) {
            private val interpolator = DecelerateInterpolator(1.4f)

            override fun getHorizontalSnapPreference(): Int = SNAP_TO_START

            override fun calculateTimeForScrolling(dx: Int): Int {
                return 150
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return 150f / displayMetrics.densityDpi
            }

            override fun onTargetFound(
                targetView: android.view.View,
                state: RecyclerView.State,
                action: Action
            ) {
                val dx = calculateDxToMakeVisible(targetView, horizontalSnapPreference)
                if (dx != 0) {
                    action.update(-dx, 0, 150, interpolator)
                }
            }
        }
        scroller.targetPosition = position
        startSmoothScroll(scroller)
    }
}
