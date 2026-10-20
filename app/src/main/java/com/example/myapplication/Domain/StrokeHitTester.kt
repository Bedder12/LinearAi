package com.example.myapplication.Domain

import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects

class StrokeHitTester {

    fun findHits(
        strokes: List<TrackedStroke>,
        x: Float,
        y: Float,
        radius: Float
    ): Set<TrackedStroke>{

        val center = ImmutableVec(x,y)

        val eraserRegion = ImmutableBox.fromCenterAndDimensions(
            center = center,
            width = radius * 2,
            height = radius * 2
        )

        val identityTransform = AffineTransform.IDENTITY

        return strokes
            .filter { trackedStroke ->
                trackedStroke.stroke.shape.intersects(
                    box = eraserRegion,
                    meshToBox = identityTransform
                )
            }
            .toSet()
    }
}