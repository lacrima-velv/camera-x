package com.lacrima.camerax.utils

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updatePadding
import timber.log.Timber

object Util {
    inline fun View.afterMeasured(crossinline block: () -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            }
        })
    }

    fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    fun Bitmap.flipHorizontally(isFlippedHorizontally: Boolean): Bitmap {
        return if (isFlippedHorizontally) {
            val matrix = Matrix().apply { postScale(-1f, 1f, width/2f, height/2f) }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        } else {
            this
        }
    }

    // Used for version codes < 0
    fun getBitmapFromDrawingCache(layout: ViewGroup): Bitmap {
        return layout.drawingCache.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Apply window insets to the bottom of the view
     */
    fun setUiWindowInsetsBottom(view: View, bottomMargin: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val params = (v.layoutParams as ViewGroup.MarginLayoutParams)
            params.setMargins(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomMargin)
            insets
        }
    }

    /**
     * Apply window insets to the top of the view
     */
    fun setUiWindowInsetsTop(view: View, topPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            insets
        }
    }

    /**
     * Apply window insets to the view to avoid overlapping with system bars which occurs
     * if the app is displayed edge to edge
     */
    fun setUiWindowInsets(view: View, topPadding: Int = 0, bottomPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            insets
        }
    }

    /**
     * Convert dp to pixels
     */
    val Int.toPixels
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()

    @RequiresApi(Build.VERSION_CODES.O)
    fun getBitmapFromView(view: View, activity: Activity): Bitmap? {
        var bitmapCopy: Bitmap? = null
        activity.window?.let { window ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)
            try {
                PixelCopy.request(window,
                    Rect(
                        locationOfViewInWindow[0],
                        locationOfViewInWindow[1],
                        locationOfViewInWindow[0] + view.width,
                        locationOfViewInWindow[1] + view.height
                    ), bitmap, { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            bitmapCopy = bitmap
                        }
                        // possible to handle other result codes ...
                    },
                    Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                // PixelCopy may throw IllegalArgumentException, make sure to handle it
                e.printStackTrace()
            }
        }
        return bitmapCopy
    }

    /**
     * Hides only status bar
     */
    @Suppress("DEPRECATION")
    fun Activity.removeStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // This work only for android 4.4+
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        }
    }

    /**
     * Returns status bar and nav bar if they were hidden
     */
    @Suppress("DEPRECATION")
    fun Activity.returnStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                //controller.show(WindowInsets.Type.navigationBars())
                controller.show(WindowInsets.Type.statusBars())
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    /**
     * Hides status bar and nav bar
     */
    @Suppress("DEPRECATION")
    fun Activity.setFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars())
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // This work only for android 4.4+
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        }
    }

    /** Milliseconds used for UI animations */
    const val ANIMATION_FAST_MILLIS = 50L
    const val ANIMATION_SLOW_MILLIS = 100L

    /**
     * Simulate a button click, including a small delay while it is being pressed to trigger the
     * appropriate animations.
     */
    fun ImageButton.simulateClick(delay: Long = ANIMATION_FAST_MILLIS) {
        performClick()
        isPressed = true
        invalidate()
        postDelayed({
            invalidate()
            isPressed = false
        }, delay)
    }
}