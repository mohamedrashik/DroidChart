package com.droid.bootchart.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droid.bootchart.domain.BootchartImportException
import com.droid.bootchart.domain.BootchartReport
import com.droid.bootchart.domain.ImportBootchart

/**
 * UI presentation state for the DroidChart home screen.
 *
 * @property importing `true` while an import operation is running in the background.
 * @property report The actively displayed [BootchartReport], or `null` if none loaded.
 * @property error Actionable error message if the last import operation failed, or `null`.
 */
internal data class HomeState(
    val importing: Boolean = false,
    val report: BootchartReport? = null,
    val error: String? = null,
)

/**
 * State holder managing import lifecycle and preserving previous report state across failures.
 *
 * @property importBootchart Use case instance for executing capture import.
 */
internal class HomeStateHolder(private val importBootchart: ImportBootchart) {
    var state by mutableStateOf(HomeState())
        private set

    /**
     * Triggers the import workflow. Prevents concurrent imports and retains
     * any previously loaded report if the user cancels or an error occurs.
     */
    suspend fun import() {
        if (state.importing) return
        val previous = state.report
        state = state.copy(importing = true, error = null)
        try {
            val report = importBootchart()
            state = HomeState(report = report ?: previous)
        } catch (error: BootchartImportException) {
            state = HomeState(report = previous, error = error.message)
        } finally {
            state = state.copy(importing = false)
        }
    }
}
