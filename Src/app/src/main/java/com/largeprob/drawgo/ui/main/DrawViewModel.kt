package com.largeprob.drawgo.ui.main


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.largeprob.drawgo.controllers.DrawController
import com.largeprob.drawgo.controllers.PathWrapper
import com.largeprob.drawgo.data.BrushConfig
import com.largeprob.drawgo.data.BrushType
import com.largeprob.drawgo.data.DrawingConfigState
import com.largeprob.drawgo.data.ToolbarOrientation
import com.largeprob.drawgo.data.toProto
import com.largeprob.drawgo.repository.DrawingRepositoryImpl
import com.largeprob.drawgo.repository.updateStoreBrushConfig
import com.largeprob.drawgo.service.ServiceManager
import com.largeprob.drawgo.ui.StrokeModifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class DrawViewModel(
    private val drawingRepository: DrawingRepositoryImpl,
    private val serviceManager: ServiceManager,
    private val drawController: DrawController,
) : ViewModel() {

    //对UI层访问的数据源
    val drawingState: StateFlow<DrawingConfigState>
        get() = drawingRepository.drawingStore

    val pathList: List<PathWrapper>
        get() = drawController.pathList

    val canUndo: StateFlow<Boolean> = drawController.canUndo
    val canRedo: StateFlow<Boolean> = drawController.canRedo
    val canClearCanvas: StateFlow<Boolean> = drawController.canClearPaths

    init {
        resetToolbarTimer()
    }

    private val brushType:BrushType
        get() = drawingRepository.drawingStore.value.currentBrushType!!

    private val penConfig:BrushConfig
        get() = drawingRepository.drawingStore.value.currentBrushConfig!!

    override fun onCleared() {
        super.onCleared()
        dimmingJob?.cancel()
    }

    //笔刷选择-缓存
    fun switchToPen(type: BrushType) {
        viewModelScope.launch {
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder()
                    .setCurrentBrushType(type.toProto())
                    .build()
            }
        }
    }

    fun resolvePenType(modifier: StrokeModifier) =
        when (modifier) {
            StrokeModifier.PrimaryButton   -> BrushType.StrokeEraser
            StrokeModifier.SecondaryButton -> BrushType.StrokeEraser
            StrokeModifier.Both            -> BrushType.StrokeEraser
            StrokeModifier.None            -> brushType
        }

    var previousPenType: BrushType? = null
    var isStrokeDown = false

    // --- 绘图操作相关方法 ---
    // 这些方法接收来自UI的事件，然后调用 DrawController 的相应方法
    fun startStroke(point: Offset, modifier: StrokeModifier) {
        finishStroke()  // Oh no! No multitouch! Who cares.

        val newPenType = resolvePenType(modifier)
        if (newPenType != this.brushType) {
            previousPenType = brushType
            switchToPen(newPenType)
        }

        drawController.createPath(point)
        isStrokeDown = true
    }

    fun updateStroke(point: Offset) {
        if (!isStrokeDown) return
        drawController.updateLatestPath(point)
    }

    fun finishStroke() {
        if (!isStrokeDown) return

        drawController.finishPath()

        previousPenType?.let {
            switchToPen(it)
            previousPenType = null
        }
        isStrokeDown = false
    }


    //工具栏-打开/关闭
    fun toggleCanvasVisibility() =
        setCanvasVisibility(!drawingRepository.drawingStore.value.canvasVisible)

    fun setCanvasVisibility(visible: Boolean) {

        //是否启用画布“触摸穿透”模式
        var currentCanvasPassthrough = drawingRepository.drawingStore.value.canvasPassthrough
        var currentPinned = drawingRepository.drawingStore.value.secondDrawerPinnedButtons

        //关闭工具栏时 && 如果自动清空画布
        if (drawingRepository.drawingStore.value.autoClearCanvas && !visible) {
            //清空画布路径
            clearCanvas()

            //关闭画布触摸穿透
            currentCanvasPassthrough = false

            //只显示
            currentPinned = getPinSecondDrawerButtonResult("passthrough", false)
        }

        viewModelScope.launch {
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder()
                    .setCanvasVisible(visible)
                    .setCanvasPassthrough(currentCanvasPassthrough)
                    .setFirstDrawerOpen(!preferences.firstDrawerOpen)
                    .clearSecondDrawerPinnedButtons()
                    .addAllSecondDrawerPinnedButtons(currentPinned)
                    .build()
            }
        }
    }

    //是否触摸穿透画布
    fun toggleCanvasPassthrough() =
        setCanvasPassthrough(!drawingRepository.drawingStore.value.canvasPassthrough)

    fun setCanvasPassthrough(passthrough: Boolean) {
        val newPinned = getPinSecondDrawerButtonResult("passthrough", passthrough)

        viewModelScope.launch {
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder()
                    .setCanvasPassthrough(passthrough)
                    .clearSecondDrawerPinnedButtons()
                    .addAllSecondDrawerPinnedButtons(newPinned)
                    .build()
            }
        }
    }

    fun setPenColor(color: Color) {
        viewModelScope.launch {
            val currentBrushType = drawingRepository.drawingStore.value.currentBrushType.toProto();
            val key = currentBrushType.number
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder().updateStoreBrushConfig(key) { builder ->
                    builder.setColor(color.toArgb())
                }.build()
            }
        }
    }


    fun setStrokeWidth(width: Float) =
        viewModelScope.launch {
            val currentBrushType = drawingRepository.drawingStore.value.currentBrushType.toProto();
            val key = currentBrushType.number
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder().updateStoreBrushConfig(key) { builder ->
                    builder.setWidth(width)
                }.build()
            }
        }

    fun setStrokeAlpha(alpha: Float) =
        viewModelScope.launch {
            val currentBrushType = drawingRepository.drawingStore.value.currentBrushType.toProto();
            val key = currentBrushType.number
            drawingRepository.updateStore{ preferences ->
                preferences.toBuilder().updateStoreBrushConfig(key) { builder ->
                    builder.setAlpha(alpha)
                }.build()
            }
        }

    fun setToolbarPosition(position: Offset, validated: Boolean = false) {
        drawingRepository.updateToolbarPositionState{ it.copy(toolbarPosition = position,positionValidated = validated) }
    }

    //更新临时坐标
    fun updateToolbarPosition2(position: Offset){
        val old = drawingRepository.toolbarPositionState.value.toolbarPosition;
        setToolbarPosition(old + position)
    }

    //保存坐标
    fun saveToolbarPosition() ={
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setToolbarOffsetX(drawingRepository.toolbarPositionState.value.toolbarPosition.x)
                    .setToolbarOffsetY(drawingRepository.toolbarPositionState.value.toolbarPosition.y)
                    .build()
            }
        }
    }



    fun clearCanvas() = drawController.clearPaths()
    fun undo() = drawController.undo()
    fun redo() = drawController.redo()
    private var dimmingJob: Job? = null

    fun resetToolbarTimer() {
        dimmingJob?.cancel()
        setToolbarActive(true)
        dimmingJob = viewModelScope.launch {
            delay(3000L)  // 5 seconds
            setToolbarActive(false)
        }

    }
    fun setToolbarActive(state: Boolean) = {
        //内存
        drawingRepository.updateToolbarPositionState{ it.copy( toolbarActive = state) }
    }




    fun setToolbarOrientation(orientation: ToolbarOrientation) =
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setToolbarOrientation(orientation.toProto())
                    .build()
            }
        }

    fun setFirstDrawerOpen(state: Boolean) =
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setFirstDrawerOpen(state)
                    .build()
            }
        }

    fun toggleSecondDrawer() =
        setSecondDrawerOpen(!drawingRepository.drawingStore.value.secondDrawerOpen)

    fun setSecondDrawerOpen(state: Boolean) =
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setSecondDrawerOpen(!preferences.secondDrawerOpen)
                    .build()
            }
        }


    fun pinSecondDrawerButton(id: String, pinned: Boolean) =
        viewModelScope.launch {
            val buttons =  getPinSecondDrawerButtonResult(id, pinned)
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .clearSecondDrawerPinnedButtons()
                    .addAllSecondDrawerPinnedButtons(buttons)
                    .build()
            }
        }

    private fun getPinSecondDrawerButtonResult(id: String, pinned: Boolean): Set<String> {

        val currentPinned = drawingRepository.drawingStore.value.secondDrawerPinnedButtons

        if (currentPinned.contains(id) == pinned)
            return currentPinned

        return if (pinned)
            currentPinned + id
        else
            currentPinned - id
    }


    fun setAutoClearCanvas(state: Boolean) =
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setAutoClearCanvas(state)
                    .build()
            }
        }

    fun setVisibleOnStart(state: Boolean) =
        viewModelScope.launch {
            drawingRepository.updateStore { preferences ->
                preferences.toBuilder()
                    .setVisibleOnStart(state)
                    .build()
            }
        }


    // 退出应用的方法
    fun quitApplication() {
        stopService()
    }

    fun setService(state:Boolean){
        viewModelScope.launch {
            drawingRepository.updateStore { it->
                it.toBuilder()
                    .setServiceRunning(state)
                    .build()
            }
        }
    }

    //启动服务
     fun startService(){
        viewModelScope.launch {
            drawingRepository.updateStore { it->
                it.toBuilder()
                    .setServiceRunning(true)
                    .build()
            }
        }
        serviceManager.startMainService()
    }

    //停止服务
    fun stopService(){
        serviceManager.stopMainService()
        viewModelScope.launch {
            drawingRepository.updateStore { it->
                it.toBuilder()
                    .setServiceRunning(false)
                    .build()
            }
        }
    }
}