package com.babytracker.manager

import com.babytracker.sharing.usecase.PartnerSleepNotification

/**
 * Posts the "main partner" notification after a batch of partner sleep ops applies on the primary
 * device (start / stop / edit). Coalesces the batch into a single notification.
 */
interface PartnerSleepNotifier {

    /**
     * @param notifications partner sleep changes applied in the just-finished batch, in apply order.
     *   Empty list is a no-op; a multi-change batch posts one notification for the most recent change.
     */
    suspend fun notifySleepChanges(notifications: List<PartnerSleepNotification>)
}
