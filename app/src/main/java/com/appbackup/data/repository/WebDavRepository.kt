package com.appbackup.data.repository

import com.appbackup.data.model.AppInfo
import com.appbackup.data.pref.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class WebDavRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(
        url: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(url)
            val request = Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", null)
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Result.success("连接成功")
                } else {
                    val msg = when (resp.code) {
                        401, 403 -> "账号或密码错误"
                        404 -> "路径不存在"
                        405, 501 -> "服务器不支持 WebDAV"
                        in 500..599 -> "服务器错误 (${resp.code})"
                        else -> "连接失败 (${resp.code})"
                    }
                    Result.failure(IOException(msg))
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("无法解析服务器地址"))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("连接超时，请检查服务器地址"))
        } catch (e: SSLHandshakeException) {
            Result.failure(IOException("SSL 证书错误"))
        } catch (e: IOException) {
            Result.failure(IOException("网络错误：${e.message}"))
        }
    }

    suspend fun backupApps(
        apps: List<AppInfo>,
        config: PreferencesManager.WebDavConfig,
        includeApk: Boolean = true,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Map<AppInfo, String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<AppInfo, String>()
        val baseUrl = normalizeUrl(config.url)
        val credential = Credentials.basic(config.username, config.password)

        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val uniqueId = UUID.randomUUID().toString().replace("-", "").take(6)
        val sessionDir = "$baseUrl/APP备份/${datePart}_$uniqueId"

        ensureDirectory(sessionDir, credential)

        for ((index, app) in apps.withIndex()) {
            if (!isActive) break
            val progress = (index.toFloat() + 1) / apps.size
            onProgress(app.name, progress)
            val result = try {
                backupSingleApp(app, sessionDir, credential, includeApk)
                Result.success(app.name)
            } catch (e: Exception) {
                val errMsg = when (e) {
                    is IOException -> "网络错误：${e.message}"
                    else -> "未知错误：${e.message}"
                }
                Result.failure<String>(IOException(errMsg))
            }
            result.fold(
                onSuccess = { results[app] = "成功" },
                onFailure = { results[app] = it.message ?: "失败" }
            )
        }

        if (results.all { it.value == "成功" }) {
            Result.success(results)
        } else if (results.none { it.value == "成功" }) {
            Result.failure(IOException("所有应用备份均失败"))
        } else {
            Result.success(results)
        }
    }

    private fun backupSingleApp(
        app: AppInfo,
        sessionDir: String,
        credential: String,
        includeApk: Boolean
    ) {
        val sanitizedName = app.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val dirUrl = "$sessionDir/$sanitizedName"
        ensureDirectory(dirUrl, credential)

        var apkMd5 = ""
        if (includeApk) {
            val apkFile = File(app.apkPath)
            apkMd5 = MessageDigest.getInstance("MD5").let { md ->
                FileInputStream(apkFile).use { fis ->
                    DigestInputStream(fis, md).use { dis ->
                        val buf = ByteArray(8192)
                        while (dis.read(buf) != -1) { }
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            putFile("$dirUrl/$sanitizedName.apk", apkFile, credential)
        }

        val jsonObj = JSONObject().apply {
            put("name", app.name)
            put("packageName", app.packageName)
            put("versionName", app.versionName)
            put("versionCode", app.versionCode)
            put("backupTime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            put("apkMd5", apkMd5)
            put("hasApk", includeApk)
        }
        putFile("$dirUrl/$sanitizedName.json", jsonObj.toString(2).toByteArray(), credential)
    }

    private fun ensureDirectory(dirUrl: String, credential: String) {
        val request = Request.Builder()
            .url(dirUrl)
            .method("MKCOL", null)
            .header("Authorization", credential)
            .build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (resp.isSuccessful) return
            when (resp.code) {
                405 -> return // already exists as collection
                409 -> throw IOException("WebDAV 父目录不存在，请检查路径")
                401, 403 -> throw IOException("WebDAV 认证失败，无法创建目录")
                404 -> throw IOException("WebDAV 父目录不存在")
                else -> throw IOException("创建目录失败 (${resp.code})")
            }
        }
    }

    private fun putFile(url: String, file: File, credential: String) {
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", credential)
            .build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("上传失败 (${resp.code})")
            }
        }
    }

    private fun putFile(url: String, data: ByteArray, credential: String) {
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", credential)
            .build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("上传失败 (${resp.code})")
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }
}
