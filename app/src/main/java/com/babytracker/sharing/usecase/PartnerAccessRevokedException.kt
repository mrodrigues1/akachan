package com.babytracker.sharing.usecase

/**
 * Thrown by [FetchPartnerDataUseCase] only when a partner's access has been *confirmed* revoked
 * (the share document no longer lists this partner's UID). Subtypes [IllegalStateException] so that
 * existing `catch (IllegalStateException)` handlers (e.g. PartnerDashboardViewModel) keep working,
 * while callers that must act only on a confirmed revoke can match this exact type.
 */
class PartnerAccessRevokedException(message: String) : IllegalStateException(message)
