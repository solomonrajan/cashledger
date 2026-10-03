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
                                val destFileName = "cashledger-${release.tagName}.apk"
                                val destFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), destFileName)
                                
                                if (destFile.exists()) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.storage.file", destFile)
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(installIntent)
                                    dismiss()
                                    return@UpdaterScreen
                                }

                                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                val request = android.app.DownloadManager.Request(Uri.parse(release.downloadUrl))
                                    .setTitle("CashLedger Update")
                                    .setDescription("Downloading ${release.tagName}")
                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, destFileName)
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
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
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
    val newCount = release.body.count { it == '*' || it == '-' } / 2
    val fixesCount = release.body.count { it == '*' || it == '-' } - newCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        // Header
        Text("An update is available", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D1B1A), letterSpacing = (-0.5).sp)
        Text("CashLedger • ${release.tagName}", fontSize = 15.sp, color = Color(0xFF757575), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        // Summary Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EFEA)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE8DCD3)) {
                        Text(currentVersion, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5D4037))
                    }
                    Text("  →  ", fontWeight = FontWeight.Bold, color = Color(0xFF8D6E63))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF6B4226)) {
                        Text(release.tagName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("New updates available", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3E2723))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // New Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF6B4226)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("✨ New", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${newCount.coerceAtLeast(1)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    // Fixes Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFE0B2)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("🐛 Fixes", color = Color(0xFF6B4226), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${fixesCount.coerceAtLeast(1)}", color = Color(0xFF6B4226), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        // Timeline
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Timeline Graphics
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp).fillMaxHeight()
                    ) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(12.dp).background(Color(0xFF6B4226), RoundedCornerShape(50)))
                        Box(modifier = Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(Color(0xFFE8DCD3)))
                    }
                    
                    // Timeline Content
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(release.tagName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1D1B1A))
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B4226)) {
                                Text("Latest", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B4226)) {
                            Text("✨ Changes", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EFEA)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = release.body.takeIf { it.isNotBlank() } ?: "No release notes provided.",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFF4E342E),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        // Buttons
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onGithubClick("https://github.com/solomonrajan/cashledger/releases/tag/${release.tagName}") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1D1B1A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("View on GitHub", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onDownloadClick(release) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B4226), contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Download Update", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(modifier = Modifier.height(24.dp))
    }
}
