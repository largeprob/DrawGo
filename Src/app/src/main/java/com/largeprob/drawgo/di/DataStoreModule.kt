package com.largeprob.drawgo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.largeprob.drawgo.DrawingDataProto
import com.largeprob.drawgo.controllers.DrawController
import com.largeprob.drawgo.repository.DrawingDataProtoSerializer
import com.largeprob.drawgo.repository.DrawingRepositoryImpl
import com.largeprob.drawgo.service.ServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@InstallIn(SingletonComponent::class)
@Module
object DataStoreModule {

    private const val DATA_STORE_FILE_NAME = "user_drawing_settings.pb"
    //缓存
    @Provides
    @Singleton
    fun provideDrawingDataProto(@ApplicationContext appContext: Context)
    : DataStore<DrawingDataProto> {
        return DataStoreFactory.create(
            serializer = DrawingDataProtoSerializer,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { appContext.dataStoreFile(DATA_STORE_FILE_NAME) }
        )
    }

    //仓储
    @Provides
    @Singleton
    fun provideDrawingRepositoryImpl(dataStore: DataStore<DrawingDataProto>,scope: CoroutineScope): DrawingRepositoryImpl {
        return DrawingRepositoryImpl(dataStore,scope)
    }

    //控制器
    @Provides
    @Singleton
    fun provideDrawController(repository: DrawingRepositoryImpl): DrawController {
        return DrawController(repository)
    }

    //仓储作用域
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }


}