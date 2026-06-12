package com.babytracker.sharing.data.repository

import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingRepositoryImpl @Inject constructor(
    private val service: FirestoreSharingService,
) : SharingRepository {

    override suspend fun signInAnonymously(): String =
        service.signInAnonymously()

    override suspend fun createShareDocument(code: ShareCode, ownerUid: String) =
        service.createShareDocument(code.value, ownerUid)

    override suspend fun isShareCodeValid(code: ShareCode): Boolean =
        service.isShareCodeValid(code.value)

    override suspend fun syncFullSnapshot(code: ShareCode, snapshot: ShareSnapshot) =
        service.syncFullSnapshot(code.value, snapshot)

    override suspend fun syncSessions(
        code: ShareCode,
        sessions: List<SessionSnapshot>,
        prediction: SleepPredictionSnapshot?,
    ) = service.syncSessions(code.value, sessions, prediction)

    override suspend fun syncSleepRecords(
        code: ShareCode,
        sleepRecords: List<SleepSnapshot>,
        prediction: SleepPredictionSnapshot?,
    ) = service.syncSleepRecords(code.value, sleepRecords, prediction)

    override suspend fun syncBottleFeeds(code: ShareCode, bottleFeeds: List<BottleFeedSnapshot>) =
        service.syncBottleFeeds(code.value, bottleFeeds)

    override suspend fun syncBaby(code: ShareCode, baby: BabySnapshot) =
        service.syncBaby(code.value, baby)

    override suspend fun syncInventory(
        code: ShareCode,
        fields: InventorySnapshotFields,
        milkBags: List<MilkBagSnapshot>,
    ) = service.syncInventory(code.value, fields, milkBags)

    override suspend fun registerPartner(code: ShareCode, partnerUid: String) =
        service.registerPartner(code.value, partnerUid)

    override suspend fun fetchSnapshot(code: ShareCode): ShareSnapshot =
        service.fetchSnapshot(code.value)

    override suspend fun isPartnerConnected(code: ShareCode, partnerUid: String): Boolean =
        service.isPartnerConnected(code.value, partnerUid)

    override suspend fun getPartners(code: ShareCode): List<PartnerInfo> =
        service.getPartners(code.value)

    override suspend fun revokePartner(code: ShareCode, partnerUid: String) =
        service.revokePartner(code.value, partnerUid)

    override suspend fun deleteShareDocument(code: ShareCode) =
        service.deleteShareDocument(code.value)

    override fun observeFeedOps(code: ShareCode): Flow<List<FeedOp>> =
        service.observeFeedOps(code.value)

    override fun writeFeedOp(code: ShareCode, op: FeedOp) =
        service.writeFeedOp(code.value, op)

    override fun observeOwnFeedOps(code: ShareCode, uid: String): Flow<List<FeedOp>> =
        service.observeOwnFeedOps(code.value, uid)

    override suspend fun deleteFeedOps(code: ShareCode, opIds: List<String>) =
        service.deleteFeedOps(code.value, opIds)
}
