package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.model.isOverdue
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Drives the redesigned Vaccine landing screen: the most urgent record (overdue or next up), the
 * parent's own upcoming schedule, and a short strip of recently given vaccines. Composes the
 * existing observe/mutate use cases rather than introducing new persistence; the redesign is
 * presentation-only.
 *
 * "Mark given" is a deferred commit: the record is optimistically moved out of the schedule (and
 * shown as just-given) but the write only fires once the undo snackbar is dismissed. Undo therefore
 * never touches the database, side-stepping the "scheduled date must be in the future" validation
 * that re-marking through an edit/add would hit for an overdue record.
 */
data class VaccineDashboardUiState(
    val isLoading: Boolean = true,
    /** Set when an upstream flow throws, so the screen can offer a retry instead of a dead spinner. */
    val isError: Boolean = false,
    /** Soonest scheduled vaccine whose date is today or later. Drives the hero when nothing is overdue. */
    val nextVaccine: VaccineRecord? = null,
    /**
     * Every upcoming vaccine sharing the soonest future calendar day, earliest first. Usually one entry;
     * holds the whole set when multiple doses fall on the same day so the hero can list them all.
     */
    val nextVaccines: List<VaccineRecord> = emptyList(),
    /** Whole-day countdown to [nextVaccine], against the injected clock (Today = 0). */
    val nextInDays: Int? = null,
    /** The most overdue scheduled vaccine, if any. Takes hero priority over [nextVaccine]. */
    val mostOverdue: VaccineRecord? = null,
    /** Whole days [mostOverdue] is past due (always >= 1 when present). */
    val mostOverdueDays: Int? = null,
    val overdueCount: Int = 0,
    /** The full upcoming list shown in the schedule section: overdue first (earliest), then future. */
    val schedule: List<VaccineRecord> = emptyList(),
    /** Most recent administered vaccines, newest first, capped for the dashboard preview. */
    val recentlyGiven: List<VaccineRecord> = emptyList(),
    /** Total administered count (incl. an in-flight mark-given), used to gate "View all". */
    val givenCount: Int = 0,
    /** The vaccine inside the mark-given undo window, held so the screen can offer an undo snackbar. */
    val lastMarkedGiven: VaccineRecord? = null,
    /** The vaccine inside the delete undo window, held so the screen can offer an undo snackbar. */
    val lastDeleted: VaccineRecord? = null,
    val now: Instant = Instant.EPOCH,
) {
    /** True on a clean install: nothing scheduled, nothing recorded. */
    val isFirstRun: Boolean get() = schedule.isEmpty() && givenCount == 0
}

