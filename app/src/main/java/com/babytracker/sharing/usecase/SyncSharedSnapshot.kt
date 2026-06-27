package com.babytracker.sharing.usecase

/**
 * Best-effort full resync of the partner snapshot, swallowing any failure. A no-op unless this
 * device is the sharing PRIMARY, so the infrequent-edit ViewModels (growth, milestones) can fire it
 * after a write without partial-sync plumbing or error handling of their own.
 */
suspend fun SyncToFirestoreUseCase.syncSharedSnapshot() {
    runCatching { invoke() }
}
