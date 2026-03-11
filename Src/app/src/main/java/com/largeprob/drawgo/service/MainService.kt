package com.largeprob.drawgo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.round
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.largeprob.drawgo.Draw.CustomLifecycleOwner
import com.largeprob.drawgo.ui.DrawCanvas
import com.largeprob.drawgo.ui.DrawToolbar
import com.largeprob.drawgo.R
import com.largeprob.drawgo.controllers.DrawController
import com.largeprob.drawgo.repository.DrawingRepositoryImpl
import com.largeprob.drawgo.repository.ToolbarPositionState
import com.largeprob.drawgo.ui.main.DrawViewModel
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainService:  Service() {

    @Inject
    lateinit var drawingRepository: DrawingRepositoryImpl

    @Inject
    lateinit var serviceManager: ServiceManager

    @Inject
    lateinit var drawController: DrawController

    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "default_channel"
    }

    // 生命周期管理，用于Compose界面的生命周期控制
    private val customLifecycleOwner = CustomLifecycleOwner()

    // 窗口管理器，用于添加和管理悬浮窗口
    private lateinit var windowManager: WindowManager

    // 画布视图 - 主要的绘图区域
    private lateinit var canvasView: View

    // 工具栏视图 - 包含绘图工具和设置
    private lateinit var toolbarView: View

    // 协程作业，用于监听状态变化
    private var uiStateJob: Job? = null
    private var serviceStateJob: Job? = null

    private var toolbarPositionStateJob: Job? = null

    private lateinit var viewModel: DrawViewModel

    override fun onBind(intent: Intent?): IBinder? = null


    override fun onCreate() {
        super.onCreate()

        Log.d("MainService", "onCreate")

        //初始化ViewModel
        viewModel = DrawViewModel(
            drawingRepository = drawingRepository,
            serviceManager = serviceManager,
            drawController = drawController,
        )


        //启动Compose生命周期
        customLifecycleOwner.onCreate()
        customLifecycleOwner.onResume()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建通知渠道并启动前台服务
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )



        // -------- 创建画布 ComposeView --------
        canvasView = ComposeView(this).apply {
            setContent {
                DrawCanvas(
                    viewModel,
                    modifier = Modifier.Companion.fillMaxSize(),
                    backgroundColor = Color.Companion.Transparent  // 透明背景，不影响底层应用
                )
            }
        }
        customLifecycleOwner.attachToDecorView(canvasView)


        // 画布窗口参数配置
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,   // 宽度：充满屏幕
            WindowManager.LayoutParams.MATCH_PARENT,   // 高度：充满屏幕
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // 类型：应用悬浮窗
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or        // 标志：不获取焦点
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or   // 标志：非模态触摸
//                    LayoutParams.FLAG_LAYOUT_NO_LIMITS or  // 标志：无布局限制（已注释）
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,    // 标志：在屏幕内布局
            PixelFormat.TRANSLUCENT  // 像素格式：半透明
        )
        // 处理画布穿透设置（允许触摸事件穿透到下层应用）
        handleCanvasPassthrough(canvasParams)


        // -------- 设置工具栏视图 --------
        toolbarView = ComposeView(this).apply {
            setContent {
                DrawToolbar(viewModel)  // 设置工具栏内容
            }
        }
        customLifecycleOwner.attachToDecorView(toolbarView)

        // 工具栏窗口参数配置
        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,   // 宽度：包裹内容
            WindowManager.LayoutParams.WRAP_CONTENT,   // 高度：包裹内容
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // 类型：应用悬浮窗
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or        // 标志：不获取焦点
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or   // 标志：非模态触摸
//                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or  // 标志：无布局限制（已注释）
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,    // 标志：在屏幕内布局
            PixelFormat.TRANSLUCENT  // 像素格式：半透明
        )
        // 初始重力设置：左上角
        toolbarParams.gravity = Gravity.TOP or Gravity.START

        // 处理工具栏位置设置
        handleToolbarPosition(drawingRepository.toolbarPositionState.value,toolbarParams, windowManager, toolbarView)

        // 将视图添加到窗口管理器
        windowManager.addView(canvasView, canvasParams)
        windowManager.addView(toolbarView, toolbarParams)

        // 监听UI状态变化
        uiStateJob = CoroutineScope(Dispatchers.Main).launch {
            drawingRepository.drawingStore.collect { state ->
                // 更新画布穿透设置
                handleCanvasPassthrough(canvasParams)
                windowManager.updateViewLayout(canvasView, canvasParams)

                // 控制画布可见性
                canvasView.visibility = if (state.canvasVisible)
                    View.VISIBLE else View.GONE
            }
        }

        // 监听工具栏状态变化
        serviceStateJob = CoroutineScope(Dispatchers.Main).launch {
            drawingRepository.toolbarPositionState.collect { state ->
                println("updateToolbarPosition2订阅变化，x${state.toolbarPosition.x},y${state.toolbarPosition.y}")

                // 更新工具栏位置
                handleToolbarPosition(state,toolbarParams,windowManager, toolbarView)

                println("updateToolbarPosition2订阅变化toolbarParams，x$toolbarParams.x},y${toolbarParams.y}")
                windowManager.updateViewLayout(toolbarView, toolbarParams)

                // 根据工具栏激活状态设置透明度动画
                val targetAlpha = if (state.toolbarActive) 1.0f else 0.5f
                toolbarView.animate()
                    .alpha(targetAlpha)
                    .setDuration(300)  // 动画持续时间：300毫秒
                    .start()
            }
        }

        // 监听服务状态
        CoroutineScope(Dispatchers.Main).launch {
            drawingRepository.drawingStore.collect { state ->
                if (state.serviceRunning==false){
                    Log.d("MainService", "stop Service")
                    stopService()
                }
            }
        }
    }

    fun stopService(){
        super.onDestroy()

        uiStateJob?.cancel()
        serviceStateJob?.cancel()

        try {
            windowManager.removeView(canvasView)
            windowManager.removeView(toolbarView)
        }catch (e:Exception){}


        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        customLifecycleOwner.onDestroy()
    }




    /**
     * 处理工具栏位置设置和验证
     * 确保工具栏始终在屏幕可见范围内
     *
     * @param toolbarParams 工具栏窗口参数
     * @param state 当前服务状态
     * @param windowManager 窗口管理器
     * @param toolbarView 工具栏视图
     * @param viewModel 视图模型
     */
    private fun handleToolbarPosition(
        state:ToolbarPositionState,
        toolbarParams: WindowManager.LayoutParams,
        windowManager: WindowManager,
        toolbarView: View
    ) {
        println("首次加载")
        //获取当前坐标
        val rounded = state.toolbarPosition.round()

        // 位置已验证，直接设置
        if (state.positionValidated) {
            toolbarParams.x = rounded.x
            toolbarParams.y = rounded.y
        } else {

            // 位置未验证，进行边界检查并修正
            val (screenWidth, screenHeight) = getUsableScreenSize(windowManager)
            val coercedX = rounded.x.coerceIn(0, screenWidth - toolbarView.width)
            val coercedY = rounded.y.coerceIn(0, screenHeight - toolbarView.height)

            viewModel.setToolbarPosition(
                Offset(coercedX.toFloat(),coercedY.toFloat()),
                true
            )
        }
    }


    /**
     * 获取可用的屏幕尺寸（排除导航栏等系统UI区域）
     *
     * @param windowManager 窗口管理器
     * @return 可用的屏幕宽度和高度
     */
    @Suppress("DEPRECATION")
    private fun getUsableScreenSize(windowManager: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android R (API 30) 及以上版本使用新的窗口度量API
            val windowMetrics = windowManager.maximumWindowMetrics
            val insets = windowMetrics.windowInsets.getInsets(
                WindowInsets.Type.navigationBars()  // 获取导航栏插入区域
            )
            val bounds = windowMetrics.bounds
            val usableWidth = bounds.width() - insets.left - insets.right
            val usableHeight = bounds.height() - insets.top - insets.bottom
            usableWidth to usableHeight
        } else {
            // 旧版本Android使用传统方法
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            size.x to size.y
        }

    /**
     * 处理画布穿透设置
     * 当启用穿透模式时，触摸事件会传递到底层应用
     *
     * @param canvasParams 画布窗口参数
     * @param state 当前UI状态
     */
    private fun handleCanvasPassthrough(
        canvasParams: WindowManager.LayoutParams
    ) {
        canvasParams.flags = if (drawingRepository.drawingStore.value.canvasPassthrough)
            canvasParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE  // 启用不可触摸标志
        else
            canvasParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()  // 禁用不可触摸标志
    }

    private fun createNotificationChannel() {
        // 检查当前设备是否运行在 Android O (API 26) 或更高版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW  // 低重要性，不会打扰用户
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     *
     * @return 配置好的通知对象
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_edit)  // 使用系统编辑图标
            .setPriority(NotificationCompat.PRIORITY_LOW)   // 低优先级
            .setOngoing(true)  // 持续通知，用户无法清除
            .build()
    }
}