package com.blacksoft.miuixpackageupdate

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal object MiuixUpdateStyle {
    private const val TEXT_OFFSET_DP = -24f
    private const val SPINNER_SIZE_DP = 40
    @JvmStatic
    fun applyBackground(activity: Activity, content: ViewGroup) {
        val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val background = if (nightMode == Configuration.UI_MODE_NIGHT_YES) 0xFF242424.toInt() else Color.WHITE
        activity.window.setBackgroundDrawable(ColorDrawable(background))
        content.setBackgroundColor(background)
        val labels = mutableListOf<TextView>()
        fun collect(view: View) {
            if (view is TextView && view.text.isNotBlank()) labels.add(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(content)
        if (labels.size == 1) {
            val label = labels.single()
            val text = label.text
            val end = text.trimEnd().length
            val ellipsisLength = when {
                text.subSequence(0, end).endsWith("...") -> 3
                end > 0 && text[end - 1] == '\u2026' -> 1
                else -> 0
            }
            if (ellipsisLength > 0) {
                label.text = text.subSequence(0, end - ellipsisLength).trimEnd()
            }
            label.translationY = TEXT_OFFSET_DP * content.resources.displayMetrics.density
        } else {
            Log.w("MiuixPackageUpdate", "Expected one update label, found " + labels.size)
        }
    }

    @JvmStatic
    fun createSpinner(original: ProgressBar): View {
        val diameterPx = (SPINNER_SIZE_DP * original.resources.displayMetrics.density).roundToInt()
        original.layoutParams.width = diameterPx
        original.layoutParams.height = diameterPx
        return UpdateSpinner(original.context)
    }
}

private class UpdateSpinner(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f * resources.displayMetrics.density
    }
    private val arcBounds = RectF()
    private var rotation = 0f
    private var sweep = 30f

    // Both the 1000 ms rotation and the 1600 ms sweep repeat after 8000 ms.
    private val animator = ValueAnimator.ofFloat(0f, 8000f).apply {
        duration = 8000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val timeMs = it.animatedValue as Float
            rotation = timeMs % 1000f * 360f / 1000f
            val sweepPhase = timeMs % 1600f / 800f
            sweep = 30f + 90f * (1f - abs(1f - sweepPhase))
            invalidate()
        }
    }

    init {
        translationY = 32f
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val radius = (min(w, h) - paint.strokeWidth) / 2f
        val centerX = w / 2f
        val centerY = h / 2f
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = 0x26888888
        canvas.drawOval(arcBounds, paint)
        paint.color = 0xFF3482FF.toInt()
        canvas.drawArc(arcBounds, rotation, sweep, false, paint)
    }

    override fun getAccessibilityClassName(): CharSequence = ProgressBar::class.java.name
}
