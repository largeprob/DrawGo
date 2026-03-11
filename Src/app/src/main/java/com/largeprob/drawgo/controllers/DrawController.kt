package com.largeprob.drawgo.controllers

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.largeprob.drawgo.data.BrushConfig
import com.largeprob.drawgo.data.BrushType
import com.largeprob.drawgo.repository.DrawingRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.zipWithNext
import kotlin.math.pow
import kotlin.math.sqrt


//笔触路径
data class PathWrapper(
    val id: String = UUID.randomUUID().toString(), // 唯一ID
    val points: SnapshotStateList<Offset>,      // 组成路径的点集合 (使用Compose的快照状态列表，使其变化可被观察)
    private var _cachedPath: MutableState<Path?> = mutableStateOf(null), // 缓存的Path对象
    private var cachedPathInvalid: MutableState<Boolean> = mutableStateOf(true), // 缓存是否失效的标志
    val color: Color,                         // 路径颜色
    val width: Float,                         // 路径宽度
    val alpha: Float                          // 路径不透明度
) {

    // 公开的只读属性，用于获取缓存的Path对象
    val cachedPath: Path
        get() =
            // 如果缓存为空或已失效，则重建路径
            if ((_cachedPath.value == null) or cachedPathInvalid.value)
                rebuildPath().value
            else
                _cachedPath.value!!

    // 重建路径（从所有点重新计算）
    @Suppress("UNCHECKED_CAST")
    private fun rebuildPath(): MutableState<Path> {  // TODO: Find a way to append points to the cached path instead of complete recalculation
        _cachedPath.value = pointsToPath(points)// 调用工具函数将点列表转换为Path对象
        cachedPathInvalid.value = false// 重建后，缓存变为有效
        return _cachedPath as MutableState<Path>
    }

    // 将缓存标记为无效（例如，当路径上添加了新点时调用）
    fun invalidatePath() {
        cachedPathInvalid.value = true
    }

    // 释放缓存（在撤销或清除时调用，以节省内存）
    fun releasePath(): PathWrapper {
        _cachedPath.value = null
        invalidatePath()
        return this
    }

    private fun pointsToPath(points: List<Offset>) = Path().apply {
        if (points.isEmpty())
            return@apply

        moveTo(points.first().x, points.first().y)
        points.zipWithNext().forEachIndexed { index, (start, end) ->
            val mid = calculateMidpoint(start, end)
            if (index == 0)
                lineTo(mid.x, mid.y)
            else
                quadraticTo(start.x, start.y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }

    private fun calculateMidpoint(start: Offset, end: Offset) =
        Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
}


sealed class DrawAction {
    data class AddPath(val pathWrapper: PathWrapper) : DrawAction()
    data class ErasePath(val pathWrapper: PathWrapper) : DrawAction()
    data class ClearPaths(val paths: List<PathWrapper>) : DrawAction()
}

// 核心绘图逻辑控制器
class DrawController @Inject constructor(
    private val drawingRepository: DrawingRepositoryImpl,
) {
    // 当前的画笔配置
    private val penConfig:BrushConfig
        get() = drawingRepository.drawingStore.value.currentBrushConfig!!


    // 存储所有当前在画布上的路径
    private val _pathList = mutableStateListOf<PathWrapper>()
    val pathList: List<PathWrapper>
        get() = _pathList


    // 撤销/重做功能相关的堆栈和状态
    private val maxUndoDepth = 50
    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()


    // 使用StateFlow向ViewModel暴露“是否可撤销/重做/清除”的状态
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _canClear = MutableStateFlow(false)
    val canClearPaths: StateFlow<Boolean> = _canClear.asStateFlow()

    // 当用户手指在屏幕上移动时调用，向最新一条路径添加点
    fun updateLatestPath(newPoint: Offset) {
        if (penConfig.type == BrushType.StrokeEraser) {
            erasePath(newPoint)
            return
        }

        // 否则，在最后一条路径上添加新点，并标记其缓存失效
        _pathList.lastOrNull()?.let { latestPath ->
            latestPath.points.add(newPoint)
            latestPath.invalidatePath()
        }
    }

    // 当用户手指第一次触摸屏幕时调用，创建一条新路径
    fun createPath(newPoint: Offset) {
        if (penConfig.type == BrushType.StrokeEraser) {
            erasePath(newPoint)
            return
        }

        // 根据当前画笔配置创建新的 PathWrapper 并添加到列表中
        _pathList.add(PathWrapper(
            points = mutableStateListOf(newPoint),
            color = penConfig.color,
            width = penConfig.width,
            alpha = penConfig.alpha
        ))
    }

    // 当用户手指离开屏幕时调用，完成一条路径的绘制
    fun finishPath() {
        if (penConfig.type == BrushType.StrokeEraser) return
        if (_pathList.isEmpty()) return

        val latestPath = _pathList.last()

        if (latestPath.points.isEmpty()) {
            _pathList.removeAt(_pathList.lastIndex)
            return
        }

        redoStack.clear()
        addToUndoStack(DrawAction.AddPath(latestPath))
        // 更新UI状态
        updateUndoRedoState()
        updateClearPathsState()
    }


    // 擦除路径的逻辑
    private fun erasePath(point: Offset) {

        val eraserRadius = penConfig.width / 2
        var indexToErase: Int? = null

        for (i in _pathList.indices.reversed()) {
            val pathWrapper = _pathList[i]
            val compensatedRadius = pathWrapper.width / 2 + eraserRadius

            if (pathWrapper.points.size > 1) {
                pathWrapper.points.zipWithNext().forEach { (p1, p2) ->
                    val dist = distancePointToLineSegment(point, p1, p2)
                    if (dist <= compensatedRadius) {
                        indexToErase = i
                        return@forEach
                    }
                }
            } else {
                pathWrapper.points.firstOrNull()?.let {
                    val dist = distance(point, it)
                    if (dist <= compensatedRadius) {
                        indexToErase = i
                    }
                }
            }
            if (indexToErase != null) break
        }

        indexToErase?.let {
            val erasedPath = _pathList.removeAt(it)
            addToUndoStack(DrawAction.ErasePath(erasedPath))
            erasedPath.releasePath()
            redoStack.clear()
            updateUndoRedoState()
            updateClearPathsState()
        }

    }

    private fun distancePointToLineSegment(p: Offset, a: Offset, b: Offset): Float {
        val ap = Offset(p.x - a.x, p.y - a.y)  // Vector from a to p
        val ab = Offset(b.x - a.x, b.y - a.y)  // Vector from a to b

        val ab2 = ab.x.pow(2) + ab.y.pow(2)  // Squared length of segment ab

        if (ab2 == 0f) {  // a and b are the same point
            return distance(p, a)
        }

        // Parameter t of the closest point on the line containing ab
        val t = (ap.x * ab.x + ap.y * ab.y) / ab2

        val closest =
            if (t < 0.0f) {
                // Closest point is a
                a
            } else if (t > 1.0f) {
                // Closest point is b
                b
            } else {
                // Closest point lies on the segment
                Offset(a.x + t * ab.x, a.y + t * ab.y)
            }
        return distance(p, closest)
    }

    private fun distance(p1: Offset, p2: Offset): Float {
        return sqrt(distanceSquared(p1, p2))
    }

    private fun distanceSquared(p1: Offset, p2: Offset): Float {
        return (p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2)
    }


    // 清除所有路径
    fun clearPaths() {
        if (_pathList.isEmpty()) return

        _pathList.forEach {
            it.releasePath()
        }
        addToUndoStack(DrawAction.ClearPaths(_pathList.toList()))
        _pathList.clear()
        redoStack.clear()
        updateUndoRedoState()
        updateClearPathsState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun updateClearPathsState() {
        _canClear.value = _pathList.isNotEmpty()
    }

    private fun addToUndoStack(action:DrawAction) {
        undoStack.add(action)
        if (undoStack.size > maxUndoDepth) {
            undoStack.removeAt(0)
        }
    }

    // 撤销上一步操作
    fun undo() {
        if (undoStack.isEmpty()) return

        //从撤销栈弹出一个动作
        val action = undoStack.removeAt(undoStack.lastIndex)
        when (action) {
            // 如果上一步是“添加”，那就“移除”
            is DrawAction.AddPath -> {
                val whichPath = action.pathWrapper
                if (_pathList.remove(whichPath)) {
                    whichPath.releasePath()
                    redoStack.add(action)
                }
            }
            // 如果上一步是“擦除”，那就“加回来”
            is DrawAction.ErasePath -> {
                val whichPath = action.pathWrapper
                _pathList.add(whichPath)
                redoStack.add(action)
            }
            // 如果上一步是“清除”，那就“全部加回来”
            is DrawAction.ClearPaths -> {
                val whichPaths = action.paths
                _pathList.addAll(whichPaths)
                redoStack.add(action)
            }
        }
        // 将被撤销的动作压入重做栈
        updateUndoRedoState()
        updateClearPathsState()
    }


    // 重做上一步被撤销的操作
    fun redo() {
        if (redoStack.isEmpty()) return

        val action = redoStack.removeAt(redoStack.lastIndex)
        when (action) {
            is DrawAction.AddPath -> {
                val whichPath = action.pathWrapper
                _pathList.add(whichPath)
                addToUndoStack(action)
            }

            is DrawAction.ErasePath -> {
                val whichPath = action.pathWrapper
                if (_pathList.remove(whichPath)) {
                    whichPath.releasePath()
                    addToUndoStack(action)
                }
            }

            is DrawAction.ClearPaths -> {
                val whichPaths = action.paths
                _pathList.removeAll(whichPaths)
                whichPaths.forEach { it.releasePath() }
                addToUndoStack(action)
            }
        }
        updateUndoRedoState()
        updateClearPathsState()
    }

//
//    //给UI层调用的异步协程
//    suspend fun updateSettings(transform: (t: LocalSettingsProto) -> LocalSettingsProto) {
//        localRepository.updateSettings(transform)
//    }
//
//    //本地缓存
//    val localStore = localRepository.localStore.value;
//    val localStoreFlow = localRepository.localStore;
}
