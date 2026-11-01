package com.example.parser

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.ApkComponent
import com.example.model.ApkFileEntry
import com.example.model.ApkMapData
import com.example.model.ApkPermission
import com.example.model.ApkSummary
import com.example.model.CategoryBreakdown
import com.example.model.CategoryType
import com.example.model.ComponentType
import com.example.model.InstalledAppItem
import com.example.model.SecurityPosture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object ApkParser {

    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.CALL_PHONE",
        "android.permission.ANSWER_PHONE_CALLS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.ADD_VOICEMAIL",
        "android.permission.USE_SIP",
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.RECEIVE_MMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_MEDIA_LOCATION",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT"
    )

    suspend fun parseApk(
        context: Context,
        apkFile: File,
        isInstalled: Boolean = false,
        installedPackageName: String? = null
    ): ApkMapData = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        // 1. Resolve PackageInfo
        val packageInfo: PackageInfo? = try {
            if (isInstalled && installedPackageName != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        installedPackageName,
                        PackageManager.PackageInfoFlags.of(
                            (PackageManager.GET_ACTIVITIES or
                                PackageManager.GET_SERVICES or
                                PackageManager.GET_RECEIVERS or
                                PackageManager.GET_PROVIDERS or
                                PackageManager.GET_PERMISSIONS or
                                PackageManager.GET_CONFIGURATIONS).toLong()
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(
                        installedPackageName,
                        PackageManager.GET_ACTIVITIES or
                            PackageManager.GET_SERVICES or
                            PackageManager.GET_RECEIVERS or
                            PackageManager.GET_PROVIDERS or
                            PackageManager.GET_PERMISSIONS or
                            PackageManager.GET_CONFIGURATIONS
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(
                            (PackageManager.GET_ACTIVITIES or
                                PackageManager.GET_SERVICES or
                                PackageManager.GET_RECEIVERS or
                                PackageManager.GET_PROVIDERS or
                                PackageManager.GET_PERMISSIONS or
                                PackageManager.GET_CONFIGURATIONS).toLong()
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.GET_ACTIVITIES or
                            PackageManager.GET_SERVICES or
                            PackageManager.GET_RECEIVERS or
                            PackageManager.GET_PROVIDERS or
                            PackageManager.GET_PERMISSIONS or
                            PackageManager.GET_CONFIGURATIONS
                    )
                }
            }
        } catch (e: Exception) {
            null
        }

        val appInfo = packageInfo?.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val appName = try {
            appInfo?.loadLabel(pm)?.toString()
                ?.takeIf { it.isNotBlank() && !it.startsWith("com.") }
                ?: (packageInfo?.packageName ?: apkFile.nameWithoutExtension)
        } catch (_: Exception) {
            packageInfo?.packageName ?: apkFile.nameWithoutExtension
        }

        val packageName = packageInfo?.packageName ?: "Unknown"
        val versionName = packageInfo?.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 1L
        }
        val minSdk = appInfo?.minSdkVersion ?: 21
        val targetSdk = appInfo?.targetSdkVersion ?: 34
        val compileSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appInfo?.compileSdkVersion ?: targetSdk
        } else {
            targetSdk
        }
        val isDebuggable = appInfo?.let { (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 } ?: false

        // 2. Parse ZIP structure of the APK
        val fileEntries = mutableListOf<ApkFileEntry>()
        val architectures = mutableSetOf<String>()
        var totalCompressedBytes = 0L
        var totalUncompressedBytes = 0L
        var hasV1Signature = false

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val entryName = entry.name
                    val uncompressed = if (entry.size >= 0) entry.size else 0L
                    val compressed = if (entry.compressedSize >= 0) entry.compressedSize else uncompressed

                    totalUncompressedBytes += uncompressed
                    totalCompressedBytes += compressed

                    val category = classifyEntry(entryName)

                    // Architecture detection
                    if (entryName.startsWith("lib/")) {
                        val parts = entryName.split('/')
                        if (parts.size >= 2 && parts[1].isNotBlank()) {
                            architectures.add(parts[1])
                        }
                    }

                    if (entryName.startsWith("META-INF/") && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".DSA") || entryName.endsWith(".EC"))) {
                        hasV1Signature = true
                    }

                    fileEntries.add(
                        ApkFileEntry(
                            path = entryName,
                            name = entryName.substringAfterLast('/'),
                            category = category,
                            uncompressedBytes = uncompressed,
                            compressedBytes = compressed
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // In case of zip read error, record the file size as total
            totalCompressedBytes = apkFile.length()
            totalUncompressedBytes = apkFile.length()
        }

        // Top largest files
        val topLargest = fileEntries.sortedByDescending { it.compressedBytes }.take(15)

        // Category Breakdown
        val categories = CategoryType.values().map { catType ->
            val matching = fileEntries.filter { it.category == catType }
            val uncompressedSum = matching.sumOf { it.uncompressedBytes }
            val compressedSum = matching.sumOf { it.compressedBytes }
            val pct = if (totalCompressedBytes > 0) {
                (compressedSum.toFloat() / totalCompressedBytes.toFloat()) * 100f
            } else 0f

            CategoryBreakdown(
                type = catType,
                label = catType.displayName,
                fileCount = matching.size,
                totalBytes = uncompressedSum,
                compressedBytes = compressedSum,
                percentage = pct
            )
        }.sortedByDescending { it.compressedBytes }

        // 3. Components
        val components = mutableListOf<ApkComponent>()

        // Activities
        packageInfo?.activities?.forEach { act ->
            val simple = act.name.substringAfterLast('.')
            components.add(
                ApkComponent(
                    name = act.name,
                    simpleName = simple,
                    type = ComponentType.ACTIVITY,
                    isExported = act.exported,
                    permission = act.permission
                )
            )
        }

        // Services
        packageInfo?.services?.forEach { srv ->
            val simple = srv.name.substringAfterLast('.')
            components.add(
                ApkComponent(
                    name = srv.name,
                    simpleName = simple,
                    type = ComponentType.SERVICE,
                    isExported = srv.exported,
                    permission = srv.permission
                )
            )
        }

        // Receivers
        packageInfo?.receivers?.forEach { rec ->
            val simple = rec.name.substringAfterLast('.')
            components.add(
                ApkComponent(
                    name = rec.name,
                    simpleName = simple,
                    type = ComponentType.RECEIVER,
                    isExported = rec.exported,
                    permission = rec.permission
                )
            )
        }

        // Providers
        packageInfo?.providers?.forEach { prov ->
            val simple = prov.name.substringAfterLast('.')
            components.add(
                ApkComponent(
                    name = prov.name,
                    simpleName = simple,
                    type = ComponentType.PROVIDER,
                    isExported = prov.exported,
                    permission = prov.readPermission ?: prov.writePermission,
                    authorities = prov.authority
                )
            )
        }

        // 4. Permissions
        val permissions = mutableListOf<ApkPermission>()
        packageInfo?.requestedPermissions?.forEachIndexed { index, permName ->
            val simple = permName.substringAfterLast('.')
            val isDangerous = DANGEROUS_PERMISSIONS.contains(permName)
            val isCustom = !permName.startsWith("android.permission.")

            val category = classifyPermission(permName)
            val desc = describePermission(permName)

            permissions.add(
                ApkPermission(
                    name = permName,
                    simpleName = simple,
                    isDangerous = isDangerous,
                    isCustom = isCustom,
                    protectionLevel = if (isDangerous) "Dangerous" else if (isCustom) "Custom" else "Normal",
                    description = desc,
                    category = category
                )
            )
        }

        // 5. Security Posture
        val exportedWithoutPermission = components.count { it.isExported && it.permission.isNullOrBlank() }
        val totalExported = components.count { it.isExported }
        val dangerousPermsCount = permissions.count { it.isDangerous }

        val signingSchemes = mutableListOf<String>()
        if (hasV1Signature) signingSchemes.add("APK Signature Scheme v1 (JAR signing)")
        signingSchemes.add("APK Signature Scheme v2 / v3")

        val insights = mutableListOf<String>()
        if (isDebuggable) {
            insights.add("App is flagged as debuggable (android:debuggable=\"true\"). Should be disabled in production releases.")
        }
        if (exportedWithoutPermission > 0) {
            insights.add("$exportedWithoutPermission component(s) are exported without requiring specific access permissions. Other apps on the device can launch them.")
        } else {
            insights.add("All exported components specify guarding permissions or none are exposed unconditionally.")
        }
        if (targetSdk < 33) {
            insights.add("Target SDK is $targetSdk. Google Play requires targeting modern Android releases (Android 14+ / SDK 34+).")
        } else {
            insights.add("Targets modern Android SDK $targetSdk.")
        }
        if (architectures.isNotEmpty()) {
            insights.add("Bundles native binaries for: ${architectures.sorted().joinToString(", ")}.")
        } else {
            insights.add("Pure DEX bytecode package (no native .so libraries). Highly portable.")
        }

        val securityLevel = when {
            isDebuggable -> "Review Needed"
            exportedWithoutPermission > 5 -> "Notice"
            else -> "Healthy"
        }

        val security = SecurityPosture(
            isDebuggable = isDebuggable,
            exportedWithoutPermissionCount = exportedWithoutPermission,
            totalExportedCount = totalExported,
            dangerousPermissionsCount = dangerousPermsCount,
            targetSdkVersion = targetSdk,
            minSdkVersion = minSdk,
            hasNativeCode = architectures.isNotEmpty(),
            architectures = architectures.sorted(),
            signingSchemes = signingSchemes,
            securityLevel = securityLevel,
            insights = insights
        )

        val summary = ApkSummary(
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            totalUncompressedBytes = totalUncompressedBytes,
            totalCompressedBytes = if (totalCompressedBytes > 0) totalCompressedBytes else apkFile.length(),
            totalFiles = fileEntries.size,
            isDebuggable = isDebuggable,
            sourcePath = apkFile.absolutePath,
            isInstalledApp = isInstalled,
            architectures = architectures.sorted()
        )

        ApkMapData(
            summary = summary,
            categories = categories,
            components = components,
            permissions = permissions.sortedByDescending { it.isDangerous },
            files = fileEntries,
            topLargestFiles = topLargest,
            security = security
        )
    }

    private fun classifyEntry(name: String): CategoryType {
        return when {
            name.endsWith(".dex") -> CategoryType.DEX
            name.startsWith("lib/") -> CategoryType.NATIVE_LIB
            name.startsWith("res/") || name == "resources.arsc" -> CategoryType.RESOURCES
            name.startsWith("assets/") -> CategoryType.ASSETS
            name.startsWith("META-INF/") || name == "AndroidManifest.xml" -> CategoryType.META_SIGNATURES
            else -> CategoryType.OTHER
        }
    }

    private fun classifyPermission(name: String): String {
        return when {
            name.contains("LOCATION") -> "Location"
            name.contains("CAMERA") -> "Camera"
            name.contains("AUDIO") || name.contains("RECORD") -> "Audio & Mic"
            name.contains("STORAGE") || name.contains("MEDIA") -> "Storage"
            name.contains("CONTACT") || name.contains("CALENDAR") -> "Personal Data"
            name.contains("PHONE") || name.contains("CALL") || name.contains("SMS") -> "Telephony"
            name.contains("BLUETOOTH") || name.contains("WIFI") || name.contains("NEARBY") -> "Connectivity"
            name.contains("INTERNET") || name.contains("NETWORK") -> "Network"
            name.contains("NOTIFICATION") -> "Notifications"
            else -> "System / Other"
        }
    }

    private fun describePermission(name: String): String {
        return when (name) {
            "android.permission.INTERNET" -> "Allows access to the Internet"
            "android.permission.ACCESS_NETWORK_STATE" -> "View network connection status"
            "android.permission.ACCESS_FINE_LOCATION" -> "Access precise device GPS coordinates"
            "android.permission.ACCESS_COARSE_LOCATION" -> "Access approximate network-based location"
            "android.permission.CAMERA" -> "Capture photos and videos via device cameras"
            "android.permission.RECORD_AUDIO" -> "Record sound and microphone streams"
            "android.permission.POST_NOTIFICATIONS" -> "Display notifications in the system shade"
            "android.permission.VIBRATE" -> "Control device haptic feedback and vibrator"
            "android.permission.WAKE_LOCK" -> "Prevent device processor from sleeping"
            "android.permission.RECEIVE_BOOT_COMPLETED" -> "Run background startup triggers on boot"
            "android.permission.READ_EXTERNAL_STORAGE" -> "Read files from shared external storage"
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "Modify files on shared external storage"
            else -> "Standard Android platform or application permission"
        }
    }

    suspend fun getInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedList = mutableListOf<InstalledAppItem>()

        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val sourceDir = appInfo.sourceDir ?: continue
                val file = File(sourceDir)
                if (!file.exists()) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }

                installedList.add(
                    InstalledAppItem(
                        appName = appName,
                        packageName = pkg.packageName,
                        versionName = pkg.versionName ?: "1.0",
                        apkPath = sourceDir,
                        apkSizeBytes = file.length(),
                        isSystemApp = isSystem
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback: list current app
            val currentPkg = context.packageName
            val currentSource = context.applicationInfo.sourceDir
            val file = File(currentSource)
            installedList.add(
                InstalledAppItem(
                    appName = "APK Mapper",
                    packageName = currentPkg,
                    versionName = "1.0",
                    apkPath = currentSource,
                    apkSizeBytes = file.length(),
                    isSystemApp = false
                )
            )
        }

        installedList.sortedWith(
            compareBy<InstalledAppItem> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
