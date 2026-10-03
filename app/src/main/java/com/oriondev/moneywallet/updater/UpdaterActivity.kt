@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter")
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
                title = { Text("About") },
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state is UpdaterState.UpdateAvailable) {
                val s = state as UpdaterState.UpdateAvailable
                val isInstalled = s.release.tagName.removePrefix("v") == s.currentVersion.removePrefix("v")
                Surface(
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        Button(
                            onClick = { onGithubClick("https://github.com/solomonrajan/cashledger/releases/tag/${s.release.tagName}") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("GitHub", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { 
                                if (!isInstalled) onDownloadClick(s.release)
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
                        allReleases = s.allReleases
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableContent(
    release: GithubRelease,
    currentVersion: String,
    allReleases: List<GithubRelease>
) {
    var showChangelog by remember { mutableStateOf(false) }

    if (showChangelog) {
        ModalBottomSheet(
            onDismissRequest = { showChangelog = false },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp).padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Changelogs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("CashLedger", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp).clickable { showChangelog = false }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allReleases.size) { index ->
                        val item = allReleases[index]
                        val isLatest = index == 0
                        
                        val newCount = item.body.count { it == '*' || it == '-' } / 2
                        val fixesCount = item.body.count { it == '*' || it == '-' } - newCount
                        
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            // Rail
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(32.dp).fillMaxHeight()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(14.dp)
                                        .background(if (isLatest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                )
                                if (index < allReleases.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .width(2.dp)
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            // Content
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 32.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.tagName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isLatest) {
                                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Text("Latest", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                val displayDate = if (isLatest) "Today • " + (item.publishedAt.take(10)) else item.publishedAt.take(10)
                                Text(displayDate, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))

                                // Fixes Card
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), modifier = Modifier.size(32.dp)) {
                                                Box(contentAlignment = Alignment.Center) { Text("🐛", fontSize = 16.sp) }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Fixes (${fixesCount.coerceAtLeast(1)})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("︿", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(modifier = Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(item.body.takeIf { it.isNotBlank() } ?: "Various bug fixes.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                                        }
                                    }
                                }

                                // Performance Card
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(32.dp)) {
                                                Box(contentAlignment = Alignment.Center) { Text("⚡", fontSize = 16.sp) }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Performance (${newCount.coerceAtLeast(1)})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("︿", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(modifier = Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Bound package manager queries in installed app picker", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val newCount = release.body.count { it == '*' || it == '-' } / 2
    val fixesCount = release.body.count { it == '*' || it == '-' } - newCount
    val totalCount = newCount + fixesCount

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp)
    ) {
        item {
            // App Info Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.oriondev.moneywallet.R.mipmap.ic_launcher),
                                contentDescription = "App Logo",
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("APP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("CashLedger", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(release.tagName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // View Changelogs Button
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clickable { showChangelog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📄", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("View changelogs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("See what's new in this version", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(">", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }

            Text("Today • $totalCount changes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))

            // Fixes Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🐛", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fixes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("${fixesCount.coerceAtLeast(1)} changes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(fixesCount.coerceAtLeast(1).toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("︿", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
                            Box(modifier = Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                release.body.takeIf { it.isNotBlank() } ?: "Various bug fixes.",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Performance Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Performance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("${newCount.coerceAtLeast(1)} changes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(newCount.coerceAtLeast(1).toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("︿", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Bound package manager queries in installed app picker", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
    }
}
