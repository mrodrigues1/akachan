package com.babytracker.sharing.domain.repository

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot

interface SharingRepository {
    suspend fun signInAnonymously(): String
    suspend fun createShareDocument(code: ShareCode, ownerUid: String)
    suspend fun isShareCodeValid(code: ShareCode): Boolean
    suspend fun syncFullSnapshot(code: ShareCode, snapshot: ShareSnapshot)
    suspend fun syncSessions(code: ShareCode, sessions: List<SessionSnapshot>)
    suspend fun syncSleepRecords(code: ShareCode, sleepRecords: List<SleepSnapshot>)
    suspend fun syncBaby(code: ShareCode, baby: BabySnapshot)
    suspend fun registerPartner(code: ShareCode, partnerUid: String)
    suspend fun fetchSnapshot(code: ShareCode): ShareSnapshot
    suspend fun isPartnerConnected(code: ShareCode, partnerUid: String): Boolean
    suspend fun getPartners(code: ShareCode): List<PartnerInfo>
    suspend fun revokePartner(code: ShareCode, partnerUid: String)
    suspend fun deleteShareDocument(code: ShareCode)
}
