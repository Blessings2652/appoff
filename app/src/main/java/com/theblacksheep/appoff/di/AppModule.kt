package com.theblacksheep.appoff.di

import android.app.Application
import android.content.Context
import com.theblacksheep.appoff.data.repository.AppRepositoryImpl
import com.theblacksheep.appoff.data.repository.JunkRepositoryImpl
import com.theblacksheep.appoff.data.repository.SystemStatsRepositoryImpl
import com.theblacksheep.appoff.domain.repository.IAppRepository
import com.theblacksheep.appoff.domain.repository.IJunkRepository
import com.theblacksheep.appoff.domain.repository.ISystemStatsRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule(private val application: Application) {

    @Provides
    @Singleton
    fun provideApplication(): Application = application

    @Provides
    @Singleton
    fun provideContext(): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideAppRepository(): IAppRepository = AppRepositoryImpl()

    @Provides
    @Singleton
    fun provideJunkRepository(): IJunkRepository = JunkRepositoryImpl()

    @Provides
    @Singleton
    fun provideSystemStatsRepository(): ISystemStatsRepository = SystemStatsRepositoryImpl()
}
