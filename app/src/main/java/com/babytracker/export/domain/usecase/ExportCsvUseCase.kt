package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.TrackingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    private val source: BackupSource,
) {
    suspend operator fun invoke(): Map<String, String> {
        val t = source.readTracking()
        // Building N CSV tables (string concat + escaping over every row) is CPU-bound; run it off
        // the main thread. The Room read above already runs off-main via the BackupSource.
        return withContext(Dispatchers.Default) { buildTables(t) }
    }

    private fun buildTables(t: TrackingSnapshot): Map<String, String> =
        mapOf(
            "breastfeeding" to buildCsv(
                listOf(
                    "id", "start_time", "end_time", "starting_side",
                    "switch_time", "notes", "paused_at", "paused_duration_ms",
                ),
                t.breastfeeding.map {
                    listOf(
                        it.id, it.startTime, it.endTime, it.startingSide,
                        it.switchTime, it.notes, it.pausedAt, it.pausedDurationMs,
                    )
                },
            ),
            "sleep" to buildCsv(
                listOf("id", "start_time", "end_time", "sleep_type", "notes"),
                t.sleep.map { listOf(it.id, it.startTime, it.endTime, it.sleepType, it.notes) },
            ),
            "pumping" to buildCsv(
                listOf(
                    "id", "start_time", "end_time", "breast",
                    "volume_ml", "notes", "paused_at", "paused_duration_ms",
                ),
                t.pumping.map {
                    listOf(
                        it.id, it.startTime, it.endTime, it.breast,
                        it.volumeMl, it.notes, it.pausedAt, it.pausedDurationMs,
                    )
                },
            ),
            "milk_bags" to buildCsv(
                listOf(
                    "id", "collection_date", "volume_ml", "source_session_id",
                    "used_at", "notes", "created_at",
                ),
                t.milkBags.map {
                    listOf(
                        it.id, it.collectionDate, it.volumeMl, it.sourceSessionId,
                        it.usedAt, it.notes, it.createdAt,
                    )
                },
            ),
            "bottle_feeds" to buildCsv(
                listOf(
                    "id", "timestamp", "volume_ml", "type",
                    "linked_milk_bag_id", "notes", "created_at",
                ),
                t.bottleFeeds.map {
                    listOf(
                        it.id, it.timestamp, it.volumeMl, it.type,
                        it.linkedMilkBagId, it.notes, it.createdAt,
                    )
                },
            ),
            "diapers" to buildCsv(
                listOf("id", "timestamp", "type", "notes", "created_at"),
                t.diapers.map {
                    listOf(it.id, it.timestamp, it.type, it.notes, it.createdAt)
                },
            ),
            "vaccines" to buildCsv(
                listOf(
                    "id", "name", "dose_label", "status",
                    "scheduled_date", "administered_date", "notes", "created_at",
                ),
                t.vaccines.map {
                    listOf(
                        it.id, it.name, it.doseLabel, it.status,
                        it.scheduledDate, it.administeredDate, it.notes, it.createdAt,
                    )
                },
            ),
        ) + doctorVisitCsvSections(t)

    private fun doctorVisitCsvSections(t: TrackingSnapshot): Map<String, String> = mapOf(
        "doctor_visits" to buildCsv(
            listOf(
                "id", "date", "provider_name", "notes",
                "snapshot_label", "snapshot_created_at", "created_at",
            ),
            t.doctorVisits.map {
                listOf(
                    it.id, it.date, it.providerName, it.notes,
                    it.snapshotLabel, it.snapshotCreatedAt, it.createdAt,
                )
            },
        ),
        "visit_questions" to buildCsv(
            listOf("id", "text", "answered", "visit_id", "created_at"),
            t.visitQuestions.map {
                listOf(it.id, it.text, it.answered, it.visitId, it.createdAt)
            },
        ),
    )

    private fun buildCsv(header: List<String>, rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { escape(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun escape(value: Any?): String {
        val raw = value?.toString() ?: ""
        val safe = if (startsWithFormulaTrigger(raw)) "'$raw" else raw
        return if (needsQuoting(safe)) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun needsQuoting(s: String): Boolean =
        s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }

    private fun startsWithFormulaTrigger(s: String): Boolean {
        val firstNonSpace = s.firstOrNull { !it.isWhitespace() } ?: return false
        return firstNonSpace == '=' || firstNonSpace == '+' ||
            firstNonSpace == '-' || firstNonSpace == '@'
    }
}
