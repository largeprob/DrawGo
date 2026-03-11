package com.largeprob.drawgo.ui.main

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.largeprob.drawgo.R
import com.largeprob.drawgo.service.MainService


@Composable
fun MainPage() {
    Box(modifier = Modifier.fillMaxSize()){
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { TopAppBar() }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { Start_This() }
//            item { Spacer(modifier = Modifier.height(16.dp)) }
//            item { Setting_This() }
        }
    }
}

@Composable
fun TopAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(id = R.drawable.main_logo),
            contentDescription = "Logo",
            modifier = Modifier.size(60.dp),
        )
        Text(text = "DrawGo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

fun isServiceRun(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == serviceClass.name
    }
}

@Composable
fun Start_This() {
    val mainViewModel: MainViewModel = hiltViewModel<MainViewModel>()
    val drawingState by mainViewModel.drawingState.collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val service = isServiceRun(context, MainService::class.java);
    var initServiceState  by remember {
        mutableStateOf(service)
    }

    // 1. UI状态直接从ViewModel派生，不再有本地变量
    val isServiceRunning = drawingState.serviceRunning
    val buttonText = if (isServiceRunning && initServiceState) "停止" else "开始"

    var showPermissionDialog by remember { mutableStateOf(false) }
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("为了使应用正常工作，请授予悬浮窗权限。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1A24),
                        contentColor = Color.White
                    )
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDialog = false }) {
                    Text("取消", color = Color(0xFF1E1A24))
                }
            }
        )
    }

    //开始绘图
    Card(
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ,
        shape = RoundedCornerShape(16.dp)
    ) {
        Button(
            onClick = {
                // 2. 所有逻辑都只通过ViewModel来驱动
                if (!isServiceRunning) { // 如果服务未运行，则启动
                    if (!Settings.canDrawOverlays(context)) {
                        showPermissionDialog = true
                    } else {
                        mainViewModel.setService(true)
                        initServiceState = true

                        val intent = Intent(context, MainService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        activity?.finish()
                    }
                } else {
                    // 如果服务正在运行，则停止
                    mainViewModel.setService(false)
//                    val intent = Intent(context, MainService::class.java)
//                    context.stopService(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E1A24),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            // 3. UI文本直接响应ViewModel的状态
            Text(text = buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Setting_This() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "基础设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.draw_go_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(40.dp)
                )
//            Text(text = "Setting", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
