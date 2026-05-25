package com.babytracker.export.domain

/** Backup was written by a newer app version than this build can read. */
class BackupTooNewException(val backupFormatVersion: Int) :
    Exception("Backup format v$backupFormatVersion is newer than this app supports")

/** Backup content failed validation (bad enum, dangling milk-bag FK, etc.). Nothing is written. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
