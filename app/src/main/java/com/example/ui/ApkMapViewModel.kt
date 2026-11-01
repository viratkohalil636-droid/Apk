package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ApkMapData
import com.example.model.CategoryType
import com.example.model.ComponentType
import com.example.model.InstalledAppItem
import com.example.parser.ApkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class ApkTab(val title: String) {
    OVERVIEW("Structure"),
    COMPONENTS("Components"),
    PERMISSIONS("Permissions"),
    FILES("File Tree"),
    SECURITY("Security")
}

sealed interface ApkUiState {
    data class Loading(val message: String = "Analyzing APK...") : ApkUiState
    data class Loaded(val data: ApkMapData) : ApkUiState
    data class Error(val error: String) : ApkUiState
}

class ApkMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ApkUiState>(ApkUiState.Loading("Initializing APK Mapper..."))
    val uiState: StateFlow<ApkUiState> = _uiState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isInstalledAppsSheetVisible = MutableStateFlow(false)
    val isInstalledAppsSheetVisible: StateFlow<Boolean> = _isInstalledAppsSheetVisible.asStateFlow()

    private val _installedAppsSearch = MutableStateFlow("")
    val installedAppsSearch: StateFlow<String> = _installedAppsSearch.asStateFlow()

    private val _filterUserAppsOnly = MutableStateFlow(true)
    val filterUserAppsOnly: StateFlow<Boolean> = _filterUserAppsOnly.asStateFlow()

    private val _activeTab = MutableStateFlow(ApkTab.OVERVIEW)
    val activeTab: StateFlow<ApkTab> = _activeTab.asStateFlow()

    // Component Filters
    private val _componentQuery = MutableStateFlow("")
    val componentQuery: StateFlow<String> = _componentQuery.asStateFlow()

    private val _componentTypeFilter = MutableStateFlow<ComponentType?>(null)
    val componentTypeFilter: StateFlow<ComponentType?> = _componentTypeFilter.asStateFlow()

    private val _onlyExportedComponents = MutableStateFlow(false)
    val onlyExportedComponents: StateFlow<Boolean> = _onlyExportedComponents.asStateFlow()

    // Permission Filters
    private val _permissionQuery = MutableStateFlow("")
    val permissionQuery: StateFlow<String> = _permissionQuery.asStateFlow()

    private val _onlyDangerousPerms = MutableStateFlow(false)
    val onlyDangerousPerms: StateFlow<Boolean> = _onlyDangerousPerms.asStateFlow()

    // File Explorer Filters
    private val _fileQuery = MutableStateFlow("")
    val fileQuery: StateFlow<String> = _fileQuery.asStateFlow()

    private val _fileCategoryFilter = MutableStateFlow<CategoryType?>(null)
    val fileCategoryFilter: StateFlow<CategoryType?> = _fileCategoryFilter.asStateFlow()

    fun setActiveTab(tab: ApkTab) {
        _activeTab.value = tab
    }

    fun setComponentQuery(query: String) {
        _componentQuery.value = query
    }

    fun setComponentTypeFilter(type: ComponentType?) {
        _componentTypeFilter.value = type
    }

    fun toggleOnlyExported() {
        _onlyExportedComponents.value = !_onlyExportedComponents.value
    }

    fun setPermissionQuery(query: String) {
        _permissionQuery.value = query
    }

    fun toggleOnlyDangerous() {
        _onlyDangerousPerms.value = !_onlyDangerousPerms.value
    }

    fun setFileQuery(query: String) {
        _fileQuery.value = query
    }

    fun setFileCategoryFilter(cat: CategoryType?) {
        _fileCategoryFilter.value = cat
    }

    fun setInstalledAppsSearch(query: String) {
        _installedAppsSearch.value = query
    }

    fun toggleFilterUserAppsOnly() {
        _filterUserAppsOnly.value = !_filterUserAppsOnly.value
    }

    fun openInstalledAppsSheet(context: Context) {
        _isInstalledAppsSheetVisible.value = true
        if (_installedApps.value.isEmpty()) {
            viewModelScope.launch {
                val apps = ApkParser.getInstalledApps(context)
                _installedApps.value = apps
            }
        }
    }

    fun closeInstalledAppsSheet() {
        _isInstalledAppsSheetVisible.value = false
    }

    fun mapCurrentApp(context: Context) {
        viewModelScope.launch {
            _uiState.value = ApkUiState.Loading("Mapping current application APK...")
            try {
                val apkPath = context.applicationInfo.sourceDir
                val file = File(apkPath)
                if (file.exists()) {
                    val mapData = ApkParser.parseApk(
                        context = context,
                        apkFile = file,
                        isInstalled = true,
                        installedPackageName = context.packageName
                    )
                    _uiState.value = ApkUiState.Loaded(mapData)
                } else {
                    _uiState.value = ApkUiState.Error("Current APK sourceDir not accessible: $apkPath")
                }
            } catch (e: Exception) {
                _uiState.value = ApkUiState.Error("Failed to map current app: ${e.localizedMessage}")
            }
        }
    }

    fun mapInstalledApp(context: Context, app: InstalledAppItem) {
        closeInstalledAppsSheet()
        viewModelScope.launch {
            _uiState.value = ApkUiState.Loading("Mapping ${app.appName}...")
            try {
                val file = File(app.apkPath)
                if (file.exists()) {
                    val mapData = ApkParser.parseApk(
                        context = context,
                        apkFile = file,
                        isInstalled = true,
                        installedPackageName = app.packageName
                    )
                    _uiState.value = ApkUiState.Loaded(mapData)
                } else {
                    _uiState.value = ApkUiState.Error("APK file not found at ${app.apkPath}")
                }
            } catch (e: Exception) {
                _uiState.value = ApkUiState.Error("Failed to parse ${app.appName}: ${e.localizedMessage}")
            }
        }
    }

    fun mapApkFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ApkUiState.Loading("Reading and staging selected APK...")
            try {
                val cacheFile = File(context.cacheDir, "selected_inspect_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not open input stream from selected URI")

                _uiState.value = ApkUiState.Loading("Extracting map structure...")
                val mapData = ApkParser.parseApk(
                    context = context,
                    apkFile = cacheFile,
                    isInstalled = false
                )
                _uiState.value = ApkUiState.Loaded(mapData)
            } catch (e: Exception) {
                _uiState.value = ApkUiState.Error("Failed to inspect picked APK: ${e.localizedMessage}")
            }
        }
    }

    fun generateReportText(data: ApkMapData): String {
        val s = data.summary
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine("           APK MAP REPORT               ")
        sb.appendLine("========================================")
        sb.appendLine("App Name:        ${s.appName}")
        sb.appendLine("Package Name:    ${s.packageName}")
        sb.appendLine("Version:         ${s.versionName} (${s.versionCode})")
        sb.appendLine("Target SDK:      ${s.targetSdk} (Min: ${s.minSdk}, Compile: ${s.compileSdk})")
        sb.appendLine("APK Size:        ${ApkParser.formatBytes(s.totalCompressedBytes)} (${s.totalFiles} files)")
        sb.appendLine("Debuggable:      ${s.isDebuggable}")
        sb.appendLine("Architectures:   ${if (s.architectures.isEmpty()) "None (Pure Java/Kotlin)" else s.architectures.joinToString(", ")}")
        sb.appendLine("Security Rating: ${data.security.securityLevel}")
        sb.appendLine()

        sb.appendLine("--- APK STORAGE MAP DISTRIBUTION ---")
        for (cat in data.categories) {
            sb.appendLine(
                String.format(
                    "• %-22s: %6.1f%% | %10s | %d files",
                    cat.label,
                    cat.percentage,
                    ApkParser.formatBytes(cat.compressedBytes),
                    cat.fileCount
                )
            )
        }
        sb.appendLine()

        sb.appendLine("--- COMPONENT MAP SUMMARY ---")
        val acts = data.components.filter { it.type == ComponentType.ACTIVITY }
        val srvs = data.components.filter { it.type == ComponentType.SERVICE }
        val recs = data.components.filter { it.type == ComponentType.RECEIVER }
        val prvs = data.components.filter { it.type == ComponentType.PROVIDER }
        val exportedCount = data.components.count { it.isExported }
        sb.appendLine("Activities:          ${acts.size} (${acts.count { it.isExported }} exported)")
        sb.appendLine("Services:            ${srvs.size} (${srvs.count { it.isExported }} exported)")
        sb.appendLine("Broadcast Receivers: ${recs.size} (${recs.count { it.isExported }} exported)")
        sb.appendLine("Content Providers:   ${prvs.size} (${prvs.count { it.isExported }} exported)")
        sb.appendLine("Total Exported:      $exportedCount")
        sb.appendLine()

        sb.appendLine("--- PERMISSION MAP (${data.permissions.size} total) ---")
        val dangerous = data.permissions.filter { it.isDangerous }
        sb.appendLine("Dangerous Permissions (${dangerous.size}):")
        dangerous.forEach {
            sb.appendLine("  [!] ${it.simpleName} (${it.category})")
        }
        val normal = data.permissions.filter { !it.isDangerous }
        sb.appendLine("Normal/Custom Permissions (${normal.size}):")
        normal.take(15).forEach {
            sb.appendLine("  [-] ${it.simpleName}")
        }
        if (normal.size > 15) {
            sb.appendLine("  ... and ${normal.size - 15} more")
        }
        sb.appendLine()

        sb.appendLine("--- TOP 10 LARGEST FILES IN APK ---")
        data.topLargestFiles.take(10).forEach { file ->
            sb.appendLine("  • ${file.path} (${ApkParser.formatBytes(file.compressedBytes)})")
        }
        sb.appendLine()

        sb.appendLine("--- SECURITY INSIGHTS ---")
        data.security.insights.forEach { insight ->
            sb.appendLine("  • $insight")
        }
        sb.appendLine("========================================")
        return sb.toString()
    }
}
