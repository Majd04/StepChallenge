package kth.se.labb3.stepchallenge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController
import kth.se.labb3.stepchallenge.data.repository.StepRepository
import kth.se.labb3.stepchallenge.service.StepCounterService
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
            // Start background service when permissions granted
            startStepCounterService()
        }
    }

    // Notification permission request launcher (Android 13+)
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, service can show notifications
        }
    }

    // Activity Recognition permission request launcher (required for foreground service health)
    private val activityRecognitionPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, now we can start the service
            startStepCounterServiceIfReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        requestNotificationPermission()

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

                        // Start background step tracking service
                        startStepCounterService(user.id)
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

    /**
     * Request notification permission for Android 13+.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    // Request permission
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * Start the background step counter service (only if permissions granted).
     */
    private fun startStepCounterService(userId: String? = null) {
        // Check if ACTIVITY_RECOGNITION permission is granted first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission granted, start service
                    startStepCounterServiceIfReady(userId)
                }
                else -> {
                    // Request permission
                    activityRecognitionPermissionRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        } else {
            // No permission needed for older Android versions
            startStepCounterServiceIfReady(userId)
        }
    }

    /**
     * Actually start the service after permissions are confirmed.
     */
    private fun startStepCounterServiceIfReady(userId: String? = null) {
        val user = authViewModel.uiState.value.user
        val dailyGoal = stepViewModel.uiState.value.dailyGoal

        if (user != null || userId != null) {
            StepCounterService.start(
                context = this,
                userId = userId ?: user?.id,
                dailyGoal = dailyGoal
            )
        }
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
            // Restart service to ensure it's running
            startStepCounterService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: We don't stop the service here because we want it to continue
        // tracking steps in the background. It will be stopped when the user
        // logs out or explicitly stops it.
    }
}