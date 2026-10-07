package com.theblacksheep.appoff.di

import com.theblacksheep.appoff.domain.repository.IAppRepository
import com.theblacksheep.appoff.domain.usecase.GetSystemAppsUseCase
import com.theblacksheep.appoff.domain.usecase.ManageAppUseCase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class UseCaseModule {

    @Provides
    @Singleton
    fun provideGetSystemAppsUseCase(appRepository: IAppRepository): GetSystemAppsUseCase {
        return GetSystemAppsUseCase(appRepository)
    }

    @Provides
    @Singleton
    fun provideManageAppUseCase(): ManageAppUseCase {
        return ManageAppUseCase()
    }
}
