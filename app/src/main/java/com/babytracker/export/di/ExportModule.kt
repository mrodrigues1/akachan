package com.babytracker.export.di

import com.babytracker.BuildConfig
import com.babytracker.export.data.BackupSourceImpl
import com.babytracker.export.data.PdfReportGenerator
import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.PdfReportRenderer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindBackupSource(impl: BackupSourceImpl): BackupSource

    @Binds
    @Singleton
    abstract fun bindPdfReportRenderer(impl: PdfReportGenerator): PdfReportRenderer

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Provides
        @Singleton
        fun provideExportMetadata(): ExportMetadata = ExportMetadata(
            appVersion = BuildConfig.VERSION_NAME,
            // Must equal BabyTrackerDatabase's @Database(version = ...); diagnostics-only field.
            roomSchemaVersion = 3,
        )
    }
}
