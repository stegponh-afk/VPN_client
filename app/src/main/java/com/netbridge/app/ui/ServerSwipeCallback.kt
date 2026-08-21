package com.netbridge.app.ui

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.netbridge.app.R
import com.netbridge.app.model.VlessConfig
import kotlin.math.abs

/**
 * Swipe left on a server card = re-check ping for just that server. Swipe right
 * = connect to it. Neither actually removes the item — [onSwiped] fires the
 * action and immediately asks the adapter to snap the card back into place.
 *
 * While dragging, draws a circle in the gap the card is vacating that fills and
 * turns green (with a checkmark once past the release threshold) as the swipe
 * progresses, so it's visually obvious when releasing will trigger the action
 * versus when it'll just spring back.
 */
class ServerSwipeCallback(
    context: Context,
    private val getServer: (Int) -> VlessConfig?,
    private val onSwipeLeft: (VlessConfig) -> Unit,
    private val onSwipeRight: (VlessConfig) -> Unit,
    private val onSettled: (Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val grayColor = ContextCompat.getColor(context, R.color.text_secondary)
    private val greenColor = ContextCompat.getColor(context, R.color.status_connected)
    private val maxRadiusPx = 22 * context.resources.displayMetrics.density
    private val insetPx = 40 * context.resources.displayMetrics.density
    private val checkIcon = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply {
        setBounds(0, 0, (20 * context.resources.displayMetrics.density).toInt(), (20 * context.resources.displayMetrics.density).toInt())
        DrawableCompat.setTint(this, android.graphics.Color.WHITE)
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argbEvaluator = ArgbEvaluator()

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val server = getServer(position) ?: return
        if (direction == ItemTouchHelper.LEFT) onSwipeLeft(server) else onSwipeRight(server)
        onSettled(position)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val threshold = getSwipeThreshold(viewHolder)
            val progress = (abs(dX) / (itemView.width.toFloat() * threshold)).coerceIn(0f, 1f)

            if (progress > 0f) {
                val cy = itemView.top + itemView.height / 2f
                val cx = if (dX < 0) itemView.right - insetPx else itemView.left + insetPx
                val radius = maxRadiusPx * progress

                circlePaint.color = argbEvaluator.evaluate(progress, grayColor, greenColor) as Int
                c.drawCircle(cx, cy, radius, circlePaint)

                if (progress >= 1f) {
                    checkIcon?.let { icon ->
                        val half = icon.bounds.width() / 2
                        c.save()
                        c.translate(cx - half, cy - half)
                        icon.draw(c)
                        c.restore()
                    }
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
