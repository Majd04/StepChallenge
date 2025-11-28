package se.kth.stepchallenge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.kth.stepchallenge.data.model.LeaderboardEntry
import se.kth.stepchallenge.data.model.LeaderboardPeriod
import se.kth.stepchallenge.data.repository.LeaderboardRepository

/**
 * UI state for leaderboard screen.
 */
data class LeaderboardUiState(
    val isLoading: Boolean = false,
    val selectedPeriod: LeaderboardPeriod = LeaderboardPeriod.WEEKLY,
    val entries: List<LeaderboardEntry> = emptyList(),
    val currentUserRank: Int = 0,
    val currentUserSteps: Long = 0,
    val stepsToNextRank: Long? = null,
    val stepsToFirstPlace: Long? = null,
    val error: String? = null
)

/**
 * ViewModel for leaderboard functionality.
 */
class LeaderboardViewModel(
    private val leaderboardRepository: LeaderboardRepository = LeaderboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    /**
     * Set the current user ID and load leaderboard.
     */
    fun setUserId(userId: String) {
        currentUserId = userId
        loadLeaderboard()
    }

    /**
     * Load leaderboard for the selected period.
     */
    fun loadLeaderboard() {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Collect leaderboard updates
            leaderboardRepository.getLeaderboardFlow(
                period = _uiState.value.selectedPeriod,
                currentUserId = userId
            ).collect { entries ->
                val currentUserEntry = entries.find { it.isCurrentUser }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    entries = entries,
                    currentUserRank = currentUserEntry?.rank ?: 0,
                    currentUserSteps = currentUserEntry?.steps ?: 0
                )

                // Calculate steps needed for next rank and first place
                calculateStepsNeeded(userId)
            }
        }
    }

    /**
     * Change the leaderboard period.
     */
    fun selectPeriod(period: LeaderboardPeriod) {
        if (period != _uiState.value.selectedPeriod) {
            _uiState.value = _uiState.value.copy(selectedPeriod = period)
            loadLeaderboard()
        }
    }

    /**
     * Calculate steps needed to reach next rank and first place.
     */
    private fun calculateStepsNeeded(userId: String) {
        viewModelScope.launch {
            val currentRank = _uiState.value.currentUserRank
            val period = _uiState.value.selectedPeriod

            if (currentRank > 1) {
                // Calculate steps to next rank
                val stepsToNext = leaderboardRepository.getStepsNeededForRank(
                    targetRank = currentRank - 1,
                    period = period,
                    currentUserId = userId
                )

                // Calculate steps to first place
                val stepsToFirst = if (currentRank > 1) {
                    leaderboardRepository.getStepsNeededForRank(
                        targetRank = 1,
                        period = period,
                        currentUserId = userId
                    )
                } else null

                _uiState.value = _uiState.value.copy(
                    stepsToNextRank = stepsToNext,
                    stepsToFirstPlace = stepsToFirst
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    stepsToNextRank = null,
                    stepsToFirstPlace = null
                )
            }
        }
    }

    /**
     * Refresh leaderboard data.
     */
    fun refresh() {
        loadLeaderboard()
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LeaderboardViewModel() as T
            }
        }
    }
}