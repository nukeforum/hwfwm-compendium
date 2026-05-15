package wizardry.compendium.drive.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupStatusDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DriveBackupIoScope

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBackupModule {

    @Binds
    abstract fun bindDriveAuth(impl: DriveAuthGmsImpl): DriveAuth

    @Binds
    abstract fun bindDriveClient(impl: DriveAppFolderRestClient): DriveAppFolderClient

    @Binds
    abstract fun bindScheduler(impl: WorkManagerBackupScheduler): BackupScheduler

    @Binds
    abstract fun bindCoordinator(impl: BackupCoordinator): BackupCoordinatorApi

    @Binds
    abstract fun bindWireCodec(impl: WirePayloadCodec): WirePayloadCodecApi

    @Binds
    abstract fun bindStatusStore(impl: BackupStatusStore): BackupStatusStoreApi

    @Binds
    abstract fun bindTokenCache(impl: TokenCache): TokenCacheApi

    companion object {
        @Provides
        @Singleton
        @BackupStatusDataStore
        fun provideStatusDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("compendium_backup_status") },
            )

        @Provides
        @Singleton
        @DriveTokenDataStore
        fun provideTokenDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("compendium_drive_token") },
            )

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideOkHttp(): OkHttpClient =
            OkHttpClient.Builder().build()

        @Provides
        @Singleton
        @DriveBackupIoScope
        fun provideIoScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
