package com.theblacksheep.appoff.di

import android.content.Context
import com.theblacksheep.appoff.domain.usecase.GetSystemAppsUseCase
import com.theblacksheep.appoff.domain.usecase.ManageAppUseCase
import com.theblacksheep.appoff.presentation.mvp.CleanerPresenter
import dagger.Module
import dagger.Provides

@Module
class PresenterModule {

    @Provides
    fun provideCleanerPresenter(
        context: Context,
        getSystemAppsUseCase: GetSystemAppsUseCase,
        manageAppUseCase: ManageAppUseCase
    ): CleanerPresenter {
        return CleanerPresenter(context, getSystemAppsUseCase, manageAppUseCase)
    }
}
