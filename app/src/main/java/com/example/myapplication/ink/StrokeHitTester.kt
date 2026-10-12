package com.example.myapplication.ink

import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.strokes.Stroke

class StrokeHitTester {

    fun findHits(
        strokes: List<Stroke>,
        x: Float,
        y: Float,
        radius: Float
    ): Set<Stroke>{

        val center = ImmutableVec(x,y)

        val eraserRegion = ImmutableBox.fromCenterAndDimensions(
            center = center,
            width = radius * 2,
            height = radius * 2
        )

        val identityTransform = AffineTransform.IDENTITY

        return strokes
            .filter { stroke ->
                stroke.shape.intersects(
                    box = eraserRegion,
                    meshToBox = identityTransform
            )
        }.toSet()
    }
}