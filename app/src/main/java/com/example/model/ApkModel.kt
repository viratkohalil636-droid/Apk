package com.example.model

enum class CategoryType(val displayName: String) {
    DEX("Code (DEX)"),
    NATIVE_LIB("Native Libraries"),
    RESOURCES("Resources & ARSC"),
    ASSETS("Assets"),
    META_SIGNATURES("Manifest & Signatures"),
    OTHER("Miscellaneous")
}

data class CategoryBreakdown(
    val type: CategoryType,
    val label: String,
    val fileCount: Int,
    val totalBytes: Long,
    val compressedBytes: Long,
    val percentage: Float
)

data class ApkFileEntry(
    val path: String,
    val name: String,
    val category: CategoryType,
    val uncompressedBytes: Long,
    val compressedBytes: Long
)

enum class ComponentType(val displayName: String) {
    ACTIVITY("Activity"),
    SERVICE("Service"),
    RECEIVER("Broadcast Receiver"),
    PROVIDER("Content Provider")
}

data class ApkComponent(
    val name: String,
    val simpleName: String,
    val type: ComponentType,
    val isExported: Boolean,
    val permission: String? = null,
    val intentActions: List<String> = emptyList(),
    val authorities: String? = null
)

data class ApkPermission(
    val name: String,
    val simpleName: String,
    val isDangerous: Boolean,
    val isCustom: Boolean,
    val protectionLevel: String,
    val description: String,
    val category: String
)

data class SecurityPosture(
    val isDebuggable: Boolean,
    val exportedWithoutPermissionCount: Int,
    val totalExportedCount: Int,
    val dangerousPermissionsCount: Int,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val hasNativeCode: Boolean,
    val architectures: List<String>,
    val signingSchemes: List<String>,
    val securityLevel: String, // "Good", "Review Needed", "Warning"
    val insights: List<String>
)

data class ApkSummary(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
    val totalUncompressedBytes: Long,
    val totalCompressedBytes: Long,
    val totalFiles: Int,
    val isDebuggable: Boolean,
    val sourcePath: String,
    val isInstalledApp: Boolean,
    val architectures: List<String>
)

data class ApkMapData(
    val summary: ApkSummary,
    val categories: List<CategoryBreakdown>,
    val components: List<ApkComponent>,
    val permissions: List<ApkPermission>,
    val files: List<ApkFileEntry>,
    val topLargestFiles: List<ApkFileEntry>,
    val security: SecurityPosture
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean
)
