package com.metroshare.droidarcade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets

/**
 * Hardware-accelerated view that drives the game loop from onDraw, one frame per vsync.
 * Touch events arrive on the same thread, so game state needs no locking.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, val app: ArcadeApp) : View(context) {
    private var lastFrame = 0L
    private var running = true

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        app.resize(w, h)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var top = 0
        var bottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cut = insets.getInsets(WindowInsets.Type.displayCutout())
            val gestures = insets.getInsets(WindowInsets.Type.mandatorySystemGestures())
            top = cut.top
            bottom = maxOf(cut.bottom, gestures.bottom / 2)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets.displayCutout?.let {
                top = it.safeInsetTop
                bottom = it.safeInsetBottom
            }
        }
        app.setInsets(top, bottom)
        return super.onApplyWindowInsets(insets)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrame = now
        app.update(dt)
        app.render(canvas)
        if (running) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        app.touch(event)
        return true
    }

    fun onResume() {
        running = true
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    fun onPause() {
        running = false
    }
}
