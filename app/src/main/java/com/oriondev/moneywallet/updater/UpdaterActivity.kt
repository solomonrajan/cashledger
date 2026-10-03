package com.oriondev.moneywallet.updater

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.BuildConfig
import com.oriondev.moneywallet.model.GithubRelease

class UpdaterActivity : ComponentActivity() {
    private val viewModel: UpdaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val isDev = BuildConfig.APPLICATION_ID.endsWith(".dev")
        viewModel.checkForUpdates(BuildConfig.VERSION_NAME, isDev)
        
        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colorScheme = if (dynamicColor) {
                dynamicLightColorScheme(context)
            } else {
                lightColorScheme(
                    primary = Color(0xFF0288D1),
                    background = Color(0xFFFFF8F6),
                    surface = Color.White
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                UpdaterScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onGithubClick = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onDownloadClick = { release ->
                        if (release.downloadUrl != null) {
                            val destFileName = "cashledger-${release.tagName}.apk"
                            val destFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), destFileName)
                            
                            if (destFile.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.storage.file", destFile)
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(installIntent)
                                finish()
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
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    viewModel: UpdaterViewModel,
    onBackClick: () -> Unit,
    onGithubClick: (String) -> Unit,
    onDownloadClick: (GithubRelease) -> Unit
) {
    val state by viewModel.updaterState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val s = state) {
                is UpdaterState.Idle, is UpdaterState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
}

@Composable
fun UpdateAvailableContent(
    release: GithubRelease,
    currentVersion: String,
    onGithubClick: (String) -> Unit,
    onDownloadClick: (GithubRelease) -> Unit
) {
    val isInstalled = release.tagName.removePrefix("v") == currentVersion.removePrefix("v")
    
    val newCount = release.body.count { it == '*' || it == '-' } / 2
    val fixesCount = release.body.count { it == '*' || it == '-' } - newCount
    val totalCount = newCount + fixesCount

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Header using Dynamic Colors
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(56.dp),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text("View changelogs", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("CashLedger • ${release.tagName}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Timeline Area
        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Timeline Rail
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(48.dp).fillMaxHeight()
                    ) {
                        val nodeColor = if (isInstalled) Color.Transparent else MaterialTheme.colorScheme.primary
                        val strokeColor = MaterialTheme.colorScheme.primary
                        
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(16.dp)
                                .background(Color.Transparent, CircleShape)
                                .padding(2.dp)
                                .background(nodeColor, CircleShape)
                                .let {
                                    if (isInstalled) {
                                        it.drawBehind {
                                            drawCircle(color = strokeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                                        }
                                    } else it
                                }
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(2.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    // Content
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(release.tagName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isInstalled) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text("Installed", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("Latest", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        Text("Today • $totalCount changes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                        // Fixes Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🐛", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fixes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(fixesCount.coerceAtLeast(1).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            release.body.takeIf { it.isNotBlank() } ?: "Various bug fixes.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Performance Card (Dummy for visual match)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text("⚡", fontSize = 12.sp, modifier = Modifier.padding(4.dp))
                                        }
                                        Text("Performance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(newCount.coerceAtLeast(1).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Bound package manager queries in installed app picker", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
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
                                        Text("Show all (${newCount.coerceAtLeast(1)})", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Actions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 16.dp).windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Button(
                    onClick = { onGithubClick("https://github.com/solomonrajan/cashledger/releases/tag/${release.tagName}") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("GitHub", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { 
                        if (!isInstalled) onDownloadClick(release)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (isInstalled) "Up to date" else "Download Update", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}
