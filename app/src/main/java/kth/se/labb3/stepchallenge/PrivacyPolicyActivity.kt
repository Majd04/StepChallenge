package kth.se.labb3.stepchallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kth.se.labb3.stepchallenge.ui.theme.StepChallengeTheme

/**
 * Privacy Policy Activity required by Health Connect.
 */
class PrivacyPolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StepChallengeTheme {
                PrivacyPolicyScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Step Challenge Privacy Policy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Last updated: December 2024",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrivacySection(
                title = "Data We Collect",
                content = """
                    Step Challenge collects the following health data from Health Connect:
                    
                    • Step count data
                    • Distance walked/run
                    • Calories burned
                    
                    This data is used solely to track your daily activity and display your progress on leaderboards.
                """.trimIndent()
            )

            PrivacySection(
                title = "How We Use Your Data",
                content = """
                    Your health data is used to:
                    
                    • Display your daily, weekly, and monthly step counts
                    • Calculate your progress towards daily goals
                    • Show your rank on leaderboards compared to other users
                    • Provide personalized activity insights
                """.trimIndent()
            )

            PrivacySection(
                title = "Data Storage",
                content = """
                    Your data is stored:
                    
                    • Locally on your device in an encrypted database
                    • In Firebase Firestore for leaderboard functionality
                    
                    We implement industry-standard security measures to protect your data.
                """.trimIndent()
            )

            PrivacySection(
                title = "Data Sharing",
                content = """
                    Your step count and display name are visible to other Step Challenge users on the leaderboard. 
                    
                    We do not sell or share your health data with third parties for advertising or marketing purposes.
                """.trimIndent()
            )

            PrivacySection(
                title = "Your Rights",
                content = """
                    You can:
                    
                    • Revoke Health Connect permissions at any time
                    • Delete your account and associated data
                    • Request a copy of your data
                    
                    To manage your data, go to Settings > Profile in the app.
                """.trimIndent()
            )

            PrivacySection(
                title = "Contact Us",
                content = """
                    If you have questions about this privacy policy or our data practices, please contact us at:
                    
                    stepchallenge@kth.se
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}