package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.InstalledAppsSheet
import com.example.ui.screens.ComponentsTab
import com.example.ui.screens.FilesTab
import com.example.ui.screens.OverviewTab
import com.example.ui.screens.PermissionsTab
import com.example.ui.screens.SecurityTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkMapApp(
    viewModel: ApkMapViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val isInstalledAppsSheetVisible by viewModel.isInstalledAppsSheetVisible.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val installedAppsSearch by viewModel.installedAppsSearch.collectAsState()
    val filterUserAppsOnly by viewModel.filterUserAppsOnly.collectAsState()

    // File Picker for APK
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.mapApkFromUri(context, uri)
        }
    }

    // Load initial map (current app) if needed on launch
    LaunchedEffect(Unit) {
        viewModel.mapCurrentApp(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "APK Mapper Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Interactive APK Map & Structure",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Pick APK File
                    IconButton(
                        onClick = {
                            apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                        },
                        modifier = Modifier.testTag("open_apk_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = stringResource(R.string.action_open_apk)
                        )
                    }

                    // Installed Apps
                    IconButton(
                        onClick = { viewModel.openInstalledAppsSheet(context) },
                        modifier = Modifier.testTag("installed_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = stringResource(R.string.action_installed_apps)
                        )
                    }

                    // Share Map Report
                    if (uiState is ApkUiState.Loaded) {
                        IconButton(
                            onClick = {
                                val state = uiState as ApkUiState.Loaded
                                val report = viewModel.generateReportText(state.data)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, report)
                                    putExtra(Intent.EXTRA_TITLE, "APK Map: ${state.data.summary.appName}")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share APK Map Report")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.testTag("share_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share_report)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ApkUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Analyzing DEX bytecode, components, assets, and signatures...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                is ApkUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    text = "APK Analysis Issue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.mapCurrentApp(context) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Map Self")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.openInstalledAppsSheet(context) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Pick App")
                                    }
                                }
                            }
                        }
                    }
                }

                is ApkUiState.Loaded -> {
                    val mapData = state.data

                    // Navigation Tabs
                    ScrollableTabRow(
                        selectedTabIndex = activeTab.ordinal,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_map_tab_row")
                    ) {
                        ApkTab.values().forEach { tab ->
                            val isSelected = activeTab == tab
                            Tab(
                                selected = isSelected,
                                onClick = { viewModel.setActiveTab(tab) },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            ApkTab.OVERVIEW -> Icons.Default.AccountTree
                                            ApkTab.COMPONENTS -> Icons.Default.Widgets
                                            ApkTab.PERMISSIONS -> Icons.Default.Security
                                            ApkTab.FILES -> Icons.Default.FolderOpen
                                            ApkTab.SECURITY -> Icons.Default.Lock
                                        },
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    // Content Screen per Tab
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TabContent"
                    ) { currentTab ->
                        when (currentTab) {
                            ApkTab.OVERVIEW -> {
                                OverviewTab(data = mapData)
                            }

                            ApkTab.COMPONENTS -> {
                                val componentQuery by viewModel.componentQuery.collectAsState()
                                val componentTypeFilter by viewModel.componentTypeFilter.collectAsState()
                                val onlyExported by viewModel.onlyExportedComponents.collectAsState()

                                ComponentsTab(
                                    components = mapData.components,
                                    searchQuery = componentQuery,
                                    onSearchChange = { viewModel.setComponentQuery(it) },
                                    selectedType = componentTypeFilter,
                                    onTypeSelect = { viewModel.setComponentTypeFilter(it) },
                                    onlyExported = onlyExported,
                                    onToggleExported = { viewModel.toggleOnlyExported() }
                                )
                            }

                            ApkTab.PERMISSIONS -> {
                                val permQuery by viewModel.permissionQuery.collectAsState()
                                val onlyDangerous by viewModel.onlyDangerousPerms.collectAsState()

                                PermissionsTab(
                                    permissions = mapData.permissions,
                                    searchQuery = permQuery,
                                    onSearchChange = { viewModel.setPermissionQuery(it) },
                                    onlyDangerous = onlyDangerous,
                                    onToggleDangerous = { viewModel.toggleOnlyDangerous() }
                                )
                            }

                            ApkTab.FILES -> {
                                val fileQuery by viewModel.fileQuery.collectAsState()
                                val fileCategoryFilter by viewModel.fileCategoryFilter.collectAsState()

                                FilesTab(
                                    files = mapData.files,
                                    searchQuery = fileQuery,
                                    onSearchChange = { viewModel.setFileQuery(it) },
                                    selectedCategory = fileCategoryFilter,
                                    onCategorySelect = { viewModel.setFileCategoryFilter(it) }
                                )
                            }

                            ApkTab.SECURITY -> {
                                SecurityTab(security = mapData.security)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Installed Apps
    if (isInstalledAppsSheetVisible) {
        InstalledAppsSheet(
            apps = installedApps,
            searchQuery = installedAppsSearch,
            onSearchChange = { viewModel.setInstalledAppsSearch(it) },
            userAppsOnly = filterUserAppsOnly,
            onToggleUserApps = { viewModel.toggleFilterUserAppsOnly() },
            onAppSelect = { app ->
                viewModel.mapInstalledApp(context, app)
            },
            onDismiss = { viewModel.closeInstalledAppsSheet() }
        )
    }
}
