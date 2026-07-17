package org.veraproject.veravideo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.veraproject.veravideo.BuildConfig
import org.veraproject.veravideo.data.local.PlaylistDao
import org.veraproject.veravideo.data.local.SavedSearchDao
import org.veraproject.veravideo.data.local.VeraDatabase
import org.veraproject.veravideo.data.local.VideoDao
import org.veraproject.veravideo.data.remote.CatalogApi
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VeraDatabase =
        Room.databaseBuilder(context, VeraDatabase::class.java, VeraDatabase.NAME)
            .build()

    @Provides
    fun provideVideoDao(database: VeraDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideSavedSearchDao(database: VeraDatabase): SavedSearchDao = database.savedSearchDao()

    @Provides
    fun providePlaylistDao(database: VeraDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("sync_prefs")
        }

    /**
     * Unknown keys are ignored so that adding a field to the backend's response
     * does not break older installs.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.CATALOG_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideCatalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)
}
