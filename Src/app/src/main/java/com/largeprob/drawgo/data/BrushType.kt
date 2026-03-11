package com.largeprob.drawgo.data

import com.largeprob.drawgo.BrushTypeProto

//笔刷类型
enum class BrushType {
//    FELT_TIP_PEN,       // 实线
//    DASHED_LINE,        // 虚线
//    CALLIGRAPHY_BRUSH, // 毛笔/笔锋
//    STROKE_ERASER    // 橡皮擦

    // 实线/均匀粗细
    FeltTipPen,
    // 虚线
    DashedLine,
    // 毛笔/笔锋
    CalligraphyBrush,
    //橡皮擦
    StrokeEraser
}

//领域模型 BrushType to BrushTypeProto
fun BrushType.toProto(): BrushTypeProto {
    return when (this) {
        BrushType.FeltTipPen -> BrushTypeProto.FeltTipPen
        BrushType.DashedLine -> BrushTypeProto.DashedLine
        BrushType.CalligraphyBrush -> BrushTypeProto.CalligraphyBrush
        BrushType.StrokeEraser -> BrushTypeProto.StrokeEraser
    }
}

fun BrushTypeProto.toDomain(): BrushType {
    return when (this) {
        BrushTypeProto.FeltTipPen -> BrushType.FeltTipPen
        BrushTypeProto.DashedLine -> BrushType.DashedLine
        BrushTypeProto.CalligraphyBrush -> BrushType.CalligraphyBrush
        BrushTypeProto.StrokeEraser -> BrushType.StrokeEraser
        else -> BrushType.FeltTipPen
    }
}


