package com.example.myapplication.ink

import android.graphics.Bitmap

class InkBitmapRenderer {


    fun render(
        document: InkDocument,
        width: Int,
        height: Int
    ): Bitmap {

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }
}