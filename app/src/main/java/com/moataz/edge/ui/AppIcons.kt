package com.moataz.edge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal enum class EdgeIconType { HOME, FLOW, WORKERS, DIAGNOSTICS, SETTINGS, PLAY, STOP, RESTART }

@Composable
internal fun EdgeIcon(type: EdgeIconType, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialEdgeColor() else tint
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val s = minOf(w, h) / 24f
        val stroke = 2f * s
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, stroke, StrokeCap.Round)
        when (type) {
            EdgeIconType.HOME -> {
                line(Offset(4*s, 11*s), Offset(12*s, 4*s)); line(Offset(12*s, 4*s), Offset(20*s, 11*s))
                line(Offset(6*s, 10*s), Offset(6*s, 20*s)); line(Offset(18*s, 10*s), Offset(18*s, 20*s))
                line(Offset(6*s, 20*s), Offset(18*s, 20*s)); line(Offset(10*s, 20*s), Offset(10*s, 14*s)); line(Offset(10*s,14*s),Offset(14*s,14*s))
            }
            EdgeIconType.FLOW -> {
                line(Offset(7*s,6*s), Offset(17*s,6*s)); line(Offset(17*s,8*s), Offset(17*s,16*s)); line(Offset(15*s,18*s), Offset(7*s,18*s))
                drawCircle(color, 3*s, Offset(5*s,6*s)); drawCircle(color, 3*s, Offset(19*s,6*s)); drawCircle(color, 3*s, Offset(5*s,18*s)); drawCircle(color, 3*s, Offset(17*s,18*s))
            }
            EdgeIconType.WORKERS -> {
                drawRoundRect(color = color, topLeft = Offset(5*s,5*s), size = Size(14*s,14*s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3*s), style = Stroke(stroke))
                for (i in 0..2) { val p=(8+i*4)*s; line(Offset(p,2*s),Offset(p,5*s)); line(Offset(p,19*s),Offset(p,22*s)) }
                line(Offset(9*s,10*s),Offset(12*s,12*s)); line(Offset(12*s,12*s),Offset(9*s,14*s)); line(Offset(14*s,14*s),Offset(16*s,14*s))
            }
            EdgeIconType.DIAGNOSTICS -> {
                line(Offset(3*s,13*s),Offset(7*s,13*s)); line(Offset(7*s,13*s),Offset(9*s,8*s)); line(Offset(9*s,8*s),Offset(12*s,17*s)); line(Offset(12*s,17*s),Offset(15*s,10*s)); line(Offset(15*s,10*s),Offset(17*s,13*s)); line(Offset(17*s,13*s),Offset(21*s,13*s))
            }
            EdgeIconType.SETTINGS -> {
                for (y in listOf(6f,12f,18f)) line(Offset(4*s,y*s),Offset(20*s,y*s))
                drawCircle(color, 2.2f*s, Offset(9*s,6*s)); drawCircle(color,2.2f*s,Offset(15*s,12*s)); drawCircle(color,2.2f*s,Offset(11*s,18*s))
            }
            EdgeIconType.PLAY -> {
                val p=Path().apply { moveTo(8*s,5*s); lineTo(19*s,12*s); lineTo(8*s,19*s); close() }; drawPath(p,color)
            }
            EdgeIconType.STOP -> drawRoundRect(color, Offset(6*s,6*s), Size(12*s,12*s), androidx.compose.ui.geometry.CornerRadius(2*s))
            EdgeIconType.RESTART -> {
                drawArc(color, -55f, 285f, false, topLeft = Offset(4*s,4*s), size=Size(16*s,16*s), style=Stroke(stroke, cap=StrokeCap.Round))
                val p=Path().apply { moveTo(17*s,3*s); lineTo(21*s,5*s); lineTo(18*s,8*s); close() }; drawPath(p,color)
            }
        }
    }
}

@Composable
private fun MaterialEdgeColor(): Color = androidx.compose.material3.LocalContentColor.current

@Composable
internal fun EdgeMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s=minOf(size.width,size.height)/48f
        val hex=Path().apply { moveTo(24*s,2*s); lineTo(42*s,12*s); lineTo(42*s,36*s); lineTo(24*s,46*s); lineTo(6*s,36*s); lineTo(6*s,12*s); close() }
        drawPath(hex, EdgeSurfaceHigh)
        val nodes=listOf(Offset(16*s,17*s),Offset(33*s,14*s),Offset(24*s,27*s),Offset(33*s,33*s))
        drawLine(EdgeGold,nodes[0],nodes[1],2*s,StrokeCap.Round); drawLine(EdgeGold,nodes[0],nodes[2],2*s,StrokeCap.Round); drawLine(EdgeGold,nodes[2],nodes[3],2*s,StrokeCap.Round)
        nodes.forEach { drawCircle(EdgeMint,4*s,it) }
    }
}
