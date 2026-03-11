package com.largeprob.drawgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.largeprob.drawgo.ui.main.DrawViewModel

enum class StrokeModifier {
    None, PrimaryButton, SecondaryButton, Both
}


@Composable
fun DrawCanvas(
    drawViewModel:DrawViewModel,
    modifier: Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {

    // 从控制器获取当前的路径列表
//    val drawViewModel = hiltViewModel<MainViewModel>()
    val pathList = drawViewModel.pathList

    // 使用Jetpack Compose的Canvas组件
    Canvas(
        modifier.pointerInputDrawing(drawViewModel).background(backgroundColor)
    ) {
        // 调用底层的 drawPath API 来绘制路径
        pathList.forEach { pathWrapper ->
            drawPath(
                path = pathWrapper.cachedPath,
                color = pathWrapper.color,
                alpha = pathWrapper.alpha,
                style = Stroke(
                    width = pathWrapper.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

fun Modifier.pointerInputDrawing(
    viewModel: DrawViewModel
): Modifier = pointerInput(Unit) {

    //协程手势处理循环
    awaitEachGesture {

        //挂起协程，等待触点发生，从事件中获取第一个触摸点
        val initialEvent = awaitPointerEvent()
        val initialChange = initialEvent.changes.firstOrNull()

        //如果找不到触摸点，或者触摸点不是“按下”状态，则忽略此次事件，等待下一次手势
        if (initialChange == null || !initialChange.pressed)
            return@awaitEachGesture

        // -------- 笔尖按下 --------
        val strokeModifier = when {
            //如果输入类型不是触控笔（例如是手指），则状态为 None
            initialChange.type != PointerType.Stylus -> StrokeModifier.None
            //如果主、次按钮同时按下，则状态为 Both
            initialEvent.buttons.isPrimaryPressed && initialEvent.buttons.isSecondaryPressed -> StrokeModifier.Both
            //如果只按下了主按钮，则状态为 PrimaryButton
            initialEvent.buttons.isPrimaryPressed -> StrokeModifier.PrimaryButton
            //如果只按下了次要按钮，则状态为 SecondaryButton
            initialEvent.buttons.isSecondaryPressed -> StrokeModifier.SecondaryButton
            else -> StrokeModifier.None
        }

        //🖊新的笔划
        viewModel.startStroke(initialChange.position, strokeModifier)

        // “消费”掉这个“按下”事件，防止它被传递给其他组件（如底层的按钮）
        initialChange.consume()

        // -------- 笔尖移动 --------
        try {
            while (true) {
                //挂起协程，等待下一个指针事件的发生
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == initialChange.id }

                // -------- 笔尖抬起 --------
                if (change == null || !change.pressed)
                    break

                if (change.positionChanged()) {
                    viewModel.updateStroke(change.position)
                    change.consume()
                }
            }
        } finally {
            //🖊笔划结束
            viewModel.finishStroke()
        }
    }
}