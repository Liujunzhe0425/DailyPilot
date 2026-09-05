package com.local.dailyautomation.domain

sealed interface BatchEvent {
    data object Locked : BatchEvent
    data object Unlocked : BatchEvent
    data object RunApproved : BatchEvent
    data object RunDeferred : BatchEvent
    data class Pause(val at: Long) : BatchEvent
    data class Resume(val at: Long) : BatchEvent
    data class Tick(val at: Long) : BatchEvent
    data object StopConfirmed : BatchEvent
    data object Finished : BatchEvent
}

data class BatchSnapshot(val state: BatchState, val pausedAt: Long? = null)

object BatchReducer {
    const val PAUSE_TIMEOUT_MILLIS = 180_000L

    fun reduce(snapshot: BatchSnapshot, event: BatchEvent): BatchSnapshot = when (snapshot.state) {
        BatchState.SCHEDULED -> when (event) {
            BatchEvent.Locked -> BatchSnapshot(BatchState.WAITING_UNLOCK)
            BatchEvent.Unlocked -> BatchSnapshot(BatchState.AWAITING_DECISION)
            else -> illegal(snapshot, event)
        }
        BatchState.WAITING_UNLOCK -> when (event) {
            BatchEvent.Unlocked -> BatchSnapshot(BatchState.AWAITING_DECISION)
            else -> illegal(snapshot, event)
        }
        BatchState.AWAITING_DECISION -> when (event) {
            BatchEvent.RunApproved -> BatchSnapshot(BatchState.RUNNING)
            BatchEvent.RunDeferred -> BatchSnapshot(BatchState.MANUAL_ONLY)
            else -> illegal(snapshot, event)
        }
        BatchState.RUNNING -> when (event) {
            is BatchEvent.Pause -> BatchSnapshot(BatchState.PAUSED, event.at)
            BatchEvent.StopConfirmed -> BatchSnapshot(BatchState.STOPPED)
            BatchEvent.Finished -> BatchSnapshot(BatchState.COMPLETED)
            else -> illegal(snapshot, event)
        }
        BatchState.PAUSED -> when (event) {
            is BatchEvent.Resume -> BatchSnapshot(BatchState.RUNNING)
            is BatchEvent.Tick -> if (event.at - requireNotNull(snapshot.pausedAt) >= PAUSE_TIMEOUT_MILLIS) {
                BatchSnapshot(BatchState.STOPPED)
            } else snapshot
            BatchEvent.StopConfirmed -> BatchSnapshot(BatchState.STOPPED)
            else -> illegal(snapshot, event)
        }
        BatchState.COMPLETED, BatchState.FAILED, BatchState.STOPPED, BatchState.EXPIRED, BatchState.MANUAL_ONLY -> illegal(snapshot, event)
    }

    private fun illegal(snapshot: BatchSnapshot, event: BatchEvent): Nothing =
        error("Illegal transition ${snapshot.state} + ${event::class.simpleName}")
}
