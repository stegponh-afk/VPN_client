package com.netbridge.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.netbridge.app.R

/** Thin line centered in the gap between consecutive server cards — no line after the last item. */
class ServerDividerDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        strokeWidth = context.resources.displayMetrics.density
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val margin = (child.layoutParams as? RecyclerView.LayoutParams)?.bottomMargin ?: 0
            val y = child.bottom + margin / 2f
            c.drawLine(left, y, right, y, paint)
        }
    }
}
