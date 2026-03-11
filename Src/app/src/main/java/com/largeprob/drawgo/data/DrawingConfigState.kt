package com.largeprob.drawgo.data

import androidx.compose.ui.geometry.Offset
import com.largeprob.drawgo.BrushTypeProto
import com.largeprob.drawgo.DrawingDataProto
import kotlin.collections.component1
import kotlin.collections.component2


// 绘图配置状态
data class DrawingConfigState(
    val canvasVisible: Boolean = true,
    val canvasPassthrough: Boolean = false,
    val autoClearCanvas: Boolean = false,
    val visibleOnStart: Boolean = true,

    // 笔刷设置
    val currentBrushType: BrushType = BrushType.FeltTipPen,
    val brushConfigs: Map<BrushType, BrushConfig> = emptyMap(),

    // 工具栏设置
    val toolbarOrientation: ToolbarOrientation = ToolbarOrientation.HORIZONTAL,
    val toolbarOffsetX: Float = 32f,
    val toolbarOffsetY: Float = 64f,

    // 抽屉设置
    val firstDrawerOpen: Boolean = false,
    val secondDrawerOpen: Boolean = false,
    val firstDrawerButtons: Set<String> = emptySet(),
    val secondDrawerButtons: Set<String> = emptySet(),
    val secondDrawerPinnedButtons: Set<String> = emptySet(),

    // 服务状态
    val serviceRunning: Boolean = false,
){
    val toolbarPosition: Offset
        get() = Offset(toolbarOffsetX, toolbarOffsetY)

    val currentBrushConfig: BrushConfig?
        get() = brushConfigs.get(currentBrushType)
}


//领域模型 DrawingConfigState to DrawingDataProto
fun DrawingConfigState.toProto(): DrawingDataProto{
    // 转换笔刷配置Map为Proto格式
    val brushConfigsMap = this.brushConfigs.map { (key, value) ->
        key.toProto().number to value.toProto()
    }.toMap()

    return DrawingDataProto.newBuilder()
        .setCanvasVisible(this.canvasVisible)
        .setCanvasPassthrough(this.canvasPassthrough)
        .setAutoClearCanvas(this.autoClearCanvas)
        .setVisibleOnStart(this.visibleOnStart)
        .setCurrentBrushType(this.currentBrushType.toProto())
        .putAllBrushConfigs(brushConfigsMap)
        .setToolbarOrientation(this.toolbarOrientation.toProto())
        .setToolbarOffsetX(this.toolbarOffsetX)
        .setToolbarOffsetY(this.toolbarOffsetY)
        .setFirstDrawerOpen(this.firstDrawerOpen)
        .setSecondDrawerOpen(this.secondDrawerOpen)
        .addAllFirstDrawerButtons(this.firstDrawerButtons)
        .addAllSecondDrawerButtons(this.secondDrawerButtons)
        .addAllSecondDrawerPinnedButtons(this.secondDrawerPinnedButtons)
        .setServiceRunning(this.serviceRunning)
        .build()
}

fun DrawingDataProto.toDomain(): DrawingConfigState {
     // 转换笔刷配置Map
     val brushConfigsMap = this.brushConfigsMap.map { (key, value) ->
         BrushTypeProto.forNumber(key)?.toDomain() to value.toDomain()
     }.filter { it.first != null }.associate { it.first!! to it.second }

    return DrawingConfigState(
        canvasVisible = this.canvasVisible,
        canvasPassthrough = this.canvasPassthrough,
        autoClearCanvas = this.autoClearCanvas,
        visibleOnStart = this.visibleOnStart,
        currentBrushType = this.currentBrushType.toDomain(),
        brushConfigs = brushConfigsMap,
        toolbarOrientation = this.toolbarOrientation.toDomain(),
        toolbarOffsetX = this.toolbarOffsetX,
        toolbarOffsetY = this.toolbarOffsetY,
        firstDrawerOpen = this.firstDrawerOpen,
        secondDrawerOpen = this.secondDrawerOpen,
        firstDrawerButtons = this.firstDrawerButtonsList.toSet(),
        secondDrawerButtons = this.secondDrawerButtonsList.toSet(),
        secondDrawerPinnedButtons = this.secondDrawerPinnedButtonsList.toSet(),
        serviceRunning = this.serviceRunning
    )
}




//// 绘图路径数据
//data class DrawPath(
//    val points: List<Offset> = emptyList(),
//    val brushConfig: BrushConfig = BrushConfig(),
//    val timestamp: Long = System.currentTimeMillis()
//)
//
//// 完整的绘图状态（配置 + 内容）
//data class DrawingState(
//    val config: DrawingConfigState = DrawingConfigState(),
//    val paths: List<DrawPath> = emptyList()
//)