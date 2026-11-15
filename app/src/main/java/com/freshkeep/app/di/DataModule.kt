package com.freshkeep.app.di

import android.content.Context
import androidx.room.Room
import com.freshkeep.app.data.local.FreshKeepDatabase
import com.freshkeep.app.data.local.GeschiedenisDao
import com.freshkeep.app.data.local.ProductDao
import com.freshkeep.app.data.local.RoomTransactieUitvoerder
import com.freshkeep.app.data.local.TransactieUitvoerder
import com.freshkeep.app.data.local.VoorraadDao
import com.freshkeep.app.data.remote.AndroidNetwerkstatus
import com.freshkeep.app.data.remote.Netwerkstatus
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FreshKeepDatabase =
        Room.databaseBuilder(context, FreshKeepDatabase::class.java, "freshkeep.db").build()

    @Provides
    fun productDao(db: FreshKeepDatabase): ProductDao = db.productDao()

    @Provides
    fun voorraadDao(db: FreshKeepDatabase): VoorraadDao = db.voorraadDao()

    @Provides
    fun geschiedenisDao(db: FreshKeepDatabase): GeschiedenisDao = db.geschiedenisDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindModule {
    @Binds
    abstract fun transacties(impl: RoomTransactieUitvoerder): TransactieUitvoerder

    @Binds
    abstract fun netwerkstatus(impl: AndroidNetwerkstatus): Netwerkstatus
}
