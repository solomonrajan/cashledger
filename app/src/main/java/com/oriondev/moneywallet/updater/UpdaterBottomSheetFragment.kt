@file:Suppress("LongMethod", "FunctionNaming", "MaxLineLength", "MagicNumber", "WildcardImport", "UnusedPrivateProperty")
package com.oriondev.moneywallet.updater

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.oriondev.moneywallet.BuildConfig
import com.oriondev.moneywallet.model.GithubRelease

class UpdaterBottomSheetFragment : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "UpdaterBottomSheetFragment"
    }

    private val viewModel: UpdaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDev = BuildConfig.APPLICATION_ID.endsWith(".dev")
        viewModel.checkForUpdates(BuildConfig.VERSION_NAME, isDev)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        surface = Color(0xFFFAF6F3),
                        onSurface = Color(0xFF1D1B1A)
                    )
                ) {
                    UpdaterScreen(
                        viewModel = viewModel,
                        onGithubClick = { url ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        onDownloadClick = { release ->
                            if (release.downloadUrl != null) {
                                val context = requireContext()
                                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                val request = android.app.DownloadManager.Request(Uri.parse(release.downloadUrl))
                                    .setTitle("CashLedger Update")
                                    .setDescription("Downloading \${release.tagName}")
                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "cashledger-\${release.tagName}.apk")
                                    .setMimeType("application/vnd.android.package-archive")
                                
                                val downloadId = downloadManager.enqueue(request)
                                
                                // Register receiver for when download completes
                                val onComplete = object : android.content.BroadcastReceiver() {
                                    override fun onReceive(ctxt: android.content.Context, intent: Intent) {
                                        val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                        if (id == downloadId) {
                                            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                                            if (uri != null) {
                                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                }
                                                ctxt.startActivity(installIntent)
                                            }
                                            ctxt.unregisterReceiver(this)
                                        }
                                    }
                                }
                                androidx.core.content.ContextCompat.registerReceiver(
                                    context,
                                    onComplete,
                                    android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                                )
                                android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UpdaterScreen(
    viewModel: UpdaterViewModel,
    onGithubClick: (String) -> Unit,
    onDownloadClick: (GithubRelease) -> Unit
) {
    val state by viewModel.updaterState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
        color = MaterialTheme.colorScheme.surface
    ) {
        when (val s = state) {
            is UpdaterState.Idle, is UpdaterState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6D4C41))
                }
            }
            is UpdaterState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Error: \${s.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is UpdaterState.UpToDate -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("You are on the latest version!", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
            is UpdaterState.UpdateAvailable -> {
                UpdateAvailableContent(
                    release = s.release,
                    currentVersion = s.currentVersion,
                    onGithubClick = onGithubClick,
                    onDownloadClick = onDownloadClick
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableContent(
    release: GithubRelease,
    currentVersion: String,
    onGithubClick: (String) -> Unit,
    onDownloadClick: (GithubRelease) -> Unit
) {
    // Parse dummy stats from release body for UI accuracy to the design
    val newCount = release.body.count { it == '*' || it == '-' } / 2
    val fixesCount = release.body.count { it == '*' || it == '-' } - newCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        // Header
        Text("An update is available", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("CashLedger • \${release.tagName}", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))

        // Summary Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E6DF)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE6D6CA)) {
                        Text(currentVersion, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp)
                    }
                    Text("  →  ", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF6B4226)) {
                        Text(release.tagName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("New updates available", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // New Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF6B4226)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("✨ New", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("\${newCount.coerceAtLeast(1)}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Fixes Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFE0B2)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🐛 Fixes", color = Color(0xFF6B4226), fontWeight = FontWeight.Medium)
                            Text("\${fixesCount.coerceAtLeast(1)}", color = Color(0xFF6B4226), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Timeline
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(Color(0xFF6B4226), RoundedCornerShape(6.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(release.tagName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF6B4226)) {
                        Text("Latest", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp)
                    }
                }
                Row {
                    // Timeline Line
                    Box(modifier = Modifier.padding(start = 5.dp).width(2.dp).height(200.dp).background(Color(0xFFE6D6CA)))
                    
                    Column(modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp)) {
                        // Dummy timeline content based on body
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF6B4226), modifier = Modifier.padding(bottom = 12.dp)) {
                            Text("✨ Changes", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E6DF)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = release.body.takeIf { it.isNotBlank() } ?: "No release notes provided.",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFF3E2723),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Buttons
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onGithubClick("https://github.com/solomonrajan/cashledger/releases/tag/\${release.tagName}") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("GitHub", fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onDownloadClick(release) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7CCC8), contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp)
        ) {
            Text("Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
