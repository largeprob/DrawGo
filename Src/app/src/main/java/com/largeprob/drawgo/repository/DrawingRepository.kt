package com.largeprob.drawgo.repository

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.largeprob.drawgo.BrushConfigProto
import com.largeprob.drawgo.BrushTypeProto
import com.largeprob.drawgo.DrawingDataProto
import com.largeprob.drawgo.ToolbarOrientationProto
import com.largeprob.drawgo.data.DrawingConfigState
import com.largeprob.drawgo.data.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

//proto序列化提供器
object DrawingDataProtoSerializer : Serializer<DrawingDataProto> {

    //初始化默认值
    //override val defaultValue: DrawingSettingsProto = DrawingSettingsProto.getDefaultInstance();
    override val defaultValue: DrawingDataProto = DrawingDataProto.newBuilder()
        .setCanvasVisible(true)
        .setCanvasPassthrough(false)
        .setAutoClearCanvas(false)
        .setVisibleOnStart(true)
        .setCurrentBrushType(BrushTypeProto.FeltTipPen)
        .putBrushConfigs(BrushTypeProto.FeltTipPen.number,
            BrushConfigProto.newBuilder()
                .setType(BrushTypeProto.FeltTipPen)
                .setColor(Color.Black.toArgb())
                .setWidth(5f)
                .setAlpha(1f)
                .build()
        )
        .setToolbarOrientation(ToolbarOrientationProto.HORIZONTAL)
        .setToolbarOffsetX(32f)
        .setToolbarOffsetY(64f)
        .setFirstDrawerOpen(true)
        .setSecondDrawerOpen(false)
        .addAllFirstDrawerButtons(listOf("undo","redo","clear", "tool_controls", "color_picker","passthrough"))
        .addAllSecondDrawerButtons(listOf("settings"))
        .setServiceRunning(false)
        .build()

    override suspend fun readFrom(input: InputStream): DrawingDataProto {
        try {
            return DrawingDataProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: DrawingDataProto, output: OutputStream) = t.writeTo(output)
}



//基础状态
data class ToolbarPositionState(

    // 临时坐标
    val toolbarPosition: Offset = Offset(32f, 64f),

    // 不需要保存的瞬时状态
    val positionValidated: Boolean = false, // 工具栏位置是否已经过边界检查
    val toolbarActive: Boolean = true // 工具栏当前是否是激活状态（未变暗）
)




class DrawingRepositoryImpl  @Inject constructor(
    private val dataStore: DataStore<DrawingDataProto>,
    private val scope: CoroutineScope
) {

    //内部可操作数据源
    private  val  _drawingState: MutableStateFlow<DrawingConfigState> = MutableStateFlow(DrawingConfigState())
    //对外访问的数据源
    val drawingStore: StateFlow<DrawingConfigState> = _drawingState.asStateFlow()

    //服务状态
    private var _toolbarPositionState = MutableStateFlow(ToolbarPositionState())
    val toolbarPositionState: StateFlow<ToolbarPositionState> = _toolbarPositionState.asStateFlow()


    init {
        //从磁盘取出缓存的数据
       loadInitialData()
    }

    private  fun loadInitialData() {
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    Log.e("DrawingDataProto", "Error reading DrawingDataProto", e)
                    emit(DrawingDataProto.getDefaultInstance())
                } else {
                    throw e
                }
            }
            .map { proto ->
                proto.toDomain()
            }
            .distinctUntilChanged()
            .onEach { state ->
//                println("持续更新domain域的值")
                Log.d("DrawingRepository", state.toLoggableString())
                _drawingState.value = state
            }
            .launchIn(scope)
    }

    //修改内存
    fun updateToolbarPositionState(transform: (t: ToolbarPositionState) ->  ToolbarPositionState) {
        _toolbarPositionState.update(transform)
    }

    //修改缓存
    suspend fun updateStore(transform: (t: DrawingDataProto) -> DrawingDataProto) {
        dataStore.updateData(transform)
    }
}

//扩展函数
fun DrawingDataProto.Builder.updateStoreBrushConfig(
    key: Int,
    transform: (BrushConfigProto.Builder) -> BrushConfigProto.Builder
): DrawingDataProto.Builder {

    // 1. 获取当前配置的 Builder
    val currentConfig = this.getBrushConfigsOrThrow(key).toBuilder()

    // 2. 调用 Lambda 进行修改
    val newConfig = transform(currentConfig).build()

    // 3. 执行 Protobuf Map 的 remove + put 替换模式
    return this.removeBrushConfigs(key)
        .putBrushConfigs(key, newConfig)
}


fun DrawingConfigState.toLoggableString(): String {

    // 使用 buildString 来高效拼接字符串
    return buildString {
        append("DrawingConfigState发生了变化重新赋值 {\n")
        append("DrawingConfigState {\n")

        // --- 布尔值 ---
        append("    canvasVisible: $canvasVisible\n")
        append("    canvasPassthrough: $canvasPassthrough\n")
        append("    autoClearCanvas: $autoClearCanvas\n")
        append("    visibleOnStart: $visibleOnStart\n")

        // --- 笔刷设置 ---
        append("    currentBrushType: $currentBrushType\n")

        // Map 属性：打印大小和部分内容
        append("    brushConfigs: (Size: ${brushConfigs.size})\n")
        brushConfigs.forEach { (type, config) ->
            // 示例：打印 Map 的每一项
            append("      -> $type: ${config.color} (Width: ${config.width})\n")
        }

        // --- 工具栏设置 ---
        append("    toolbarOrientation: $toolbarOrientation\n")
        append("    toolbarOffsetX: $toolbarOffsetX\n")
        append("    toolbarOffsetY: $toolbarOffsetY\n")

        // --- 抽屉设置 (Set 类型只打印大小) ---
        append("    firstDrawerOpen: $firstDrawerOpen\n")
        append("    secondDrawerOpen: $secondDrawerOpen\n")
        append("    firstDrawerButtons: (Size: ${firstDrawerButtons.size})\n")
        append("    secondDrawerButtons: (Size: ${secondDrawerButtons.size})\n")
        append("    secondDrawerPinnedButtons: (Size: ${secondDrawerPinnedButtons.size})\n")

        // --- 服务状态 ---
        append("    serviceRunning: $serviceRunning\n")

        // --- 计算属性 ---
        append("    toolbarPosition: $toolbarPosition\n")
        append("    currentBrushConfig: ${currentBrushConfig}\n")

        append("}")
    }
}