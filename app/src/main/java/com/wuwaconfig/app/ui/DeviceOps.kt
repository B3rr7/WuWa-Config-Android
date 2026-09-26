package com.wuwaconfig.app.ui

import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * App-scoped serializer for every device-touching operation (connect, deploy,
 * backup, clean, analyze). Lives on [com.wuwaconfig.app.WuWaConfigApp] because
 * the backend itself is app-scoped: operations must survive ViewModel teardown
 * and stay mutually exclusive across ViewModels.
 *
 * Cancellation propagates — a cancelled job unwinds through its own finally
 * blocks before the lock is released, so a new op cannot start while the old
 * one is still writing to the device.
 */
class DeviceOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val mutex = Mutex()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _operationCancelled = MutableStateFlow(false)
    val operationCancelled: StateFlow<Boolean> = _operationCancelled.asStateFlow()

    /**
     * The job that owns the device lock right now. Only ever assigned when the
     * launched job is still live, so a busy-path request (which finishes
     * immediately) can never replace the real operation and make [requestCancel]
     * cancel a dead job while the device write continues.
     */
    internal var activeJob: Job? = null

    /**
     * Runs [block] under the exclusive device lock.
     *
     * @param managesBusyFlag when true, the caller owns [_isApplying] and this
     *   helper clears it if the lock cannot be acquired (busy path).
     */
    fun launchBackendOp(
        managesBusyFlag: Boolean,
        block: suspend CoroutineScope.() -> Unit,
    ): Job =
        scope.launch {
            if (!mutex.tryLock()) {
                // NOTE: _operationCancelled is deliberately NOT reset here — a
                // dropped request must not clear the cancel flag of the operation
                // that is actually running.
                LogRepository.add("Busy: another device operation is still running", LogLevel.WARNING)
                if (managesBusyFlag) _isApplying.value = false
                return@launch
            }
            _operationCancelled.value = false
            try {
                block()
            } catch (e: CancellationException) {
                LogRepository.add("Operation cancelled.", LogLevel.WARNING)
                throw e
            } finally {
                mutex.unlock()
            }
        }.also { if (!it.isCompleted) activeJob = it }

    /**
     * Cancels the active job and drops the connection so in-flight shell work
     * aborts promptly. The job's own finally clears [_isApplying].
     */
    fun requestCancel(
        disconnect: () -> Unit,
        resetBackendStatus: () -> Unit,
    ) {
        if (!_isApplying.value) return
        _operationCancelled.value = true
        activeJob?.cancel()
        disconnect()
        resetBackendStatus()
        LogRepository.add("Cancelling operation...")
    }

    internal fun setApplying(value: Boolean) {
        _isApplying.value = value
    }
}
