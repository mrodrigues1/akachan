package com.babytracker.domain.model

/**
 * Visual expiration state of a milk stash bag, derived from its collection date,
 * the configured expiration window, and the current date.
 *
 * - [NONE] more than one day until expiration (no visual treatment)
 * - [EXPIRING_SOON] expires exactly tomorrow
 * - [EXPIRING_OR_EXPIRED] expires today or any past date
 */
enum class ExpirationStatus {
    NONE,
    EXPIRING_SOON,
    EXPIRING_OR_EXPIRED,
}
