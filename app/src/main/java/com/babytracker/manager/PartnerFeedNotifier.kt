package com.babytracker.manager

/**
 * Posts the "main partner" notification when partner-authored bottle feeds consume stash milk.
 *
 * Called on the primary device after a batch of partner feed ops applies. The implementation gates
 * on the user setting, resolves the consumed bag volumes, and posts a single coalesced notification.
 */
interface PartnerFeedNotifier {

    /**
     * @param consumedBagIds bag ids freshly consumed by partner feeds in the just-applied batch.
     *   Empty list is a no-op. Each id corresponds to one partner bottle feed.
     */
    suspend fun notifyStashConsumed(consumedBagIds: List<Long>)
}
