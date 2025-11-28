package kth.se.labb3.stepchallenge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kth.se.labb3.stepchallenge.data.repository.StepRepository
import kth.se.labb3.stepchallenge.ui.navigation.StepChallengeNavHost
import kth.se.labb3.stepchallenge.ui.theme.StepChallengeTheme
import kth.se.labb3.stepchallenge.viewmodel.AuthViewModel
import kth.se.labb3.stepchallenge.viewmodel.LeaderboardViewModel
import kth.se.labb3.stepchallenge.viewmodel.StepViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.Factory }
    private val stepViewModel: StepViewModel by viewModels()
    private val leaderboardViewModel: LeaderboardViewModel by viewModels { LeaderboardViewModel.Factory }

    // Health Connect permission request launcher
    private val healthConnectPermissionRequest = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(StepRepository.PERMISSIONS)) {
            stepViewModel.checkPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StepChallengeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authState by authViewModel.uiState.collectAsState()

                    // Set user ID for step and leaderboard ViewModels when logged in
                    authState.user?.let { user ->
                        stepViewModel.setUserId(user.id)
                        leaderboardViewModel.setUserId(user.id)
                    }

                    StepChallengeNavHost(
                        navController = navController,
                        authViewModel = authViewModel,
                        stepViewModel = stepViewModel,
                        leaderboardViewModel = leaderboardViewModel,
                        isLoggedIn = authState.isLoggedIn,
                        onRequestHealthPermissions = { requestHealthConnectPermissions() }
                    )
                }
            }
        }

        checkHealthConnectAvailability()
    }

    private fun checkHealthConnectAvailability() {
        val status = HealthConnectClient.getSdkStatus(this)
        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                // Health Connect is not available on this device
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                // Health Connect needs to be updated
            }
            HealthConnectClient.SDK_AVAILABLE -> {
                lifecycleScope.launch {
                    stepViewModel.checkPermissions()
                }
            }
        }
    }

    private fun requestHealthConnectPermissions() {
        val status = HealthConnectClient.getSdkStatus(this)

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                // Show message that Health Connect is not available
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                // Prompt to install/update Health Connect
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                }
                startActivity(intent)
            }
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnectPermissionRequest.launch(StepRepository.PERMISSIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (authViewModel.uiState.value.isLoggedIn) {
            stepViewModel.checkPermissions()
        }
    }
}