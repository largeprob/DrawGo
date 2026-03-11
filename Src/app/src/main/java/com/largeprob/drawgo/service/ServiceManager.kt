package com.largeprob.drawgo.service

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {


    fun startMainService() {
        val service = Intent(context, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }

    fun stopMainService() {
        val service = Intent(context, MainService::class.java)
        context.stopService(service)
    }
}