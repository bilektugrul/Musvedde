package dev.aliarslan.musvedde.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.aliarslan.musvedde.data.lock.KeyWrapper
import dev.aliarslan.musvedde.data.lock.KeystoreKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig

@Module
@InstallIn(SingletonComponent::class)
object LockModule {
    @Provides
    fun lockConfig(): LockConfig = LockConfig()
}

@Module
@InstallIn(SingletonComponent::class)
interface LockBindings {
    @Binds
    fun keyWrapper(wrapper: KeystoreKeyWrapper): KeyWrapper
}
