package com.theblacksheep.appoff.di

import com.theblacksheep.appoff.CleanerApp
import com.theblacksheep.appoff.MainActivity
import com.theblacksheep.appoff.domain.usecase.GetSystemAppsUseCase
import com.theblacksheep.appoff.domain.usecase.ManageAppUseCase
import com.theblacksheep.appoff.presentation.mvp.CleanerPresenter
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class, UseCaseModule::class, PresenterModule::class])
interface AppComponent {
    fun inject(app: CleanerApp)
    fun inject(activity: MainActivity)

    fun getCleanerPresenter(): CleanerPresenter
    fun getGetSystemAppsUseCase(): GetSystemAppsUseCase
    fun getManageAppUseCase(): ManageAppUseCase
}
