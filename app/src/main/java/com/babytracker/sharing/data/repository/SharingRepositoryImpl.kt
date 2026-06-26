package com.babytracker.sharing.data.repository

import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.deleteSleepOps
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.data.firebase.writeSleepOp
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
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

    override suspend fun syncDiapers(code: ShareCode, diapers: List<DiaperSnapshot>) =
        service.syncDiapers(code.value, diapers)

    override suspend fun syncBaby(code: ShareCode, baby: BabySnapshot) =
        service.syncBaby(code.value, baby)

    override suspend fun syncInventory(
        code: ShareCode,
        fields: InventorySnapshotFields,
        milkBags: List<MilkBagSnapshot>,
    ) = service.syncInventory(code.value, fields, milkBags)

    override suspend fun syncBottleFeedsAndInventory(
        code: ShareCode,
        bottleFeeds: List<BottleFeedSnapshot>,
        fields: InventorySnapshotFields,
        milkBags: List<MilkBagSnapshot>,
    ) = service.syncBottleFeedsAndInventory(code.value, bottleFeeds, fields, milkBags)

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

    override suspend fun writeFeedOp(
        code: ShareCode,
        op: FeedOp,
        onFailure: (Throwable) -> Unit,
    ) = service.writeFeedOp(code.value, op, onFailure)

    override fun observeOwnFeedOps(code: ShareCode, uid: String): Flow<List<FeedOp>> =
        service.observeFeedOps(code.value, authorUid = uid)

    override suspend fun deleteFeedOps(code: ShareCode, opIds: List<String>) =
        service.deleteFeedOps(code.value, opIds)

    override fun observeSleepOps(code: ShareCode): Flow<List<SleepOp>> =
        service.observeSleepOps(code.value)

    override suspend fun writeSleepOp(
        code: ShareCode,
        op: SleepOp,
        onFailure: (Throwable) -> Unit,
    ) = service.writeSleepOp(code.value, op, onFailure)

    override fun observeOwnSleepOps(code: ShareCode, uid: String): Flow<List<SleepOp>> =
        service.observeSleepOps(code.value, authorUid = uid)

    override suspend fun deleteSleepOps(code: ShareCode, opIds: List<String>) =
        service.deleteSleepOps(code.value, opIds)
}
