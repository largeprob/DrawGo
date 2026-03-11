package com.largeprob.drawgo.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.largeprob.drawgo.BrushConfigProto


// 笔刷配置
data class BrushConfig(
    val type: BrushType = BrushType.FeltTipPen,
    val color: Color = Color.Black,
    val width: Float = 5f,
    val alpha: Float = 1f
)

//领域模型 ToolbarOrientation to ToolbarOrientationProto
fun BrushConfig.toProto(): BrushConfigProto {
    return BrushConfigProto.newBuilder()
        .setType(this.type.toProto())
        .setColor(this.color.toArgb())
        .setWidth(this.width)
        .setAlpha(this.alpha)
        .build()
}

fun BrushConfigProto.toDomain(): BrushConfig {
    return BrushConfig(
        type = this.type.toDomain(),
        color = Color(this.color),
        width = this.width,
        alpha = this.alpha
    )
}
