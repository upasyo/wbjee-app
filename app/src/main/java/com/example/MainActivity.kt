package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Notice
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoticeViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: NoticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF3F6E9) // Match Bento Background perfectly
                ) { innerPadding ->
                    NoticeDashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeDashboardScreen(
    viewModel: NoticeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val lastChecked by viewModel.lastChecked.collectAsStateWithLifecycle()
    val isBackgroundEnabled by viewModel.isBackgroundEnabled.collectAsStateWithLifecycle()
    val enabledCategories by viewModel.enabledCategories.collectAsStateWithLifecycle()
    val checkIntervalHours by viewModel.checkIntervalHours.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val sortedNotices = remember(notices) {
        notices.sortedWith(
            compareByDescending<Notice> { it.isNew }
                .thenByDescending { it.scannedAt }
        )
    }

    // Permission tracking state for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled! You'll receive instant alerts.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Alert notifications are disabled. You might miss important updates.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission request on start for Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Showing Toast if any scraper errors occur
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Continuous rotation transition for scanning state icon
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6E9)) // Exact Bento Background color
    ) {
        // --- BENTO HEADER BLOCK ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WBJEE Tracker",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A1C18),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Last scan: ${formatTimestamp(lastChecked)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF43483E)
                )
            }
            // Rounded bento status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD7E8CD))
                    .clickable { viewModel.triggerManualScan() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan status",
                    tint = Color(0xFF386A20),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(if (isScanning) rotationAngle else 0f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permission Banner (Rendered as a beautiful callout grid block)
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF9EBE0)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color(0xFFEDD9C9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerts Disabled",
                                tint = Color(0xFF8B4513),
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alerts Disabled",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B4513),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Enable notifications to play urgent alarms for releases.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8B4513).copy(alpha = 0.8f)
                                )
                            }
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B4513)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Enable", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Hero banner image card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFC9B4))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_wbjee_banner),
                            contentDescription = "WBJEE Board Notice Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )
                        Text(
                            text = "West Bengal Joint Entrance Examinations Board",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp)
                        )
                    }
                }
            }

            // --- BENTO GRID: MAIN MONITORING CELL ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD7E8CD)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFC9B4)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF386A20))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE MONITORING",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Active scanning info",
                                tint = Color(0xFF386A20),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "wbjeeb.nic.in/wbjee",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF111F0E),
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "Scanning the official board portal every 60 minutes.",
                                fontSize = 14.sp,
                                color = Color(0xFF386A20),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- BENTO GRID: TWO-COLUMN SMALL CELLS ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Small Cell 1: Next Scan Timer status (Lavender)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8DEF8)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color(0xFFCDC2DB)),
                        modifier = Modifier
                            .weight(1f)
                            .height(115.dp)
                            .clickable { viewModel.toggleBackgroundScan(!isBackgroundEnabled) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Auto-Scan Status",
                                tint = Color(0xFF21005D),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(bottom = 4.dp)
                            )
                            Text(
                                text = "AUTO-SCAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isBackgroundEnabled) "HOURLY" else "PAUSED",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF21005D)
                            )
                        }
                    }

                    // Small Cell 2: Alarms (Peach)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF9EBE0)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color(0xFFEDD9C9)),
                        modifier = Modifier
                            .weight(1f)
                            .height(115.dp)
                            .clickable { viewModel.triggerTestAlert() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alarm status",
                                tint = Color(0xFF8B4513),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(bottom = 4.dp)
                            )
                            Text(
                                text = "ALARMS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B4513),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (hasNotificationPermission) "ACTIVE" else "MUTED",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF8B4513)
                            )
                        }
                    }
                }
            }

            // --- BENTO GRID: FORCE MANUAL SCAN BAR ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1C18)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isScanning) { viewModel.triggerManualScan() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF386A20)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Force scan icon",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (isScanning) rotationAngle else 0f)
                                )
                            }
                            Text(
                                text = if (isScanning) "Scanning board portal..." else "Force manual scan",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Trigger action",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- BENTO GRID: NOTIFICATION PREFERENCES ---
            item {
                NotificationPreferencesCard(
                    enabledCategories = enabledCategories,
                    checkIntervalHours = checkIntervalHours,
                    onCategoryToggled = { category ->
                        val newCategories = if (category in enabledCategories) {
                            if (enabledCategories.size > 1) {
                                enabledCategories - category
                            } else {
                                enabledCategories
                            }
                        } else {
                            enabledCategories + category
                        }
                        viewModel.updateCategories(newCategories)
                    },
                    onIntervalChanged = { hours ->
                        viewModel.updateCheckInterval(hours)
                    },
                    onTriggerTestAlarm = {
                        viewModel.triggerTestAlert()
                    }
                )
            }
            
            // --- BENTO GRID: HISTORY LOG ---
            if (notices.isNotEmpty()) {
                item {
                    HistoryLogCard(notices = notices)
                }
            }

            // --- SECTION HEADER: ANNOUNCEMENTS BOARD ---
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Announcements Board",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1A1C18)
                        )
                        val unreadCount = notices.count { it.isNew }
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFF5252))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$unreadCount NEW",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    if (notices.any { it.isNew }) {
                        TextButton(
                            onClick = { viewModel.markAllAsRead() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Acknowledge All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF386A20)
                            )
                        }
                    }
                }
            }

            // --- EMPTY STATE / NOTICES LIST ---
            if (notices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE1E3D5)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "No updates cached",
                                tint = Color(0xFF74796D).copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No notices cached yet",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1C18),
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap 'Force manual scan' to run the initial web scrape. We'll populate existing releases and monitor for any incoming board updates.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF43483E),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            } else {
                items(sortedNotices, key = { it.id }) { notice ->
                    BentoNoticeItemCard(
                        notice = notice,
                        onOpenUrl = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(notice.url))
                            context.startActivity(intent)
                        },
                        onMarkAsRead = { viewModel.markAsRead(notice.id) }
                    )
                }
                
                // Add an option to purge cache at the bottom
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF8B4513)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEDD9C9)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Purge logs", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear History & Cache", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryLogCard(notices: List<Notice>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E3D5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Recent History",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF1A1C18)
            )
            
            notices.take(5).forEach { notice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notice.title,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF43483E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notice.date,
                        fontSize = 11.sp,
                        color = Color(0xFF74796D),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPreferencesCard(
    enabledCategories: Set<String>,
    checkIntervalHours: Int,
    onCategoryToggled: (String) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onTriggerTestAlarm: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E3D5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Preferences",
                    tint = Color(0xFF386A20),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Notification Settings",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFF1A1C18)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ALERT ME FOR CATEGORIES:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF74796D),
                    letterSpacing = 0.5.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("Counselling", "Schedule", "Notice")
                    categories.forEach { category ->
                        val isSelected = category in enabledCategories
                        val chipBg = if (isSelected) Color(0xFFD7E8CD) else Color(0xFFF3F6E9)
                        val chipBorder = if (isSelected) Color(0xFF386A20) else Color(0xFFE1E3D5)
                        val chipText = if (isSelected) Color(0xFF111F0E) else Color(0xFF43483E)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
                                .clickable { onCategoryToggled(category) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = chipText
                            )
                        }
                    }
                }
            }

            // Custom divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE1E3D5))
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CHECKING FREQUENCY:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF74796D),
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(
                        Pair(1, "Hourly"),
                        Pair(12, "12 Hours"),
                        Pair(24, "Daily")
                    )
                    intervals.forEach { (hours, label) ->
                        val isSelected = checkIntervalHours == hours
                        val bg = if (isSelected) Color(0xFFD7E8CD) else Color(0xFFF3F6E9)
                        val border = if (isSelected) Color(0xFF386A20) else Color(0xFFE1E3D5)
                        val textColor = if (isSelected) Color(0xFF111F0E) else Color(0xFF43483E)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(16.dp))
                                .clickable { onIntervalChanged(hours) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Custom divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE1E3D5))
            )

            // Test Alarm Button
            Button(
                onClick = onTriggerTestAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Test Notification Alarm Sound",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "🔊 Test Alarm Sound",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BentoNoticeItemCard(
    notice: Notice,
    onOpenUrl: () -> Unit,
    onMarkAsRead: () -> Unit
) {
    val context = LocalContext.current
    // Elegant category-specific colors
    val (catBg, catText) = when (notice.category) {
        "Counselling" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "Schedule" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "Notice" -> Pair(Color(0xFFE0F7FA), Color(0xFF006064))
        else -> Pair(Color(0xFFF3E5F5), Color(0xFF4A148C))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onMarkAsRead()
                onOpenUrl()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            width = if (notice.isNew) 2.dp else 1.dp,
            color = if (notice.isNew) Color(0xFF386A20) else Color(0xFFE1E3D5)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(catBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = notice.category.uppercase(),
                        color = catText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Status Info or Date
                if (notice.isNew) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF386A20))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Text(
                        text = notice.date,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF74796D)
                    )
                }
            }

            // Notice Title
            Text(
                text = notice.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C18),
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            notice.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = Color(0xFF43483E),
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Divider(color = Color(0xFFF0F2E7), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (notice.url.lowercase().endsWith(".pdf")) "📄 PDF DOCUMENT" else "🌐 WEBPAGE",
                    fontSize = 11.sp,
                    color = Color(0xFF74796D),
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (notice.isNew) {
                        Text(
                            text = "Acknowledge",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF386A20),
                            modifier = Modifier.clickable { onMarkAsRead() }
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share link",
                        tint = Color(0xFF386A20),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                // Simple share link launcher
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, notice.title)
                                    putExtra(Intent.EXTRA_TEXT, "${notice.title}\n\nLink: ${notice.url}")
                                }
                                val chooser = Intent.createChooser(shareIntent, "Share WBJEE Notice")
                                context.startActivity(chooser)
                            }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never scanned"
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
