package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ApkDownloadState

    data class ReadyToInstall(val file: File) : ApkDownloadState
    data class Error(val message: String) : ApkDownloadState
}

sealed interface ApkInstallLaunchResult {
    data object LaunchedInstaller : ApkInstallLaunchResult
    data object NeedsInstallPermission : ApkInstallLaunchResult
    data class Failed(val message: String) : ApkInstallLaunchResult
}

object ApkUpdateDownloader {

    fun downloadApk(context: Context, rawUrl: String): Flow<ApkDownloadState> = flow {
        val cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true) &&
            !cleanUrl.startsWith("http://", ignoreCase = true)
        ) {
            emit(ApkDownloadState.Error("رابط ملف التحديث (apk_url) غير صالح."))
            return@flow
        }

        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val tempFile = File(updatesDir, "rafiq-update.apk.part")
        val targetFile = File(updatesDir, "rafiq-update.apk")

        var connection: HttpURLConnection? = null
        try {
            emit(ApkDownloadState.Downloading(0f, 0L, -1L))

            var currentUrl = cleanUrl
            var redirectCount = 0
            while (true) {
                connection?.disconnect()
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "RafiqTeacherAndroidUpdater/1.0")
                }
                connection = conn
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank() || redirectCount >= 5) {
                        emit(ApkDownloadState.Error("تعذر متابعة رابط التحميل (تحويلات كثيرة)."))
                        return@flow
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirectCount++
                    continue
                }
                if (code !in 200..299) {
                    emit(ApkDownloadState.Error("فشل تحميل التحديث من الخادم (HTTP $code)."))
                    return@flow
                }
                break
            }

            val finalConn = connection
            val totalBytes = finalConn.contentLengthLong
            if (tempFile.exists()) tempFile.delete()

            finalConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastEmitTime = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 120L) {
                            lastEmitTime = now
                            val progress = if (totalBytes > 0L) {
                                (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            emit(ApkDownloadState.Downloading(progress, downloaded, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.exists() || tempFile.length() < 1024L) {
                emit(ApkDownloadState.Error("الملف المحمّل غير مكتمل أو فارغ."))
                return@flow
            }

            // Verify APK ZIP magic header ('PK')
            val isZipApk = tempFile.inputStream().use {
                val b0 = it.read()
                val b1 = it.read()
                b0 == 0x50 && b1 == 0x4B
            }
            if (!isZipApk) {
                tempFile.delete()
                emit(ApkDownloadState.Error("الرابط لا يشير إلى ملف APK صالح (قد يكون صفحة ويب)."))
                return@flow
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            emit(ApkDownloadState.Downloading(1f, targetFile.length(), targetFile.length()))
            emit(ApkDownloadState.ReadyToInstall(targetFile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ApkDownloadState.Error(e.localizedMessage ?: "حدث خطأ أثناء تنزيل التحديث."))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun launchApkInstaller(context: Context, apkFile: File): ApkInstallLaunchResult {
        if (!apkFile.exists()) {
            return ApkInstallLaunchResult.Failed("ملف التحديث غير موجود.")
        }
        if (!canRequestPackageInstalls(context)) {
            return ApkInstallLaunchResult.NeedsInstallPermission
        }
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            ApkInstallLaunchResult.LaunchedInstaller
        } catch (e: Exception) {
            ApkInstallLaunchResult.Failed(e.localizedMessage ?: "تعذر فتح مثبّت التطبيقات.")
        }
    }
}
