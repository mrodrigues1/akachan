package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Throwable.isPartnerAccessRevokedError(): Boolean =
    this is PartnerAccessRevokedException || isPermissionDenied()

suspend fun Throwable.toPartnerAccessRevokedExceptionAfterClearing(
    settingsRepository: SettingsRepository,
    code: ShareCode,
): PartnerAccessRevokedException? {
    if (!isPartnerAccessRevokedError()) return null
    settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
    return PartnerAccessRevokedException("Partner access revoked", this)
}

fun Throwable.clearPartnerStateIfRevokedLater(
    settingsRepository: SettingsRepository,
    code: ShareCode,
    scope: CoroutineScope,
) {
    if (!isPartnerAccessRevokedError()) return
    scope.launch {
        settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
    }
}

private fun Throwable.isPermissionDenied(): Boolean =
    generateSequence(this) { it.cause }.any {
        (it as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
