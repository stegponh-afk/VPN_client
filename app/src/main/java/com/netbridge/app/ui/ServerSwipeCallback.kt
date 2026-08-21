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
 * Swipe left on a server card = re-check ping for just that server (orange).
 * Swipe right = connect to it (green). This is a bounded "swipe action", not a
 * dismiss: the framework's own swipe-threshold/fly-out machinery is disabled
 * (see [getSwipeThreshold]/[getSwipeEscapeVelocity]) and everything is driven
 * manually — [onChildDraw] clamps how far the card can visually travel so it
 * never leaves the list bounds, and [clearView] (fired on release, regardless
 * of distance) decides whether the drag went far enough to fire the action,
 * then animates the card back to rest itself.
 */
class ServerSwipeCallback(
    context: Context,
    private val getServer: (Int) -> VlessConfig?,
    private val onSwipeLeft: (VlessConfig) -> Unit,
    private val onSwipeRight: (VlessConfig) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val density = context.resources.displayMetrics.density
    private val maxSwipePx = 84f * density
    private val triggerPx = 60f * density
    private val maxRadiusPx = 20f * density
    private val insetPx = 38f * density

    private val grayColor = ContextCompat.getColor(context, R.color.text_secondary)
    private val pingColor = ContextCompat.getColor(context, R.color.swipe_ping)
    private val connectColor = ContextCompat.getColor(context, R.color.status_connected)
    private val checkIcon = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply {
        val size = (18 * density).toInt()
        setBounds(0, 0, size, size)
        DrawableCompat.setTint(this, android.graphics.Color.WHITE)
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argbEvaluator = ArgbEvaluator()
    private var trackedDx = 0f

    // Unreachably high on purpose — the framework's own swipe-to-dismiss completion
    // (and its fly-past-the-edge recover animation) is disabled; clearView decides instead.
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 10f
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE
    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    // Since the threshold above is unreachable, every release is a "swipe cancel" as
    // far as the framework is concerned — it was still playing its own recover-to-rest
    // animation on top of the one clearView starts, and the two fighting over the same
    // translationX each frame is what actually caused the visible double-snap. Making
    // the framework's own animation instant (0ms) leaves ours as the only visible one.
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float,
    ): Long = if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
        0L
    } else {
        super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Never reached — see getSwipeThreshold. Action fires from clearView instead.
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
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val clamped = dX.coerceIn(-maxSwipePx, maxSwipePx)
        if (isCurrentlyActive) trackedDx = clamped
        val itemView = viewHolder.itemView
        val progress = (abs(clamped) / triggerPx).coerceIn(0f, 1f)

        if (progress > 0f) {
            val cy = itemView.top + itemView.height / 2f
            val cx = if (clamped < 0) itemView.right - insetPx else itemView.left + insetPx
            val targetColor = if (clamped < 0) pingColor else connectColor

            circlePaint.color = argbEvaluator.evaluate(progress, grayColor, targetColor) as Int
            c.drawCircle(cx, cy, maxRadiusPx * progress, circlePaint)

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

        super.onChildDraw(c, recyclerView, viewHolder, clamped, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val dx = trackedDx
        trackedDx = 0f
        val position = viewHolder.bindingAdapterPosition
        val shouldFire = position != RecyclerView.NO_POSITION && abs(dx) >= triggerPx
        val isLeft = dx < 0

        super.clearView(recyclerView, viewHolder)

        val itemView = viewHolder.itemView
        itemView.translationX = dx
        // Fire the action only once the card has *fully* settled back at rest —
        // triggering it mid-animation raced the adapter's notifyItemChanged (from
        // the ping refresh) against this animation and made the card visibly
        // stutter/snap twice.
        itemView.animate()
            .translationX(0f)
            .setDuration(180)
            .withEndAction {
                if (!shouldFire) return@withEndAction
                val server = getServer(position) ?: return@withEndAction
                if (isLeft) onSwipeLeft(server) else onSwipeRight(server)
            }
            .start()
    }
}
