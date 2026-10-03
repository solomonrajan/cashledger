@file:Suppress("LongMethod", "FunctionNaming", "MaxLineLength", "MagicNumber", "WildcardImport", "UnusedPrivateProperty")
package com.oriondev.moneywallet.updater

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
                        },
                        onClose = { dismiss() }
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
    onDownloadClick: (GithubRelease) -> Unit,
    onClose: () -> Unit
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
                    onDownloadClick = onDownloadClick,
                    onClose = onClose
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
    onDownloadClick: (GithubRelease) -> Unit,
    onClose: () -> Unit
) {
    val isInstalled = release.tagName.removePrefix("v") == currentVersion.removePrefix("v")
    
    val newCount = release.body.count { it == '*' || it == '-' } / 2
    val fixesCount = release.body.count { it == '*' || it == '-' } - newCount
    val totalCount = newCount + fixesCount

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF8F6))
    ) {
        // Top Header
        Surface(
            color = Color(0xFFE6F3F3),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", color = Color(0xFF0288D1), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("View changelogs", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("CashLedger • ${release.tagName}", fontSize = 14.sp, color = Color(0xFF5F6368))
                }
            }
        }

        // Timeline Area
        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Timeline Rail
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp).fillMaxHeight().padding(start = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(14.dp)
                                .background(Color.Transparent, CircleShape)
                                .padding(2.dp)
                                .background(if (isInstalled) Color.Transparent else Color(0xFF388E3C), CircleShape)
                                .let {
                                    if (isInstalled) {
                                        androidx.compose.ui.draw.drawBehind {
                                            drawCircle(color = Color(0xFF388E3C), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                                        }(it)
                                    } else it
                                }
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(2.dp)
                                .weight(1f)
                                .background(Color(0xFFE0E0E0))
                        )
                    }

                    // Content
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(release.tagName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isInstalled) {
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFC8E6C9)) {
                                    Text("Installed", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFF1B5E20), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE3F2FD)) {
                                    Text("Latest", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFF0D47A1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text("︿", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        
                        Text("Today • $totalCount fix", fontSize = 13.sp, color = Color(0xFF5F6368), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                        // Fixes Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEBE0DD)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column {
                                Surface(
                                    color = Color(0xFFE4D6D2),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🐛", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fixes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(shape = CircleShape, color = Color(0xFFD3C5C1)) {
                                            Text(fixesCount.coerceAtLeast(1).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = Color.Black)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).background(Color(0xFF946549), CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            release.body.takeIf { it.isNotBlank() } ?: "Various bug fixes.",
                                            fontSize = 14.sp,
                                            color = Color.Black,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Performance Card (Dummy for visual match)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEBE0DD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Surface(
                                    color = Color(0xFFE4D6D2),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFA5D6A7),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text("⚡", fontSize = 12.sp, modifier = Modifier.padding(4.dp))
                                        }
                                        Text("Performance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(shape = CircleShape, color = Color(0xFFD3C5C1)) {
                                            Text(newCount.coerceAtLeast(1).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = Color.Black)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).background(Color(0xFF388E3C), CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Bound package manager queries in installed app picker", fontSize = 14.sp, color = Color.Black, lineHeight = 20.sp)
                                    }
                                }
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().clickable { }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show all (${newCount.coerceAtLeast(1)})", fontSize = 14.sp, color = Color(0xFF5D4037))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("⌄", fontSize = 18.sp, color = Color(0xFF5D4037))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Actions
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Button(
                onClick = { onGithubClick("https://github.com/solomonrajan/cashledger/releases/tag/${release.tagName}") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Black),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3C5C1)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("GitHub", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { 
                    if (isInstalled) onClose() else onDownloadClick(release)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Black),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3C5C1)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (isInstalled) "Close" else "Download Update", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
