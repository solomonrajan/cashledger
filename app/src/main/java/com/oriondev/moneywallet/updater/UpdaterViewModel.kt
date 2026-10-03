@file:Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "MaxLineLength")
package com.oriondev.moneywallet.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.model.GithubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

sealed class UpdaterState {
    object Idle : UpdaterState()
    object Loading : UpdaterState()
    data class UpdateAvailable(val release: GithubRelease, val currentVersion: String, val allReleases: List<GithubRelease> = emptyList()) : UpdaterState()
    object UpToDate : UpdaterState()
    data class Error(val message: String) : UpdaterState()
}

class UpdaterViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val _updaterState = MutableStateFlow<UpdaterState>(UpdaterState.Idle)
    val updaterState: StateFlow<UpdaterState> = _updaterState

    fun checkForUpdates(currentVersion: String, isDevBuild: Boolean) {
        _updaterState.value = UpdaterState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/solomonrajan/cashledger/releases")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _updaterState.value = UpdaterState.Error("Failed to fetch releases: \${response.code}")
                    return@launch
                }

                val bodyStr = response.body?.string() ?: ""
                val releasesArray = JSONArray(bodyStr)
                
                var latestOfficial: GithubRelease? = null
                var latestPreRelease: GithubRelease? = null

                val parsedReleases = mutableListOf<GithubRelease>()
                for (i in 0 until releasesArray.length()) {
                    val releaseObj = releasesArray.getJSONObject(i)
                    val tagName = releaseObj.optString("tag_name", "")
                    val isPrerelease = releaseObj.optBoolean("prerelease", false)
                    
                    var downloadUrl: String? = null
                    val assets = releaseObj.optJSONArray("assets")
                    if (assets != null) {
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            if (asset.optString("name", "").endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    val release = GithubRelease(
                        name = releaseObj.optString("name", ""),
                        tagName = tagName,
                        body = releaseObj.optString("body", ""),
                        isPrerelease = isPrerelease,
                        publishedAt = releaseObj.optString("published_at", ""),
                        downloadUrl = downloadUrl
                    )

                    parsedReleases.add(release)

                    if (isPrerelease) {
                        if (latestPreRelease == null) latestPreRelease = release
                    } else {
                        if (latestOfficial == null) latestOfficial = release
                    }
                }

                val targetRelease = if (isDevBuild) {
                    latestPreRelease ?: latestOfficial
                } else {
                    latestOfficial
                }

                if (targetRelease != null) {
                    _updaterState.value = UpdaterState.UpdateAvailable(targetRelease, currentVersion, parsedReleases)
                } else {
                    _updaterState.value = UpdaterState.Error("Could not find a valid release or download link.")
                }
            } catch (e: IOException) {
                _updaterState.value = UpdaterState.Error(e.message ?: "Network error")
            } catch (e: Exception) {
                _updaterState.value = UpdaterState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
