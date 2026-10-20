package com.example.myapplication.domain

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

        return bitmap
    }
}