package kth.se.labb3.stepchallenge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kth.se.labb3.stepchallenge.ui.screens.*
import kth.se.labb3.stepchallenge.viewmodel.AuthViewModel
import kth.se.labb3.stepchallenge.viewmodel.LeaderboardViewModel
import kth.se.labb3.stepchallenge.viewmodel.StepViewModel

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Leaderboard : Screen("leaderboard")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
}

/**
 * Main navigation host for the app.
 */
@Composable
fun StepChallengeNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    stepViewModel: StepViewModel,
    leaderboardViewModel: LeaderboardViewModel,
    isLoggedIn: Boolean,
    onRequestHealthPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                stepViewModel = stepViewModel,
                onNavigateToLeaderboard = {
                    navController.navigate(Screen.Leaderboard.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onRequestHealthPermissions = onRequestHealthPermissions
            )
        }

        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(
                viewModel = leaderboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                authViewModel = authViewModel,
                stepViewModel = stepViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                stepViewModel = stepViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}