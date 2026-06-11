package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    private val source: BackupSource,
) {
    suspend operator fun invoke(): Map<String, String> {
        val t = source.readTracking()
        return mapOf(
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
        )
    }

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
        s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')

    private fun startsWithFormulaTrigger(s: String): Boolean {
        val firstNonSpace = s.firstOrNull { !it.isWhitespace() } ?: return false
        return firstNonSpace == '=' || firstNonSpace == '+' ||
            firstNonSpace == '-' || firstNonSpace == '@'
    }
}
