package com.largeprob.drawgo.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.largeprob.drawgo.controllers.DrawController
import com.largeprob.drawgo.data.DrawingConfigState
import com.largeprob.drawgo.repository.DrawingRepositoryImpl
import com.largeprob.drawgo.service.ServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val drawingRepository: DrawingRepositoryImpl,
    private val serviceManager: ServiceManager,
) : ViewModel() {

    val drawingState: StateFlow<DrawingConfigState>
        get() = drawingRepository.drawingStore

    fun setService(state:Boolean){
        viewModelScope.launch {
            drawingRepository.updateStore { it->
                it.toBuilder()
                    .setServiceRunning(state)
                    .build()
            }
        }
    }
}