@HiltViewModel
class VaccineDashboardViewModel @Inject constructor(
    observeRecords: ObserveVaccineRecordsUseCase,
    private val markGivenUseCase: MarkVaccineAdministeredUseCase,
    private val deleteUseCase: DeleteVaccineRecordUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) : ViewModel() {

    // The record inside the mark-given undo window: optimistically treated as administered until the
    // snackbar is dismissed, then committed. One flow drives both the optimistic hide and the undo
    // snackbar, so a single mark-given is one atomic emission (no intermediate "hidden but no
    // snackbar" frame). Held outside the data flow so it survives Room re-emissions.
    private val pendingMarkGiven = MutableStateFlow<VaccineRecord?>(null)

    // The record inside the delete undo window: optimistically hidden, deleted only once the snackbar
    // is dismissed. Same deferred-commit pattern as the history screen.
    private val pendingDelete = MutableStateFlow<VaccineRecord?>(null)

    // Bumped by onRetry so flatMapLatest rebuilds the combined flow after an upstream failure;
    // a plain .catch would emit the error once and leave the flow terminated with no way back.
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineDashboardUiState> =
        retryTrigger.flatMapLatest {
            combine(observeRecords(), pendingMarkGiven, pendingDelete) { records, pendingMark, pendingDel ->
                val instant = now()
                val today = instant.atZone(zone).toLocalDate()
                // Optimistically hide a record inside its delete-undo window before anything else.
                val visible = records.filterNot { it.id == pendingDel?.id }

                val scheduled = visible.filter {
                    it.status == VaccineStatus.SCHEDULED && it.scheduledDate != null && it.id != pendingMark?.id
                }
                // Overdue is day-based: a dose due today is "next up" (countdown shows Today), never overdue.
                val overdue = scheduled
                    .filter { it.isOverdue(instant, zone) }
                    .sortedBy { it.scheduledDate }
                val future = scheduled
                    .filterNot { it.isOverdue(instant, zone) }
                    // Stable same-day order: by date, then name, so a tie renders the same way every emission.
                    .sortedWith(compareBy({ it.scheduledDate }, { it.name }))

                val administered = visible.filter { it.status == VaccineStatus.ADMINISTERED }
                // Optimistically fold the pending record in as if given now, so marking it doesn't make
                // the row vanish for the length of the undo window.
                val optimisticPending = pendingMark?.copy(
                    status = VaccineStatus.ADMINISTERED,
                    administeredDate = instant,
                )
                val given = (listOfNotNull(optimisticPending) + administered)
                    .sortedByDescending { it.administeredDate ?: it.createdAt }

                val mostOverdue = overdue.firstOrNull()
                val next = future.firstOrNull()
                // The hero lists every dose sharing the soonest upcoming day, not just the first.
                val nextDay = next?.scheduledDate?.atZone(zone)?.toLocalDate()
                val nextVaccines = if (nextDay == null) {
                    emptyList()
                } else {
                    future.filter { it.scheduledDate!!.atZone(zone).toLocalDate() == nextDay }
                }
                VaccineDashboardUiState(
                    isLoading = false,
                    nextVaccine = next,
                    nextVaccines = nextVaccines,
                    nextInDays = next?.scheduledDate?.let { daysBetween(today, it) },
                    mostOverdue = mostOverdue,
                    mostOverdueDays = mostOverdue?.scheduledDate?.let { -daysBetween(today, it) },
                    overdueCount = overdue.size,
                    schedule = overdue + future,
                    recentlyGiven = given.take(RECENT_LIMIT),
                    givenCount = given.size,
                    lastMarkedGiven = pendingMark,
                    lastDeleted = pendingDel,
                    now = instant,
                )
            }.catch {
                emit(VaccineDashboardUiState(isLoading = false, isError = true))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = VaccineDashboardUiState(),
        )

    private fun daysBetween(today: LocalDate, date: Instant): Int =
        ChronoUnit.DAYS.between(today, date.atZone(zone).toLocalDate()).toInt()

    /** Start the undo window for marking [record] given. Finalizes any prior pending mark first. */
    fun markGiven(record: VaccineRecord) {
        flushPending()
        pendingMarkGiven.value = record
    }

    /** Snackbar "Undo": nothing was written yet, so just reveal the record back in the schedule. */
    fun undoMarkGiven() {
        pendingMarkGiven.value = null
    }

    /** Snackbar dismissed / timed out: finalize the mark-given write. */
    fun onMarkGivenConsumed() {
        flushPending()
    }

    fun onRetry() {
        retryTrigger.value++
    }

    private fun flushPending() {
        val record = pendingMarkGiven.value ?: return
        pendingMarkGiven.value = null
        viewModelScope.launch { runCatching { markGivenUseCase(record.id, now()) } }
    }

    /** Start the undo window for deleting [record]; finalizes any prior pending delete first. */
    fun requestDelete(record: VaccineRecord) {
        flushPendingDelete()
        pendingDelete.value = record
    }

    /** Snackbar "Undo": nothing was written yet, so just reveal the record again. */
    fun undoDelete() {
        pendingDelete.value = null
    }

    /** Snackbar dismissed / timed out: finalize the delete. */
    fun onDeleteConsumed() {
        flushPendingDelete()
    }

    private fun flushPendingDelete() {
        val record = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { runCatching { deleteUseCase(record.id) } }
    }

    private companion object {
        const val RECENT_LIMIT = 3
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
