package com.largeprob.drawgo.data

import com.largeprob.drawgo.ToolbarOrientationProto

// 工具栏排列方式
enum class ToolbarOrientation {
    HORIZONTAL,  // 水平排列
    VERTICAL     // 垂直排列
}

//领域模型 ToolbarOrientation to ToolbarOrientationProto
fun ToolbarOrientation.toProto(): ToolbarOrientationProto {
    return when (this) {
        ToolbarOrientation.HORIZONTAL -> ToolbarOrientationProto.HORIZONTAL
        ToolbarOrientation.VERTICAL -> ToolbarOrientationProto.VERTICAL
    }
}

//to ToolbarOrientation
fun ToolbarOrientationProto.toDomain(): ToolbarOrientation {
    return when (this) {
        ToolbarOrientationProto.HORIZONTAL -> ToolbarOrientation.HORIZONTAL
        ToolbarOrientationProto.VERTICAL -> ToolbarOrientation.VERTICAL
        else -> ToolbarOrientation.HORIZONTAL
    }
}
