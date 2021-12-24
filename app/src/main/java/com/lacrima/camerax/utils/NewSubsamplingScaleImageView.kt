package com.lacrima.camerax.utils

import android.content.Context
import android.util.AttributeSet
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class NewSubsamplingScaleImageView : SubsamplingScaleImageView {
    constructor(context: Context?, attr: AttributeSet?) : super(context, attr)
    constructor(context: Context?) : super(context)

    /*
    That will work fine unless the view changes size for other reasons like
    headers and footers appearing/hiding
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (oldh !=0 && oldw != 0) {
            //reset image scale to original on image rotation
            resetScaleAndCenter()
        }
    }
}