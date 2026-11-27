package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.attemptAutoSignIn
import com.example.core.configureAppCheck
import com.example.ui.components.GlobalStatusTopBar
import com.example.ui.components.GoogleSignInCard
import com.example.ui.screens.HybridLabScreen
import com.example.ui.screens.SceneScanScreen
import com.example.ui.screens.TriageScreen
import com.example.ui.screens.VaultLogsScreen
import com.example.ui.theme.AegisPulseTheme
import com.example.viewmodel.AegisAppContainer
import com.example.viewmodel.HybridLabViewModel
import com.example.viewmodel.SceneScanViewModel
import com.example.viewmodel.TriageViewModel
import com.example.viewmodel.VaultLogsViewModel
import kotlinx.coroutines.launch

private data class AegisDestination(
    val index: Int,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val AegisDestinations = listOf(
    AegisDestination(0, R.string.nav_triage, Icons.Filled.HealthAndSafety, Icons.Outlined.HealthAndSafety, "nav_tab_triage"),
    AegisDestination(1, R.string.nav_scene_scan, Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner, "nav_tab_scene_scan"),
    AegisDestination(2, R.string.nav_vault_logs, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_tab_vault_logs"),
    AegisDestination(3, R.string.nav_hybrid_lab, Icons.Filled.Science, Icons.Outlined.Science, "nav_tab_hybrid_lab")
)

class MainActivity : ComponentActivity() {

    private lateinit var container: AegisAppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        container = AegisAppContainer(applicationContext)

        setContent {
            val isDarkMode by container.localVaultStore.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            val scope = rememberCoroutineScope()

            AegisPulseTheme(darkTheme = isDarkMode) {
                AegisPulseRoot(
                    container = container,
                    isDarkTheme = isDarkMode,
                    onToggleTheme = {
                        scope.launch { container.localVaultStore.setDarkMode(!isDarkMode) }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::container.isInitialized) {
            container.dispose()
        }
    }
}

@Composable
private fun AegisPulseRoot(
    container: AegisAppContainer,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Silent background check for previously authorized Google account
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope
        )
    }

    val triageViewModel: TriageViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TriageViewModel(
                    router = container.hybridAIRouter,
                    incidentRepository = container.incidentRepository,
                    ttsSpeaker = container.ttsSpeaker
                )
            }
        }
    )

    val sceneScanViewModel: SceneScanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SceneScanViewModel(
                    router = container.hybridAIRouter,
                    connectivityObserver = container.connectivityObserver,
                    incidentRepository = container.incidentRepository,
                    ttsSpeaker = container.ttsSpeaker
                )
            }
        }
    )

    val vaultLogsViewModel: VaultLogsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                VaultLogsViewModel(
                    localVaultStore = container.localVaultStore,
                    incidentRepository = container.incidentRepository,
                    router = container.hybridAIRouter
                )
            }
        }
    )

    val hybridLabViewModel: HybridLabViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HybridLabViewModel(
                    router = container.hybridAIRouter,
                    connectivityObserver = container.connectivityObserver
                )
            }
        }
    )

    val isOnline by container.connectivityObserver.isOnline.collectAsStateWithLifecycle()
    val onDeviceStatus by container.hybridAIRouter.onDeviceStatus.collectAsStateWithLifecycle()
    val remotePolicy by container.hybridAIRouter.remotePolicy.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSignInPromptDialog by remember { mutableStateOf(false) }
    var signInPromptError by remember { mutableStateOf<String?>(null) }
    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Support back button returning to Triage home tab from secondary tabs
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                GlobalStatusTopBar(
                    isOnline = isOnline,
                    onDeviceStatus = onDeviceStatus,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        AegisDestinations.forEach { dest ->
                            val selected = selectedTab == dest.index
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AegisDestinations.forEach { dest ->
                            val selected = selectedTab == dest.index
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "aegis_tab_transition",
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> TriageScreen(
                                viewModel = triageViewModel,
                                disclaimerText = remotePolicy.emergencyDisclaimerText,
                                onRequestSignInForSave = {
                                    pendingSaveAction = {
                                        triageViewModel.saveCurrentTriageToLog {}
                                    }
                                    signInPromptError = null
                                    showSignInPromptDialog = true
                                }
                            )
                            1 -> SceneScanScreen(
                                viewModel = sceneScanViewModel,
                                disclaimerText = remotePolicy.emergencyDisclaimerText,
                                onRequestSignInForSave = {
                                    pendingSaveAction = {
                                        sceneScanViewModel.saveAssessmentToLog {}
                                    }
                                    signInPromptError = null
                                    showSignInPromptDialog = true
                                }
                            )
                            2 -> VaultLogsScreen(
                                viewModel = vaultLogsViewModel,
                                disclaimerText = remotePolicy.emergencyDisclaimerText
                            )
                            else -> HybridLabScreen(
                                viewModel = hybridLabViewModel,
                                disclaimerText = remotePolicy.emergencyDisclaimerText
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSignInPromptDialog) {
        AlertDialog(
            onDismissRequest = { showSignInPromptDialog = false },
            title = { Text("Sign In to Sync Incident Log") },
            text = {
                Column {
                    GoogleSignInCard(
                        title = "Google Sign-In Required",
                        subtitle = "Your saved incidents sync securely to your private Cloud Firestore timeline with offline queueing.",
                        errorMessage = signInPromptError,
                        onAuthSuccess = {
                            showSignInPromptDialog = false
                            pendingSaveAction?.invoke()
                            pendingSaveAction = null
                        },
                        onAuthError = { msg -> signInPromptError = msg }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSignInPromptDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }
}
