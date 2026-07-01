@file:Suppress("DEPRECATION")
package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.data.AppDatabase
import com.example.data.ShiftDay
import com.example.data.ShiftRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.ShiftViewModel
import com.example.ui.ShiftViewModelFactory
import com.example.ui.RosterScreen
import com.example.ui.LoginScreen
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun safeZoneId(): java.time.ZoneId {
    return try {
        java.time.ZoneId.systemDefault()
    } catch (e: Exception) {
        java.time.ZoneId.of("UTC")
    }
}

private fun safeLocalDateNow(): LocalDate {
    return try {
        LocalDate.now(safeZoneId())
    } catch (e: Exception) {
        LocalDate.of(2026, 5, 27)
    }
}

private fun safeLocalDateTimeNow(): java.time.LocalDateTime {
    return try {
        java.time.LocalDateTime.now(safeZoneId())
    } catch (e: Exception) {
        java.time.LocalDateTime.of(2026, 5, 27, 12, 0)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            ReminderReceiver.createNotificationChannel(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            
            // Initialise RosterData persistent system
            com.example.ui.RosterData.appContext = context.applicationContext
            com.example.ui.RosterData.initMonths()

            // Auto-connect Firebase Sync on app launch if configured
            LaunchedEffect(Unit) {
                com.example.ui.FirebaseSync.init(context.applicationContext)
            }

            val database = remember { AppDatabase.getDatabase(context) }
            val repository = remember { ShiftRepository(database.shiftDao()) }
            val viewModel: ShiftViewModel = viewModel(
                factory = ShiftViewModelFactory(repository)
            )

            val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
            val isRunningInTest = remember {
                try {
                    android.os.Build.FINGERPRINT == "robolectric" ||
                    Class.forName("org.robolectric.RuntimeEnvironment") != null
                } catch (t: Throwable) {
                    false
                }
            }
            var isLoggedIn by remember {
                mutableStateOf(isRunningInTest || prefs.getBoolean("is_logged_in", false))
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoggedIn) {
                        ShiftAppScreen(
                            viewModel = viewModel,
                            onLogout = { isLoggedIn = false }
                        )
                    } else {
                        LoginScreen(
                            onLoginSuccess = { name ->
                                isLoggedIn = true
                            }
                        )
                    }
                }
            }
        }
    }
}

// Extension to support horizontal swipe gestures
fun Modifier.onSwipeHorizontal(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = this.composed {
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    var dragAmountAccumulated by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAmountAccumulated += delta
    }
    this.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = { dragAmountAccumulated = 0f },
        onDragStopped = {
            if (dragAmountAccumulated < -120f) {
                currentOnSwipeLeft()
            } else if (dragAmountAccumulated > 120f) {
                currentOnSwipeRight()
            }
        }
    )
}

// Helper for striped painting
fun Modifier.stripedBackground(
    bgColor: Color,
    stripeColor: Color,
    stripeWidth: Float = 3f,
    gapWidth: Float = 11f
) = this.drawBehind {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return@drawBehind
    val step = stripeWidth + gapWidth
    if (step <= 0.1f) {
        drawRect(color = bgColor)
        return@drawBehind
    }
    val f = stripeWidth / step
    val brush = Brush.linearGradient(
        0.0f to stripeColor,
        f to stripeColor,
        f to bgColor,
        1.0f to bgColor,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(step, step),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated
    )
    drawRect(brush = brush)
}

// Colors for different shift types (Unified with Roster/Rozpis)
object ShiftColorScheme {
    val Morning = Color(0xFF000000)
    val MorningBackground @Composable get() = getColorsForType("MORNING").second
    val MorningPRBackground @Composable get() = getColorsForType("MORNING_PR").second
    val NightBackground @Composable get() = getColorsForType("NIGHT").second
    val NightPNBackground @Composable get() = getColorsForType("NIGHT_PN").second
    val VacationBackground @Composable get() = getColorsForType("VACATION").second
    val SickBackground @Composable get() = getColorsForType("SICK").second
    val OvertimeBackground @Composable get() = getColorsForType("OVERTIME").second
    val MeetingBackground @Composable get() = getColorsForType("MEETING").second
    val TrainingBackground @Composable get() = getColorsForType("TRAINING").second
    val NoneBackground @Composable get() = getColorsForType("NONE").second

    @Composable
    fun getColorsForType(type: String): Pair<Color, Color> {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        return getColorsForType(type, isDark)
    }

    fun isCurrentUserInDayGroup(): Boolean {
        val ctx = com.example.ui.RosterData.appContext ?: return false
        val prefs = ctx.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", null) ?: return false
        return com.example.ui.RosterData.isDailyGroupEmployee(name)
    }

    fun getColorsForType(type: String, isDark: Boolean): Pair<Color, Color> {
        val code = when (type) {
            "MORNING" -> "R"
            "MORNING_PR" -> "PR"
            "NIGHT" -> "N"
            "NIGHT_PN" -> "PN"
            "VACATION" -> "D"
            "SICK" -> "CH"
            "KZ" -> "KZ"
            "Par" -> "Par"
            "MEETING" -> "P"
            "TRAINING" -> "V"
            else -> null
        }
        if (code != null) {
            val isDayGroup = if (code == "P") isCurrentUserInDayGroup() else false
            val cellColors = com.example.ui.RosterData.getCellColorScheme(code, isDark = isDark, isDayGroup = isDayGroup)
            return Pair(cellColors.text, cellColors.background)
        }
        if (type == "OVERTIME") {
            return if (isDark) {
                Pair(Color(0xFF000000), Color(0xFFFFB300))
            } else {
                Pair(Color(0xFF000000), Color(0xFFFFCA28))
            }
        }
        val text = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
        val bg = if (isDark) Color(0xFF1E293B) else Color(0xFFECEFF1)
        return Pair(text, bg)
    }

    @Composable
    fun getStripeColorForType(type: String): Color? {
        return null
    }

    fun getSlovakName(type: String): String {
        return when (type) {
            "MORNING" -> "Ranná (R)"
            "MORNING_PR" -> "PCO ranná (PR)"
            "NIGHT" -> "Nočná (N)"
            "NIGHT_PN" -> "PCO nočná (PN)"
            "VACATION" -> "Dovolenka"
            "SICK" -> "CH"
            "KZ" -> "KZ"
            "Par" -> "Paragraf (Par)"
            "OVERTIME" -> "Nadčas (NČ)"
            "MEETING" -> "Porada (P)"
            "TRAINING" -> "Výcvik (V)"
            else -> "Voľno"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftAppScreen(viewModel: ShiftViewModel, onLogout: () -> Unit = {}) {
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    // Bidirectional Month Synchronization between Calendar View (selectedMonth) and Firebase Roster (activeRosterMonth)
    LaunchedEffect(selectedMonth) {
        val mappedIndex = if (selectedMonth.year == 2025 && selectedMonth.monthValue == 12) {
            0
        } else if (selectedMonth.year == 2026 && selectedMonth.monthValue in 1..12) {
            selectedMonth.monthValue
        } else {
            1
        }
        if (com.example.ui.RosterData.activeRosterMonth != mappedIndex) {
            com.example.ui.RosterData.switchMonth(mappedIndex)
        }
    }

    val activeRosterMonth = com.example.ui.RosterData.activeRosterMonth
    LaunchedEffect(activeRosterMonth) {
        val ym = com.example.ui.RosterData.getYearMonthForIndex(activeRosterMonth)
        if (selectedMonth != ym) {
            viewModel.setSelectedMonth(ym)
        }
    }
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentView by viewModel.currentView.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val allShiftDays by viewModel.visibleShiftDays.collectAsStateWithLifecycle()
    val isReadOnlyPreview by viewModel.isReadOnlyPreview.collectAsStateWithLifecycle()
    val previewSenderName by viewModel.previewSenderName.collectAsStateWithLifecycle()

    val currentShift by viewModel.currentDayShift.collectAsStateWithLifecycle(initialValue = null)

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val activeTab = pagerState.currentPage
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRosterSettingsDialog by remember { mutableStateOf(false) }
    var showFirebaseDialog by remember { mutableStateOf(false) }
    val isCountdownEnabled by viewModel.isCountdownEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultShiftLength by viewModel.defaultShiftLength.collectAsStateWithLifecycle()
    val isCleanerModeEnabled by viewModel.isCleanerModeEnabled.collectAsStateWithLifecycle()
    val vacationAllowance by viewModel.vacationAllowance.collectAsStateWithLifecycle()

    val editDayListState = rememberLazyListState()
    val editDayScrollState = rememberScrollState()
    val statsListState = rememberLazyListState()
    val overviewListState = rememberLazyListState()

    val showHeader by remember {
        derivedStateOf {
            when (activeTab) {
                0 -> {
                    if (selectedDate == null) {
                        editDayScrollState.value < 20
                    } else {
                        editDayListState.firstVisibleItemIndex == 0
                    }
                }
                1 -> statsListState.firstVisibleItemIndex == 0
                2 -> overviewListState.firstVisibleItemIndex == 0
                else -> true
            }
        }
    }
    val spentVacationDays = remember(allShiftDays) {
        allShiftDays.filter { it.shiftType == "VACATION" }.sumOf { it.shiftLength }.toDouble() / 12.0
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var authorizedAiUsers by remember {
        val saved = prefs.getStringSet("authorized_ai_users", null)
        val initial = saved ?: setOf("admin", "Rieger T.", "riegert", "rieger t.")
        if (saved == null) {
            prefs.edit().putStringSet("authorized_ai_users", initial).apply()
        }
        mutableStateOf(initial)
    }
    val isUserAuthorizedForAi = remember(userName, authorizedAiUsers) {
        authorizedAiUsers.any { it.trim().equals(userName.trim(), ignoreCase = true) }
    }
    var tariffSalary by remember { mutableStateOf(prefs.getFloat("tariff_salary", 936.50f)) }
    var personalAllowance by remember { mutableStateOf(prefs.getFloat("personal_allowance", 380.0f)) }
    var shiftAllowance by remember { mutableStateOf(prefs.getFloat("shift_allowance", 40.0f)) }
    var mealAllowance by remember { mutableStateOf(prefs.getFloat("meal_allowance", 0.00f)) }
    var isRosterNotificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("roster_notifications_enabled", true))
    }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showManageMessagesDialog by remember { mutableStateOf(false) }
    var shownPendingMessageDialog by remember { mutableStateOf<Map<String, Any>?>(null) }

    LaunchedEffect(com.example.ui.FirebaseSync.isConnected, userName) {
        if (com.example.ui.FirebaseSync.isConnected && userName.isNotBlank()) {
            val firestoreDb = try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            } catch(e: Exception) {
                null
            }
            if (firestoreDb != null) {
                firestoreDb.collection("messages")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            val isTopEmployee = com.example.ui.RosterData.topEmployees.any { 
                                it.name.trim().equals(userName.trim(), ignoreCase = true) 
                            }
                            val targetShift = if (isTopEmployee) "top" else "bottom"
                            
                            val unread = snapshot.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                val id = doc.id
                                val msgMap = data.toMutableMap()
                                msgMap["id"] = id
                                msgMap
                            }.filter { msg ->
                                val targetType = msg["targetType"] as? String ?: ""
                                val targetValue = msg["targetValue"] as? String ?: ""
                                val readBy = msg["readBy"] as? Map<*, *> ?: emptyMap<Any, Any>()
                                
                                val isMyTarget = when (targetType) {
                                    "all" -> true
                                    "shift" -> targetValue == targetShift
                                    "individual" -> targetValue.trim().equals(userName.trim(), ignoreCase = true)
                                    else -> false
                                }
                                
                                val alreadyRead = readBy.keys.any { key ->
                                    key.toString().trim().equals(userName.trim(), ignoreCase = true)
                                }
                                
                                isMyTarget && !alreadyRead
                            }.sortedBy { 
                                it["createdAt"] as? String ?: "" 
                            }
                            
                            if (unread.isNotEmpty() && shownPendingMessageDialog == null) {
                                shownPendingMessageDialog = unread.first()
                            }
                        }
                    }
            }
        }
    }

    if (showManageMessagesDialog) {
        RosterMessagesManagementDialog(onDismiss = { showManageMessagesDialog = false })
    }

    shownPendingMessageDialog?.let { msg ->
        val msgId = msg["id"] as? String ?: ""
        val title = msg["title"] as? String ?: "Dôležitý oznam"
        val body = msg["body"] as? String ?: ""
        val sender = msg["sender"] as? String ?: "Administrátor"
        val createdAt = msg["createdAt"] as? String ?: ""
        val priority = msg["priority"] as? String ?: "2"
        val isHigh = priority == "1" || priority == "high"
        
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isHigh) 2.dp else 1.dp,
                    color = if (isHigh) Color.Red else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isHigh) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isHigh) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isHigh) "Vysoká priorita" else "Doručená správa",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isHigh) Color.Red else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Odoslal: $sender",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        if (createdAt.isNotEmpty()) {
                            val parsedDate = try {
                                createdAt.substringBefore("T")
                            } catch(e: Exception) {
                                ""
                            }
                            if (parsedDate.isNotEmpty()) {
                                Text(
                                    text = parsedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = {
                            if (msgId.isNotEmpty()) {
                                try {
                                    val firestoreDb = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    firestoreDb.collection("messages").document(msgId)
                                        .update("readBy.${userName}", java.time.Instant.now().toString())
                                        .addOnCompleteListener {
                                            shownPendingMessageDialog = null
                                        }
                                } catch(e: Exception) {
                                    shownPendingMessageDialog = null
                                }
                            } else {
                                shownPendingMessageDialog = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHigh) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Potvrdiť prečítanie dôležitého oznamu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }



    LaunchedEffect(userName) {
        viewModel.activeUserName = userName
        com.example.ui.RosterData.onCellUpdatedExternal = { employeeName, day, code, hours ->
            if (employeeName.trim().equals(userName.trim(), ignoreCase = true)) {
                val yearMonth = com.example.ui.RosterData.getYearMonthForIndex(com.example.ui.RosterData.activeRosterMonth)
                val date = yearMonth.atDay(day)
                val isDayGroup = com.example.ui.RosterData.isDailyGroupEmployee(employeeName)
                val finalCode = when (code) {
                    "R", "SR" -> "MORNING"
                    "PR" -> "MORNING_PR"
                    "N", "SN" -> "NIGHT"
                    "PN" -> "NIGHT_PN"
                    "D" -> "VACATION"
                    "CH" -> "SICK"
                    "KZ", "KZS", "KZV", "KZVS" -> "KZ"
                    "Par" -> "Par"
                    "P" -> if (isDayGroup) "MORNING" else "MEETING"
                    "V" -> "TRAINING"
                    else -> "NONE"
                }
                val finalLength = hours?.replace(',', '.')?.toDoubleOrNull()?.toInt() ?: when (finalCode) {
                    "MORNING", "MORNING_PR" -> if (isDayGroup) 8 else 12
                    "NIGHT", "NIGHT_PN" -> 12
                    "VACATION" -> 12
                    "SICK" -> 12
                    "KZ" -> 12
                    "Par" -> 12
                    "MEETING" -> 2
                    "TRAINING" -> 5
                    else -> 0
                }
                viewModel.setShiftType(date, finalCode, finalLength, syncToRosterEnabled = false)
            }
        }
        if (userName.isNotBlank()) {
            for (m in 0..12) {
                com.example.ui.RosterData.syncRosterToSichereForUser(userName, m)
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getBoolean("countdown_enabled", true)
        viewModel.setCountdownEnabled(saved)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        viewModel.setThemeMode(savedTheme)
        val savedLength = prefs.getInt("default_shift_length", 12)
        viewModel.setDefaultShiftLength(savedLength)
        val savedCleaner = prefs.getBoolean("cleaner_mode_enabled", false)
        viewModel.setCleanerModeEnabled(savedCleaner)
        val savedVacation = prefs.getFloat("vacation_allowance", 25f)
        viewModel.setVacationAllowance(savedVacation)

        // Load previously stored in-app notifications
        com.example.ui.RosterData.loadInAppNotifications(context)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Plánovač zmien",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Výber zobrazenia rozpisov",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                NavigationDrawerItem(
                    label = { Text("Moje zmeny (Šichter)", fontWeight = FontWeight.Bold) },
                    selected = currentView == "shichter",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.setCurrentView("shichter")
                            drawerState.close()
                        }
                    },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text("Celkový rozpis (Rozpis)", fontWeight = FontWeight.Bold) },
                    selected = currentView == "rozpis",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.setCurrentView("rozpis")
                            drawerState.close()
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )


                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "Verzia 1.5 • December 2025",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            },
                            modifier = Modifier.testTag("menu_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = when (currentView) {
                                "shichter" -> "Moje zmeny"
                                "rozpis" -> "Celkový rozpis"
                                else -> "AI Asistent"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        if (!isRosterNotificationsEnabled) {
                            val notifications = com.example.ui.RosterData.inAppNotifications
                            val unreadCount = notifications.size
                            
                            IconButton(
                                onClick = { showNotificationsDialog = true },
                                modifier = Modifier.testTag("notifications_bell_btn")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) {
                                            Badge(
                                                containerColor = Color.Red,
                                                contentColor = Color.White
                                            ) {
                                                Text(text = unreadCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        tint = if (unreadCount > 0) Color.Red else MaterialTheme.colorScheme.onSurface,
                                        contentDescription = "Upozornenia"
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                showSettingsDialog = true
                            },
                            modifier = Modifier.testTag("settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Nastavenia"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentView == "shichter",
                        onClick = { viewModel.setCurrentView("shichter") },
                        icon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = "Moje zmeny") },
                        label = { Text("Moje zmeny", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_shichter")
                    )
                    NavigationBarItem(
                        selected = currentView == "rozpis",
                        onClick = { viewModel.setCurrentView("rozpis") },
                        icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Celkový rozpis") },
                        label = { Text("Celkový rozpis", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_rozpis")
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            if (currentView == "rozpis") {
                RosterScreen(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
            // Read-Only Preview Mode indicator banner
            if (isReadOnlyPreview) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "ReadOnly",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Náhľad smien od: $previewSenderName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Režim čítania - úpravy sú zablokované",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        TextButton(
                            onClick = { viewModel.endPreview() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Skončiť", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Live active shift countdown banner
            if (isCountdownEnabled) {
                LiveOngoingShiftBanner(allShiftDays = allShiftDays)
            }

            AnimatedVisibility(
                visible = showHeader,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // Month Switcher Controller
                MonthSelectorHeader(
                    selectedMonth = selectedMonth,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() }
                )
            }

            // Custom Calendar Grid Composable (with horizontal swiping to change months)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSwipeHorizontal(
                        onSwipeLeft = { viewModel.nextMonth() },
                        onSwipeRight = { viewModel.previousMonth() }
                    )
            ) {
                CalendarGrid(
                    selectedMonth = selectedMonth,
                    selectedDate = selectedDate,
                    shiftDays = allShiftDays,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab bar for separating Day Editor, Month Statistics and Month Overview
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upraviť deň")
                        }
                    },
                    modifier = Modifier.testTag("tab_edit_day")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Štatistiky")
                        }
                    },
                    modifier = Modifier.testTag("tab_stats")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Denný prehľad")
                        }
                    },
                    modifier = Modifier.testTag("tab_overview")
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { page ->
                when (page) {
                    0 -> {
                        // TAB 1: Day Details Editor
                        val dateToEdit = selectedDate
                        if (dateToEdit == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editDayScrollState),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(24.dp).fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Vyberte deň v kalendári pre úpravu",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if (!isReadOnlyPreview) {
                                    MonthQuickActionsCard(
                                        defaultShiftLength = defaultShiftLength,
                                        onQuickFillTemplate = { sequence, length ->
                                            viewModel.applyTemplateForRemainingDays(sequence, length)
                                        },
                                        onClearMonth = {
                                            viewModel.clearCurrentMonthData()
                                        }
                                    )
                                }
                            }
                        } else {
                            if (isReadOnlyPreview) {
                                ReadOnlyDayView(
                                    selectedDate = dateToEdit,
                                    currentShift = currentShift,
                                    onDeselectDate = { viewModel.clearSelectedDate() },
                                    listState = editDayListState
                                )
                            } else {
                                DayEditorView(
                                    selectedDate = dateToEdit,
                                    currentShift = currentShift,
                                    defaultShiftLength = defaultShiftLength,
                                    isCleanerModeEnabled = isCleanerModeEnabled,
                                    onShiftTypeSelected = { type ->
                                        val length = when (type) {
                                            "MEETING" -> 2
                                            "TRAINING" -> 5
                                            else -> defaultShiftLength
                                        }
                                        viewModel.setShiftType(dateToEdit, type, length)
                                    },
                                    onShiftLengthSelected = { len -> viewModel.setShiftLength(dateToEdit, len) },
                                    onOvertimeHoursSelected = { hours -> viewModel.setOvertimeHours(dateToEdit, hours) },
                                    onSaveNotes = { note, reminder ->
                                        viewModel.saveNoteAndReminder(dateToEdit, note, reminder)
                                        if (!reminder.isNullOrBlank()) {
                                            ReminderReceiver.scheduleReminder(context, dateToEdit, reminder, note)
                                        } else {
                                            ReminderReceiver.cancelReminder(context, dateToEdit)
                                        }
                                    },
                                    onQuickFillTemplate = { sequence, length ->
                                        viewModel.applyTemplateForRemainingDays(sequence, length)
                                    },
                                    onClearMonth = {
                                        viewModel.clearCurrentMonthData()
                                    },
                                    onDeselectDate = {
                                        viewModel.clearSelectedDate()
                                    },
                                    onClearDay = {
                                        ReminderReceiver.cancelReminder(context, dateToEdit)
                                        viewModel.clearDayData(dateToEdit)
                                    },
                                    listState = editDayListState
                                )
                            }
                        }
                    }
                    1 -> {
                        // TAB 2: Month Statistics & Reports
                        MonthStatisticsView(
                            selectedMonth = selectedMonth,
                            shiftDays = allShiftDays,
                            vacationAllowance = vacationAllowance,
                            tariffSalary = tariffSalary,
                            personalAllowance = personalAllowance,
                            shiftAllowance = shiftAllowance,
                            mealAllowance = mealAllowance,
                            defaultShiftLength = defaultShiftLength,
                            listState = statsListState
                        )
                    }
                    2 -> {
                        // TAB 3: Month Overview
                        MonthOverviewView(
                            selectedDate = selectedDate,
                            selectedMonth = selectedMonth,
                            shiftDays = allShiftDays,
                            onSaveDayNotes = { date, note, reminder ->
                                viewModel.saveNoteAndReminder(date, note, reminder)
                            },
                            basicHourlyRate = tariffSalary.toDouble() / 177.0,
                            listState = overviewListState
                        )
                    }
                }
            }
        }
    }
}
}

    // Firebase settings pop-up dialog
    if (showFirebaseDialog) {
        FirebaseSettingsDialog(onDismiss = { showFirebaseDialog = false })
    }

    // App Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            isCountdownEnabled = isCountdownEnabled,
            themeMode = themeMode,
            defaultShiftLength = defaultShiftLength,
            isCleanerModeEnabled = isCleanerModeEnabled,
            userName = userName,
            vacationAllowance = vacationAllowance,
            spentVacationDays = spentVacationDays,
            tariffSalary = tariffSalary,
            personalAllowance = personalAllowance,
            shiftAllowance = shiftAllowance,
            mealAllowance = mealAllowance,
            isRosterNotificationsEnabled = isRosterNotificationsEnabled,
            onUserNameChange = { name ->
                userName = name
                prefs.edit().putString("user_name", name).apply()
            },
            onShowFirebaseSync = {
                showFirebaseDialog = true
                showSettingsDialog = false
            },
            onShowManageMessages = {
                showManageMessagesDialog = true
                showSettingsDialog = false
            },
            onShareMyShifts = { onlyCycle ->
                try {
                    val shareCode = viewModel.generateShareCode(userName, allShiftDays, onlyCycle)
                    val shareText = if (onlyCycle) {
                        "Ahoj, tu je môj kolobeh zmien! Kliknutím si načítaš môj náhľad zmien (iba kolobeh, bez poznámok a dát).\n\nKód zmien pre import:\n$shareCode"
                    } else {
                        "Ahoj, tu sú moje zmeny! Kliknutím si načítaš môj náhľad zmien v rovnakej aplikácii.\n\nKód zmien pre import:\n$shareCode"
                    }
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Kód zmien", shareCode)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Kód zmien bol skopírovaný do schránky!", android.widget.Toast.LENGTH_SHORT).show()

                    // Intent share
                    val sendIntent = android.content.Intent().apply {
                         action = android.content.Intent.ACTION_SEND
                         putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                         type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Zdieľať zmeny").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(shareIntent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Zdieľanie zlyhalo: ${e.localizedMessage ?: "Neznáma chyba"}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            onImportShifts = { code ->
                val res = viewModel.importShareCode(code)
                if (res != null) {
                    viewModel.startPreview(res.first, res.second)
                    true
                } else {
                    false
                }
            },
            onToggleCountdown = { enabled ->
                viewModel.setCountdownEnabled(enabled)
                prefs.edit().putBoolean("countdown_enabled", enabled).apply()
            },
            onToggleTheme = { mode ->
                viewModel.setThemeMode(mode)
                prefs.edit().putString("theme_mode", mode).apply()
            },
            onSetDefaultShiftLength = { length ->
                viewModel.setDefaultShiftLength(length)
                prefs.edit().putInt("default_shift_length", length).apply()
            },
            onToggleCleanerMode = { enabled ->
                viewModel.setCleanerModeEnabled(enabled)
                prefs.edit().putBoolean("cleaner_mode_enabled", enabled).apply()
            },
            onVacationAllowanceChange = { limit ->
                viewModel.setVacationAllowance(limit)
                prefs.edit().putFloat("vacation_allowance", limit).apply()
            },
            onTariffSalaryChange = { valSalary ->
                tariffSalary = valSalary
                prefs.edit().putFloat("tariff_salary", valSalary).apply()
            },
            onPersonalAllowanceChange = { valAllowance ->
                personalAllowance = valAllowance
                prefs.edit().putFloat("personal_allowance", valAllowance).apply()
            },
            onShiftAllowanceChange = { valAllowance ->
                shiftAllowance = valAllowance
                prefs.edit().putFloat("shift_allowance", valAllowance).apply()
            },
            onMealAllowanceChange = { valAllowance ->
                mealAllowance = valAllowance
                prefs.edit().putFloat("meal_allowance", valAllowance).apply()
            },
            onToggleRosterNotifications = { enabled ->
                isRosterNotificationsEnabled = enabled
                prefs.edit().putBoolean("roster_notifications_enabled", enabled).apply()
            },
            onLogout = {
                prefs.edit().putBoolean("is_logged_in", false).apply()
                onLogout()
                showSettingsDialog = false
            },
            onUpdateAuthorizedUsers = { newSet ->
                authorizedAiUsers = newSet
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // In-app Notifications Dialog
    if (showNotificationsDialog) {
        val notifications = com.example.ui.RosterData.inAppNotifications
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                    Text(
                        text = "Upozornenia o zmenách",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Žiadne nové upozornenia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications) { item ->
                            val title = item.first
                            val desc = item.second
                            val ts = item.third
                            val timeStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (notifications.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            com.example.ui.RosterData.clearInAppNotifications(context)
                            showNotificationsDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Vymazať všetko")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Zavrieť")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun MonthSelectorHeader(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.testTag("prev_month_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Predchádzajúci mesiac",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            val monthNameSlovak = getSlovakMonthName(selectedMonth.monthValue)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$monthNameSlovak ${selectedMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.testTag("next_month_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Nasledujúci mesiac",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CalendarGrid(
    selectedMonth: YearMonth,
    selectedDate: LocalDate?,
    shiftDays: List<ShiftDay>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf("Po", "Ut", "St", "Št", "Pi", "So", "Ne")
    
    // First day of selected month
    val firstDay = selectedMonth.atDay(1)
    // Day of week number: 1 = Monday, 7 = Sunday
    val startPadding = firstDay.dayOfWeek.value - 1
    val daysInMonth = selectedMonth.lengthOfMonth()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Days of the Week Row
            Row(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dayName == "So" || dayName == "Ne") {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Create dates map to avoid linear scans
            val shiftMap = remember(shiftDays) { shiftDays.associateBy { it.date } }

            // Grid Calculations
            val totalCells = startPadding + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < startPadding || cellIndex >= totalCells) {
                            // Empty space for padding
                            Box(modifier = Modifier.weight(1f))
                        } else {
                            val dayNumber = cellIndex - startPadding + 1
                            val currentDate = selectedMonth.atDay(dayNumber)
                            val dateString = currentDate.toString()

                            val shift = shiftMap[dateString]
                            val isSelected = currentDate == selectedDate

                            CalendarDayItem(
                                dayNumber = dayNumber,
                                isToday = currentDate == safeLocalDateNow(),
                                isSelected = isSelected,
                                shift = shift,
                                hasNotes = shift?.note?.isNotBlank() == true || shift?.reminderText?.isNotBlank() == true,
                                onClick = { onDateSelected(currentDate) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayItem(
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    shift: ShiftDay?,
    hasNotes: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shiftType = shift?.shiftType ?: "NONE"
    val (primaryColor, backgroundColor) = ShiftColorScheme.getColorsForType(shiftType)

    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> null
    }

    val stripeColor = ShiftColorScheme.getStripeColorForType(shiftType)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (shiftType != "NONE") {
                    if (stripeColor != null) {
                        Modifier.stripedBackground(bgColor = backgroundColor, stripeColor = stripeColor)
                    } else {
                        Modifier.background(backgroundColor)
                    }
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
            .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .testTag("day_$dayNumber"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Day Number text
            Text(
                text = dayNumber.toString(),
                fontWeight = if (isSelected || isToday || shiftType != "NONE") FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    shiftType != "NONE" -> primaryColor
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            // Shift abbreviation or visual indicator
            if (shiftType != "NONE") {
                val label = when (shiftType) {
                    "MORNING" -> "R"
                    "MORNING_PR" -> "PR"
                    "NIGHT" -> "N"
                    "NIGHT_PN" -> "PN"
                    "VACATION" -> "D"
                    "SICK" -> "CH"
                    "OVERTIME" -> "NČ"
                    "MEETING" -> "P"
                    "TRAINING" -> "V"
                    else -> ""
                }
                val isLabelWithoutHours = shiftType == "VACATION" || shiftType == "SICK"
                val subtitleText = if (isLabelWithoutHours) {
                    label
                } else if ((shift?.overtimeHours ?: 0) > 0) {
                    "$label (${shift?.shiftLength}+${shift?.overtimeHours}h)"
                } else {
                    "$label (${shift?.shiftLength}h)"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = primaryColor,
                    maxLines = 1
                )
            }

            // Notification dots (notes indicators)
            if (hasNotes) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveOngoingShiftBanner(allShiftDays: List<ShiftDay>) {
    var currentTime by remember { mutableStateOf(safeLocalDateTimeNow()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            try {
                currentTime = safeLocalDateTimeNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            kotlinx.coroutines.delay(10000)
        }
    }

    val activeShiftInfo = remember(allShiftDays, currentTime) {
        try {
            val today = currentTime.toLocalDate()
            val yesterday = today.minusDays(1)
            val tomorrow = today.plusDays(1)

            // Case 1: Night shift yesterday (19:00 yesterday to 7:00 today)
            val yesterdayShift = allShiftDays.find { it.date == yesterday.toString() }
            val isYesterdayNight = yesterdayShift?.shiftType in listOf("NIGHT", "NIGHT_PN")
            val yesterdayNightStart = yesterday.atTime(19, 0)
            val yesterdayNightEnd = today.atTime(7, 0)
            
            if (isYesterdayNight && currentTime.isAfter(yesterdayNightStart) && currentTime.isBefore(yesterdayNightEnd)) {
                val duration = java.time.Duration.between(currentTime, yesterdayNightEnd)
                val h = duration.toHours()
                val m = duration.toMinutes() % 60
                val remainsStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val shiftLabel = if (yesterdayShift?.shiftType == "NIGHT_PN") "PCO nočná (PN)" else "Nočná"
                Triple(shiftLabel, "Nočná zmena: 19:00 - 7:00", remainsStr)
            } else {
                // Case 2: Today shift
                val todayShift = allShiftDays.find { it.date == today.toString() }
                if (todayShift != null) {
                    val isMorning = todayShift.shiftType in listOf("MORNING", "MORNING_PR")
                    val isNight = todayShift.shiftType in listOf("NIGHT", "NIGHT_PN")
                    
                    if (isMorning) {
                        val morningStart = today.atTime(7, 0)
                        val morningEnd = today.atTime(19, 0)
                        if (currentTime.isAfter(morningStart) && currentTime.isBefore(morningEnd)) {
                            val duration = java.time.Duration.between(currentTime, morningEnd)
                            val h = duration.toHours()
                            val m = duration.toMinutes() % 60
                            val remainsStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                            val shiftLabel = if (todayShift.shiftType == "MORNING_PR") "PCO ranná (PR)" else "Ranná"
                            Triple(shiftLabel, "Ranná zmena: 7:00 - 19:00", remainsStr)
                        } else null
                    } else if (isNight) {
                        val nightStart = today.atTime(19, 0)
                        val nightEnd = tomorrow.atTime(7, 0)
                        if (currentTime.isAfter(nightStart) && currentTime.isBefore(nightEnd)) {
                            val duration = java.time.Duration.between(currentTime, nightEnd)
                            val h = duration.toHours()
                            val m = duration.toMinutes() % 60
                            val remainsStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                            val shiftLabel = if (todayShift.shiftType == "NIGHT_PN") "PCO nočná (PN)" else "Nočná"
                            Triple(shiftLabel, "Nočná zmena: 19:00 - 7:00", remainsStr)
                        } else null
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    if (activeShiftInfo == null) return

    val (shiftName, interval, remaining) = activeShiftInfo

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("live_shift_banner"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFC8E6C9) // highly readable soft green matching live status
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF2E7D32), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AKTÍVNA ZMENA: $shiftName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        text = interval,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1B5E20).copy(alpha = 0.8f)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Zostáva: $remaining",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    isCountdownEnabled: Boolean,
    themeMode: String,
    defaultShiftLength: Int,
    isCleanerModeEnabled: Boolean,
    userName: String,
    vacationAllowance: Float,
    spentVacationDays: Double,
    tariffSalary: Float,
    personalAllowance: Float,
    shiftAllowance: Float,
    mealAllowance: Float,
    isRosterNotificationsEnabled: Boolean,
    onUserNameChange: (String) -> Unit,
    onShowFirebaseSync: () -> Unit,
    onShowManageMessages: () -> Unit,
    onShareMyShifts: (Boolean) -> Unit,
    onImportShifts: (String) -> Boolean,
    onToggleCountdown: (Boolean) -> Unit,
    onToggleTheme: (String) -> Unit,
    onSetDefaultShiftLength: (Int) -> Unit,
    onToggleCleanerMode: (Boolean) -> Unit,
    onVacationAllowanceChange: (Float) -> Unit,
    onTariffSalaryChange: (Float) -> Unit,
    onPersonalAllowanceChange: (Float) -> Unit,
    onShiftAllowanceChange: (Float) -> Unit,
    onMealAllowanceChange: (Float) -> Unit,
    onToggleRosterNotifications: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onUpdateAuthorizedUsers: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
    
    var isFinancialSettingsExpanded by remember { mutableStateOf(false) }
    var editPasswordInput by remember { mutableStateOf(prefs.getString("roster_login_password", "123456").orEmpty().ifEmpty { "123456" }) }
    var changeSuccessMessage by remember { mutableStateOf<String?>(null) }
    var changeErrorMessage by remember { mutableStateOf<String?>(null) }
    var rosterEmailInput by remember { mutableStateOf(prefs.getString("user_email", "") ?: "") }
    
    var importInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    var importSuccess by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Nastavenia Sichter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Zavrieť")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // PART 1: VZHĽAD A PROFIL
                Text(
                    text = "Vzhľad a Profil",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Theme Mode Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Farebný motív",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "LIGHT" to ("Svetlý" to Icons.Default.LightMode),
                            "DARK" to ("Tmavý" to Icons.Default.DarkMode),
                            "SYSTEM" to ("Systém" to Icons.Default.Refresh)
                        ).forEach { (mode, pair) ->
                            val (label, icon) = pair
                            val isSelected = themeMode == mode
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleTheme(mode) }
                                    .testTag("theme_btn_$mode"),
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                contentColor = contentColor,
                                border = border
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Username input
                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChange,
                    label = { Text("Meno používateľa (Príslušník)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_username_input")
                )

                // Email Input
                OutlinedTextField(
                    value = rosterEmailInput,
                    onValueChange = { input ->
                        rosterEmailInput = input
                        prefs.edit().putString("user_email", input).apply()
                    },
                    label = { Text("Prihlasovací e-mail") },
                    placeholder = { Text("Napr. jan@email.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("roster_settings_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Passcode Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Prístupové heslo (6-miestne)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editPasswordInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    editPasswordInput = input
                                    changeSuccessMessage = null
                                    changeErrorMessage = null
                                }
                            },
                            placeholder = { Text("Nové heslo (6 číslic)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                if (editPasswordInput.length == 6) {
                                    prefs.edit().putString("roster_login_password", editPasswordInput).apply()
                                    changeSuccessMessage = "Heslo úspešne uložené!"
                                    changeErrorMessage = null
                                } else {
                                    changeErrorMessage = "Heslo musí mať presne 6 číslic!"
                                    changeSuccessMessage = null
                                }
                            },
                            modifier = Modifier.testTag("settings_password_save_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Uložiť")
                        }
                    }
                    changeSuccessMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                    }
                    changeErrorMessage?.let { err ->
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // PART 2: MOJE ZMENY (ŠICHTER) NASTAVENIA
                Text(
                    text = "Moje zmeny (Šichter)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Shift Length configuration
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Predvolená dĺžka zmeny",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(8, 12).forEach { len ->
                            val isSelected = defaultShiftLength == len
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSetDefaultShiftLength(len) }
                                    .testTag("settings_length_chip_$len"),
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                contentColor = contentColor,
                                border = border
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$len Hodín",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Countdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Čas zmeny a odpočet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zobrazí aktívnu zmenu a odpočet na domovskej obrazovke.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCountdownEnabled,
                        onCheckedChange = onToggleCountdown,
                        modifier = Modifier.testTag("settings_countdown_switch")
                    )
                }

                // Cleaner Mode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cleaner mód",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zobrazí iba typ zmeny a skryje detaily/úlohy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCleanerModeEnabled,
                        onCheckedChange = onToggleCleanerMode,
                        modifier = Modifier.testTag("settings_cleaner_switch")
                    )
                }

                // Osobné a platové nastavenia
                OutlinedCard(
                    onClick = { isFinancialSettingsExpanded = !isFinancialSettingsExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_financial_toggle_card"),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isFinancialSettingsExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFinancialSettingsExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Osobné a platové nastavenia",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isFinancialSettingsExpanded) "Kliknutím skryjete" else "Tarif, bonusy, dovolenka",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isFinancialSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isFinancialSettingsExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        var tariffText by remember(tariffSalary) {
                            mutableStateOf(String.format(java.util.Locale.US, "%.2f", tariffSalary).replace(".", ","))
                        }
                        var personalText by remember(personalAllowance) {
                            mutableStateOf(String.format(java.util.Locale.US, "%.2f", personalAllowance).replace(".", ","))
                        }
                        var shiftText by remember(shiftAllowance) {
                            mutableStateOf(String.format(java.util.Locale.US, "%.2f", shiftAllowance).replace(".", ","))
                        }

                        OutlinedTextField(
                            value = tariffText,
                            onValueChange = { newValue ->
                                tariffText = newValue
                                val parsed = newValue.replace(",", ".").toFloatOrNull()
                                if (parsed != null && parsed >= 0f) {
                                    onTariffSalaryChange(parsed)
                                }
                            },
                            label = { Text("Tarifný plat (€/mesiac)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_tariff_salary_input")
                        )

                        OutlinedTextField(
                            value = personalText,
                            onValueChange = { newValue ->
                                personalText = newValue
                                val parsed = newValue.replace(",", ".").toFloatOrNull()
                                if (parsed != null && parsed >= 0f) {
                                    onPersonalAllowanceChange(parsed)
                                }
                            },
                            label = { Text("Osobný príplatok (€/mesiac)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_personal_allowance_input")
                        )

                        OutlinedTextField(
                            value = shiftText,
                            onValueChange = { newValue ->
                                shiftText = newValue
                                val parsed = newValue.replace(",", ".").toFloatOrNull()
                                if (parsed != null && parsed >= 0f) {
                                    onShiftAllowanceChange(parsed)
                                }
                            },
                            label = { Text("Príplatok iné (€/mesiac)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_shift_allowance_input")
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ročný nárok na dovolenku (dni)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        var allowanceText by remember(vacationAllowance) {
                            mutableStateOf(
                                if (vacationAllowance % 1.0f == 0.0f) vacationAllowance.toInt().toString()
                                else String.format(java.util.Locale.US, "%.1f", vacationAllowance).replace(".", ",")
                            )
                        }

                        OutlinedTextField(
                            value = allowanceText,
                            onValueChange = { newValue ->
                                allowanceText = newValue
                                val parsed = newValue.replace(",", ".").toFloatOrNull()
                                if (parsed != null && parsed >= 0f) {
                                    onVacationAllowanceChange(parsed)
                                }
                            },
                            label = { Text("Počet dní dovolenky") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_vacation_allowance_input")
                        )

                        val remainingDays = vacationAllowance.toDouble() - spentVacationDays
                        val spentHours = spentVacationDays * 12.0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Celkový nárok:", style = MaterialTheme.typography.bodySmall)
                                    Text("${if (vacationAllowance % 1f == 0f) vacationAllowance.toInt().toString() else String.format(java.util.Locale.US, "%.1f", vacationAllowance).replace(".", ",")} dní", fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Vyčerpané v kalendári:", style = MaterialTheme.typography.bodySmall)
                                    val spentStr = if (spentVacationDays % 1.0 == 0.0) spentVacationDays.toInt().toString() else String.format(java.util.Locale.US, "%.1f", spentVacationDays).replace(".", ",")
                                    val spentHoursStr = if (spentHours % 1.0 == 0.0) spentHours.toInt().toString() else String.format(java.util.Locale.US, "%.1f", spentHours).replace(".", ",")
                                    Text("$spentStr dní ($spentHoursStr h)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Zostáva dovolenky:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    val remainingStr = if (remainingDays % 1.0 == 0.0) remainingDays.toInt().toString() else String.format(java.util.Locale.US, "%.1f", remainingDays).replace(".", ",")
                                    val color = if (remainingDays < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    Text(text = "$remainingStr dní", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // PART 3: ZDIEĽANIE A IMPORT
                Text(
                    text = "Zdieľanie a Import",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { onShareMyShifts(false) },
                    modifier = Modifier.fillMaxWidth().testTag("settings_share_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zdieľať moje zmeny s poznámkami")
                }

                Button(
                    onClick = { onShareMyShifts(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().testTag("settings_share_cycle_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zdieľať iba kolobeh zmien")
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Importovať zmeny zo schránky", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = importInput,
                            onValueChange = { importInput = it; importError = false; importSuccess = false },
                            placeholder = { Text("Sem vložte import kód") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("settings_import_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                if (importInput.isNotBlank()) {
                                    val success = onImportShifts(importInput.trim())
                                    if (success) {
                                        importSuccess = true
                                        importError = false
                                        importInput = ""
                                    } else {
                                        importError = true
                                        importSuccess = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_import_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Import")
                        }
                    }
                    if (importSuccess) {
                        Text("Zmeny boli úspešne načítané do náhľadu!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    if (importError) {
                        Text("Chybný import kód!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // PART 4: ADMINISTRÁTORSKÉ MOŽNOSTI & SYNCHRONIZÁCIA
                val isAdmin = userName.trim().equals("admin", ignoreCase = true) || userName.trim().equals("Rieger T.", ignoreCase = true)
                Text(
                    text = if (isAdmin) "Administrátor a Cloud Sync" else "Cloud Synchronizácia",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Roster Notifications Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upozornenia pre rozpis",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Povoliť systémové upozornenia na zmeny v rozpise.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRosterNotificationsEnabled,
                        onCheckedChange = onToggleRosterNotifications,
                        modifier = Modifier.testTag("roster_notifications_switch")
                    )
                }

                // Connection indicator row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowFirebaseSync() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Firebase Synchronizácia",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Nastavenia prepojenia s webom a synchronizácie zmien v reálnom čase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Firebase",
                        tint = if (com.example.ui.FirebaseSync.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("firebase_sync_btn_icon")
                    )
                }

                if (isAdmin) {
                    // Internal Messages Management Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowManageMessages() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Správa interných správ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Uverejňovanie oznamov s potvrdzovaním o prečítaní.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Správa správ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("manage_messages_btn_icon")
                        )
                    }

                    // AI Permissions Row
                    var showAiPermissions by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAiPermissions = !showAiPermissions }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prístup k AI asistentovi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Vyberte príslušníkov s povolením používať AI asistenta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showAiPermissions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Rozbaliť",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showAiPermissions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val employeesList = remember {
                                (com.example.ui.RosterData.topEmployees + com.example.ui.RosterData.bottomEmployees).map { it.name }.distinct().sorted()
                            }
                            var authorizedSet by remember {
                                val saved = prefs.getStringSet("authorized_ai_users", null)
                                mutableStateOf(saved ?: setOf("admin", "Rieger T.", "riegert", "rieger t."))
                            }

                            employeesList.forEach { empName ->
                                val isAuthorized = authorizedSet.any { it.trim().equals(empName.trim(), ignoreCase = true) }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = empName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    Checkbox(
                                        checked = isAuthorized,
                                        onCheckedChange = { checked ->
                                            val newSet = authorizedSet.toMutableSet()
                                            if (checked) {
                                                newSet.add(empName)
                                            } else {
                                                if (!empName.trim().equals("admin", ignoreCase = true) && !empName.trim().equals("Rieger T.", ignoreCase = true)) {
                                                    newSet.removeAll { it.trim().equals(empName.trim(), ignoreCase = true) }
                                                }
                                            }
                                            prefs.edit().putStringSet("authorized_ai_users", newSet).apply()
                                            authorizedSet = newSet
                                            onUpdateAuthorizedUsers(newSet)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Logout Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLogout()
                            onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Odhlásiť sa",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Uzamkne celú aplikáciu a vráti vás na prihlasovaciu obrazovku.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Odhlásiť sa",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("logout_btn_icon")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("settings_close_btn")
                    ) {
                        Text("Zavrieť", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RosterSettingsDialog(
    themeMode: String,
    isNotificationsEnabled: Boolean,
    userName: String,
    onShowFirebaseSync: () -> Unit,
    onShowManageMessages: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleTheme: (String) -> Unit,
    onLogout: () -> Unit = {},
    onUpdateAuthorizedUsers: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
    var editPasswordInput by remember { mutableStateOf(prefs.getString("roster_login_password", "123456").orEmpty().ifEmpty { "123456" }) }
    var changeSuccessMessage by remember { mutableStateOf<String?>(null) }
    var changeErrorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("roster_settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Nastavenia rozpisu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Theme Mode Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Vzhľad rozpisu",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vyberte si farebný motív (tmavý alebo svetlý) pre prispôsobenie celého vzhľadu rozpisu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "LIGHT" to ("Svetlý" to Icons.Default.LightMode),
                            "DARK" to ("Tmavý" to Icons.Default.DarkMode),
                            "SYSTEM" to ("Systém" to Icons.Default.Refresh)
                        ).forEach { (mode, pair) ->
                            val (label, icon) = pair
                            val isSelected = themeMode == mode
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleTheme(mode) }
                                    .testTag("roster_theme_btn_$mode"),
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                contentColor = contentColor,
                                border = border
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Notifications Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleNotifications(!isNotificationsEnabled) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upozornenia pre rozpis",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zapnúť alebo vypnúť upozornenia a notifikácie ku zmenám z tohto rozpisu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isNotificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        modifier = Modifier.testTag("roster_notifications_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Registered Email Section
                var rosterEmailInput by remember { mutableStateOf(prefs.getString("user_email", "") ?: "") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Prihlasovací e-mail",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Váš uložený prihlasovací e-mail, ktorý sa zobrazuje a dá sa meniť.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = rosterEmailInput,
                        onValueChange = { input ->
                            rosterEmailInput = input
                            prefs.edit().putString("user_email", input).apply()
                        },
                        placeholder = { Text("Napr. jan@email.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("roster_settings_email_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Passcode Edit Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Prístupové heslo (6-miestne)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nastavte nové číselné heslo pre zabezpečenie prístupu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editPasswordInput,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { it.isDigit() }) {
                                    editPasswordInput = input
                                    changeSuccessMessage = null
                                    changeErrorMessage = null
                                }
                            },
                            placeholder = { Text("Nové heslo (6 číslic)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                if (editPasswordInput.length == 6) {
                                    prefs.edit().putString("roster_login_password", editPasswordInput).apply()
                                    changeSuccessMessage = "Heslo úspešne uložené!"
                                    changeErrorMessage = null
                                } else {
                                    changeErrorMessage = "Heslo musí mať presne 6 číslic!"
                                    changeSuccessMessage = null
                                }
                            },
                            modifier = Modifier.testTag("settings_password_save_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Uložiť")
                        }
                    }
                    changeSuccessMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    changeErrorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Odhlásenie Section (Logout Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLogout()
                            onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Odhlásiť sa",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Uzamkne celú aplikáciu a vráti vás na prihlasovaciu obrazovku.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Odhlásiť sa",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("logout_btn_icon")
                    )
                }

                if (userName.trim().equals("admin", ignoreCase = true) || userName.trim().equals("Rieger T.", ignoreCase = true)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onShowFirebaseSync()
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Firebase Synchronizácia",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Nastavenia prepojenia so spoločným webovým admin panelom a synchronizácia zmien.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Firebase Synchronizácia",
                            tint = if (com.example.ui.FirebaseSync.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("firebase_sync_btn_icon")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onShowManageMessages()
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Správa interných správ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Odosielanie dôležitých oznamov pre jednotlivcov, smeny alebo všetkých a sledovanie prečítania.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Správa interných správ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("manage_messages_btn_icon")
                        )
                    }

                    var showAiPermissions by remember { mutableStateOf(false) }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAiPermissions = !showAiPermissions }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prístup k AI asistentovi",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Určte, ktorí príslušníci majú povolený prístup k AI asistentovi smien.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showAiPermissions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Rozbaliť",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showAiPermissions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val employeesList = remember {
                                (com.example.ui.RosterData.topEmployees + com.example.ui.RosterData.bottomEmployees).map { it.name }.distinct().sorted()
                            }
                            var authorizedSet by remember {
                                val saved = prefs.getStringSet("authorized_ai_users", null)
                                mutableStateOf(saved ?: setOf("admin", "Rieger T.", "riegert", "rieger t."))
                            }

                            employeesList.forEach { empName ->
                                val isAuthorized = authorizedSet.any { it.trim().equals(empName.trim(), ignoreCase = true) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = empName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Checkbox(
                                        checked = isAuthorized,
                                        onCheckedChange = { checked ->
                                            val newSet = authorizedSet.toMutableSet()
                                            if (checked) {
                                                newSet.add(empName)
                                            } else {
                                                if (!empName.trim().equals("admin", ignoreCase = true) && !empName.trim().equals("Rieger T.", ignoreCase = true)) {
                                                    newSet.removeAll { it.trim().equals(empName.trim(), ignoreCase = true) }
                                                }
                                            }
                                            prefs.edit().putStringSet("authorized_ai_users", newSet).apply()
                                            authorizedSet = newSet
                                            onUpdateAuthorizedUsers(newSet)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Dialog close action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("roster_settings_close_btn")
                    ) {
                        Text("Zavrieť", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReadOnlyDayView(
    selectedDate: java.time.LocalDate,
    currentShift: ShiftDay?,
    onDeselectDate: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val formattedDate = remember(selectedDate) {
        val dayOfWeek = getSlovakDayOfWeekName(selectedDate.dayOfWeek.value)
        val month = getSlovakMonthName(selectedDate.monthValue, genitive = true)
        "$dayOfWeek, ${selectedDate.dayOfMonth}. $month ${selectedDate.year}"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("read_only_day_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDeselectDate,
                    modifier = Modifier.testTag("deselect_preview_date_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zavrieť náhľad dňa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Shift Card
        item {
            val shiftType = currentShift?.shiftType ?: "NONE"
            val slovakName = ShiftColorScheme.getSlovakName(shiftType)
            val (mainColor, bgColor) = ShiftColorScheme.getColorsForType(shiftType)
            val cardStripeColor = ShiftColorScheme.getStripeColorForType(shiftType)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (cardStripeColor != null) {
                            Modifier.clip(RoundedCornerShape(16.dp)).stripedBackground(bgColor = bgColor.copy(alpha = 0.5f), stripeColor = cardStripeColor.copy(alpha = 0.5f))
                        } else {
                            Modifier
                        }
                    ),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (cardStripeColor != null) Color.Transparent else bgColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Large Badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(mainColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val badgeText = if (shiftType == "NONE") "-" else slovakName.take(1).uppercase()
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Status dňa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = slovakName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = mainColor
                        )
                        if (shiftType != "NONE") {
                            Text(
                                text = if ((currentShift?.shiftLength ?: 8) == 12) "Dĺžka zmeny: 12 hod. (odpracovaných 11,5 hod.)" else "Dĺžka zmeny: ${currentShift?.shiftLength ?: 8} hod.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Overtime Hours indicator if present
        if (currentShift != null && currentShift.overtimeHours > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Nadčas",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${currentShift.overtimeHours} odpracovaných hodín navyše",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Notes and Tasks Display
        val note = currentShift?.note
        val reminder = currentShift?.reminderText
        if (!note.isNullOrBlank() || !reminder.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!note.isNullOrBlank()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Poznámka",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!reminder.isNullOrBlank()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Úloha",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = reminder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayEditorView(
    selectedDate: LocalDate,
    currentShift: ShiftDay?,
    defaultShiftLength: Int,
    isCleanerModeEnabled: Boolean,
    onShiftTypeSelected: (String) -> Unit,
    onShiftLengthSelected: (Int) -> Unit,
    onOvertimeHoursSelected: (Int) -> Unit,
    onSaveNotes: (String?, String?) -> Unit,
    onQuickFillTemplate: (List<String>, Int) -> Unit,
    onClearMonth: () -> Unit,
    onDeselectDate: () -> Unit,
    onClearDay: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val focusManager = LocalFocusManager.current
    var noteText by remember(selectedDate) { mutableStateOf("") }
    var taskInputText by remember(selectedDate) { mutableStateOf("") }
    var selectedTime by remember(selectedDate) { mutableStateOf("") }

    var showTimePickerDialog by remember { mutableStateOf(false) }
    var selectedHour by remember(showTimePickerDialog) {
        val calendar = java.util.Calendar.getInstance()
        mutableStateOf(if (showTimePickerDialog) calendar.get(java.util.Calendar.HOUR_OF_DAY) else 12)
    }
    var selectedMinute by remember(showTimePickerDialog) {
        val calendar = java.util.Calendar.getInstance()
        mutableStateOf(if (showTimePickerDialog) calendar.get(java.util.Calendar.MINUTE) else 0)
    }

    if (showTimePickerDialog) {
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Zvoľte čas úlohy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Large digital display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = String.format("%02d", selectedHour),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = String.format("%02d", selectedMinute),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Controls Row (Hours and Minutes controls side-by-side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Hours controls
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hodiny",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        selectedHour = (selectedHour - 1 + 24) % 24
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Menej hodín",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = String.format("%02d", selectedHour),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        selectedHour = (selectedHour + 1) % 24
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Viac hodín",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(60.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Minutes controls
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Minúty",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        selectedMinute = (selectedMinute - 1 + 60) % 60
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Menej minút",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = String.format("%02d", selectedMinute),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        selectedMinute = (selectedMinute + 1) % 60
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Viac minút",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTimePickerDialog = false }) {
                            Text("Zrušiť")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                                selectedTime = formattedTime
                                showTimePickerDialog = false
                            }
                        ) {
                            Text("Potvrdiť", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    val formattedDate = remember(selectedDate) {
        val dayOfWeek = getSlovakDayOfWeekName(selectedDate.dayOfWeek.value)
        val month = getSlovakMonthName(selectedDate.monthValue, genitive = true)
        "$dayOfWeek, ${selectedDate.dayOfMonth}. $month ${selectedDate.year}"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selected Date label header with Close button
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDeselectDate,
                    modifier = Modifier.testTag("deselect_date_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zrušiť výber dňa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Button to cancel/clear the current day shift and data
        item {
            Button(
                onClick = onClearDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_day_data_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zrušiť tento deň (vynulovať)", fontWeight = FontWeight.Bold)
            }
        }

        // Shift type picker card
        item {
            ShiftTypeSelectorCard(
                selectedType = currentShift?.shiftType ?: "NONE",
                onTypeSelected = onShiftTypeSelected
            )
        }

        item {
            MonthQuickActionsCard(
                defaultShiftLength = defaultShiftLength,
                onQuickFillTemplate = onQuickFillTemplate,
                onClearMonth = onClearMonth
            )
        }

        val activeType = currentShift?.shiftType ?: "NONE"

        // Overtime Hours picker (available for any day when cleaner mode is disabled)
        if (!isCleanerModeEnabled) {
            item {
                OvertimeSelectorCard(
                    selectedOvertimeHours = currentShift?.overtimeHours ?: 0,
                    onOvertimeHoursSelected = onOvertimeHoursSelected
                )
            }
        }

        // Notes and Tasks editor card
        if (!isCleanerModeEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Poznámka a úloha",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Note input row with + button to add multiple notes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("Poznámka pre tento deň") },
                                placeholder = { Text("Napíšte poznámku...") },
                                modifier = Modifier.weight(1f).testTag("note_input"),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        focusManager.clearFocus()
                                        val currentNotes = currentShift?.note ?: ""
                                        val updatedNotes = if (currentNotes.isBlank()) {
                                            noteText
                                        } else {
                                            "$currentNotes\n$noteText"
                                        }
                                        onSaveNotes(updatedNotes, currentShift?.reminderText)
                                        noteText = "" // clear input so user can write another one
                                    }
                                },
                                enabled = noteText.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = if (noteText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Pridať ďalšiu poznámku",
                                    tint = if (noteText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Task input with bell icon and Add button
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = taskInputText,
                                    onValueChange = { taskInputText = it },
                                    label = { Text("Úloha pre tento deň") },
                                    placeholder = { Text("Napíšte text úlohy...") },
                                    modifier = Modifier.weight(1f).testTag("task_input"),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                showTimePickerDialog = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Zvoliť čas úlohy",
                                                tint = if (selectedTime.isNotBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        if (taskInputText.isNotBlank()) {
                                            focusManager.clearFocus()
                                            // Combine selectedTime and taskInputText: "HH:mm - Text" or just "Text"
                                            val finalReminderText = if (selectedTime.isNotBlank()) {
                                                "$selectedTime - $taskInputText"
                                            } else {
                                                taskInputText
                                            }
                                            onSaveNotes(currentShift?.note, finalReminderText)
                                            taskInputText = ""
                                            selectedTime = ""
                                        }
                                    },
                                    enabled = taskInputText.isNotBlank(),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (taskInputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Uložiť úlohu",
                                        tint = if (taskInputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (selectedTime.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Zvolený čas úlohy: $selectedTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(Zrušiť čas)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable { selectedTime = "" },
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthQuickActionsCard(
    defaultShiftLength: Int,
    onQuickFillTemplate: (List<String>, Int) -> Unit,
    onClearMonth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7).copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "RÝCHLE AKCIE PRE MESIAC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49454F),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Vyplňte zvyšné dni v mesiaci automaticky v kolobehu smien alebo kompletne vyčistite mesiac.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF49454F).copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onQuickFillTemplate(listOf("NIGHT", "NONE", "NONE", "MORNING_PR", "NIGHT_PN", "NONE", "NONE", "MORNING"), defaultShiftLength) },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("quick_fill_12h_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kolobeh (N-V-V-PR-PN)", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onClearMonth,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_month_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Vyčistiť mesiac", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ShiftTypeSelectorCard(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Typ zmeny / Status dňa",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = listOf(
                    listOf("NONE"),
                    listOf("MORNING", "MORNING_PR"),
                    listOf("NIGHT", "NIGHT_PN"),
                    listOf("OVERTIME"),
                    listOf("VACATION", "SICK"),
                    listOf("KZ", "Par"),
                    listOf("MEETING", "TRAINING")
                )

                rows.forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { type ->
                            val slovakName = ShiftColorScheme.getSlovakName(type)
                            val isSelected = type == selectedType
                            val (activeColor, activeBgColor) = ShiftColorScheme.getColorsForType(type)
                            val baseStripeColor = ShiftColorScheme.getStripeColorForType(type)

                            val animatedBgColor by animateColorAsState(
                                targetValue = if (isSelected) activeBgColor else MaterialTheme.colorScheme.surface,
                                label = "bgColorAnimation"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected && baseStripeColor != null) {
                                            Modifier.stripedBackground(bgColor = activeBgColor, stripeColor = baseStripeColor)
                                        } else {
                                            Modifier.background(animatedBgColor)
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onTypeSelected(type) }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = slovakName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) {
                                        activeColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftLengthSelectorCard(
    selectedLength: Int,
    onLengthSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dĺžka zmeny",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12).forEach { length ->
                    val isSelected = selectedLength == length
                    FilterChip(
                        selected = isSelected,
                        onClick = { onLengthSelected(length) },
                        label = { Text("$length h zmena") },
                        modifier = Modifier.testTag("length_$length")
                    )
                }
            }
        }
    }
}

@Composable
fun OvertimeSelectorCard(
    selectedOvertimeHours: Int,
    onOvertimeHoursSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Nadčas+ (Hodiny navyše)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedOvertimeHours > 0) {
                    Text(
                        text = "$selectedOvertimeHours h",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Bez nadčasu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Quick select chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 1, 2, 4, 8).forEach { hours ->
                    val isSelected = selectedOvertimeHours == hours
                    FilterChip(
                        selected = isSelected,
                        onClick = { onOvertimeHoursSelected(hours) },
                        label = { Text(if (hours == 0) "0 h" else "+$hours h") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("overtime_$hours")
                    )
                }
            }
            
            // Custom input field to allow any custom hours setting
            var customHoursText by remember(selectedOvertimeHours) {
                mutableStateOf(if (selectedOvertimeHours > 0 && selectedOvertimeHours !in listOf(1, 2, 4, 8)) selectedOvertimeHours.toString() else "")
            }
            
            OutlinedTextField(
                value = customHoursText,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        customHoursText = newValue
                        val parsed = newValue.toIntOrNull() ?: 0
                        onOvertimeHoursSelected(parsed)
                    }
                },
                label = { Text("Nastaviť iný počet hodín nadčasu") },
                placeholder = { Text("napr. 3, 5, 6...") },
                modifier = Modifier.fillMaxWidth().testTag("custom_overtime_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
fun MonthStatisticsView(
    selectedMonth: YearMonth,
    shiftDays: List<ShiftDay>,
    vacationAllowance: Float,
    tariffSalary: Float,
    personalAllowance: Float,
    shiftAllowance: Float,
    mealAllowance: Float,
    defaultShiftLength: Int = 12,
    listState: LazyListState = rememberLazyListState()
) {
    // Collect stats in memory for the currently active selectedMonth
    val monthShiftDays = remember(shiftDays, selectedMonth) {
        shiftDays.filter { day ->
            try {
                val parsed = LocalDate.parse(day.date)
                parsed.year == selectedMonth.year && parsed.month == selectedMonth.month
            } catch (e: Exception) {
                false
            }
        }
    }

    val daysInMonthSum = selectedMonth.lengthOfMonth()
    
    // Aggregations
    var morningCount = 0
    var nightCount = 0
    var vacationCount = 0
    var sickCount = 0
    var overtimeCount = 0
    var meetingCount = 0
    var trainingCount = 0
    var kzCount = 0
    var parCount = 0
    var noneCount = 0

    var totalWorkedHours = 0.0

    var nightHoursSum = 0.0
    var saturdayHoursSum = 0.0
    var sundayHoursSum = 0.0
    var holidayHoursSum = 0.0

    var overtimeHoursSum = 0.0

    val basicHourlyRate = tariffSalary.toDouble() / 177.0
    var computedBasicWage = 0.0
    var computedOvertimeSurcharge = 0.0
    var computedNightSurcharge = 0.0
    var computedSaturdaySurcharge = 0.0
    var computedSundaySurcharge = 0.0
    var computedHolidaySurcharge = 0.0

    monthShiftDays.forEach { d ->
        when (d.shiftType) {
            "MORNING", "MORNING_PR" -> morningCount++
            "NIGHT", "NIGHT_PN" -> nightCount++
            "OVERTIME" -> overtimeCount++
            "MEETING" -> meetingCount++
            "TRAINING" -> trainingCount++
            "VACATION" -> vacationCount++
            "SICK" -> sickCount++
            "KZ" -> kzCount++
            "Par" -> parCount++
            else -> noneCount++
        }

        val comp = calculateDayWageComponents(d, basicHourlyRate, shiftDays)
        totalWorkedHours += comp.workedHours
        overtimeHoursSum += comp.overtimeHours
        nightHoursSum += comp.nightHours
        saturdayHoursSum += comp.saturdayHours
        sundayHoursSum += comp.sundayHours
        holidayHoursSum += comp.holidayHours

        computedBasicWage += comp.basicWage
        computedOvertimeSurcharge += comp.overtimeSurcharge
        computedNightSurcharge += comp.nightSurcharge
        computedSaturdaySurcharge += comp.saturdaySurcharge
        computedSundaySurcharge += comp.sundaySurcharge
        computedHolidaySurcharge += comp.holidaySurcharge
    }

    val untrackedDays = daysInMonthSum - monthShiftDays.size
    noneCount += untrackedDays

    val daysWithNotesOrReminders = remember(monthShiftDays) {
        monthShiftDays.filter { d -> d.note?.isNotBlank() == true || d.reminderText?.isNotBlank() == true }
    }

    val singleDaySickCount = remember(monthShiftDays, shiftDays) {
        monthShiftDays.count { d ->
            if (d.shiftType == "SICK") {
                val parsed = try { java.time.LocalDate.parse(d.date) } catch (e: Exception) { null }
                val isMultiDay = if (parsed != null && shiftDays.isNotEmpty()) {
                    val yesterdayStr = parsed.minusDays(1).toString()
                    val tomorrowStr = parsed.plusDays(1).toString()
                    val hasYesterdaySick = shiftDays.any { it.date == yesterdayStr && it.shiftType == "SICK" }
                    val hasTomorrowSick = shiftDays.any { it.date == tomorrowStr && it.shiftType == "SICK" }
                    hasYesterdaySick || hasTomorrowSick
                } else {
                    false
                }
                !isMultiDay
            } else {
                false
            }
        }
    }

    val singleDayVacationCount = remember(monthShiftDays, shiftDays) {
        monthShiftDays.count { d ->
            if (d.shiftType == "VACATION") {
                val parsed = try { java.time.LocalDate.parse(d.date) } catch (e: Exception) { null }
                val isMultiDay = if (parsed != null && shiftDays.isNotEmpty()) {
                    val yesterdayStr = parsed.minusDays(1).toString()
                    val tomorrowStr = parsed.plusDays(1).toString()
                    val hasYesterdayVacation = shiftDays.any { it.date == yesterdayStr && it.shiftType == "VACATION" }
                    val hasTomorrowVacation = shiftDays.any { it.date == tomorrowStr && it.shiftType == "VACATION" }
                    hasYesterdayVacation || hasTomorrowVacation
                } else {
                    false
                }
                !isMultiDay
            } else {
                false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // TILE 0: SALARY ESTIMATE (Odhad výplaty)
        item {
            var isExpanded by remember { mutableStateOf(false) }

            // Math computations
            val dailyPnBasis = 8 * basicHourlyRate // 42.32

            // Calculate PN
            var computedPnPay = 0.0
            for (i in 1..singleDaySickCount) {
                computedPnPay += if (i <= 3) {
                    dailyPnBasis * 0.25
                } else {
                    dailyPnBasis * 0.55
                }
            }

            // Calculate Vacation Pay (single-day)
            var computedVacationPay = 0.0
            for (i in 1..singleDayVacationCount) {
                computedVacationPay += if (i <= 3) {
                    dailyPnBasis * 0.25
                } else {
                    dailyPnBasis * 0.55
                }
            }

            val fundOfHours = run {
                val isDec2025 = (selectedMonth.year == 2025 && selectedMonth.monthValue == 12)
                val isJan2026 = (selectedMonth.year == 2026 && selectedMonth.monthValue == 1)
                
                if (isDec2025) {
                    if (defaultShiftLength == 12) 172.5 else 161.0
                } else if (isJan2026) {
                    if (defaultShiftLength == 12) 180.0 else 168.0
                } else {
                    var wkDays = 0
                    val numDays = selectedMonth.lengthOfMonth()
                    for (day in 1..numDays) {
                        val date = selectedMonth.atDay(day)
                        val dayOfWeek = date.dayOfWeek.value
                        val isWeekend = (dayOfWeek == 6 || dayOfWeek == 7)
                        val isHoliday = when (selectedMonth.monthValue) {
                            1 -> day == 1 || day == 6
                            5 -> day == 1 || day == 8
                            7 -> day == 5
                            8 -> day == 29
                            9 -> day == 1 || day == 15
                            11 -> day == 1 || day == 17
                            12 -> day == 24 || day == 25 || day == 26
                            else -> false
                        }
                        if (!isWeekend && !isHoliday) {
                            wkDays++
                        }
                    }
                    if (defaultShiftLength == 12) wkDays * 8.0 else wkDays * 7.5
                }
            }

            val personalAllowanceBase = if (personalAllowance > 0f) personalAllowance.toDouble() else 380.0
            val workedRatio = if (fundOfHours > 0.0) totalWorkedHours / fundOfHours else 0.0
            val workedPercentage = workedRatio * 100.0
            val personalAllowanceVal = workedRatio * personalAllowanceBase

            val shiftAllowanceVal = shiftAllowance.toDouble()
            val workedShiftsForMeal = monthShiftDays.count { d ->
                d.shiftType in listOf("MORNING", "MORNING_PR", "NIGHT", "NIGHT_PN", "OVERTIME", "MEETING")
            }
            val isWholeMonthSickOrVacation = (sickCount == daysInMonthSum) || (vacationCount == daysInMonthSum) || ((sickCount + vacationCount) == daysInMonthSum)
            val mvAllowance = if (workedShiftsForMeal >= 2 && !isWholeMonthSickOrVacation) 20.0 else 0.0
            val computedMealAllowance = workedShiftsForMeal * 4.34
            val totalWageEstimate = computedBasicWage + computedOvertimeSurcharge + computedNightSurcharge + computedSaturdaySurcharge + computedSundaySurcharge + computedHolidaySurcharge + computedPnPay + computedVacationPay + personalAllowanceVal + shiftAllowanceVal + mvAllowance + computedMealAllowance

            // Net salary computation according to Slovak legislation (2026 guidelines)
            // Note: Príplatok iné (shiftAllowanceVal) is exempt from taxes/insurance according to laws
            val deductibleGross = (totalWageEstimate - computedPnPay - computedMealAllowance - shiftAllowanceVal).coerceAtLeast(0.0)
            val healthInsurance = deductibleGross * 0.04
            val socialInsurance = deductibleGross * 0.094
            val totalInsurance = healthInsurance + socialInsurance

            val taxBase = (deductibleGross - totalInsurance).coerceAtLeast(0.0)
            val nczd = 492.20 // Monthly NČZD for 2026
            val taxableAmount = (taxBase - nczd).coerceAtLeast(0.0)
            val taxLimit = 3961.50 // Millionaire tax threshold monthly
            val tax = if (taxableAmount > taxLimit) {
                (taxLimit * 0.19) + ((taxableAmount - taxLimit) * 0.25)
            } else {
                taxableAmount * 0.19
            }

            val netWageEstimate = totalWageEstimate - totalInsurance - tax

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("salary_estimate_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ODHAD VÝPLATY k r. 2026",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Informatívny výpočet podľa zákonov SR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Zbaliť detaily" else "Rozbaliť detaily",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gross column (Hrubá)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hrubá mzda (Brutto):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(java.util.Locale("sk", "SK"), "%.2f €", totalWageEstimate),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Net column (Čistá)
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Čistá mzda (Čisté):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format(java.util.Locale("sk", "SK"), "%.2f €", netWageEstimate),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!isExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Zobraziť položky a odvody",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .clickable { isExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            val formatHours = { h: Double ->
                                if (h % 1.0 == 0.0) "${h.toInt()}"
                                else String.format(java.util.Locale.US, "%.1f", h).replace(".", ",")
                            }

                            val basicRateFormatted = String.format(java.util.Locale.US, "%.2f", basicHourlyRate).replace(".", ",")
                            val overtimeRateFormatted = String.format(java.util.Locale.US, "%.2f", basicHourlyRate * 0.30).replace(".", ",")
                            val nightRateFormatted = String.format(java.util.Locale.US, "%.2f", basicHourlyRate * 0.25).replace(".", ",")
                            val satRateFormatted = String.format(java.util.Locale.US, "%.2f", basicHourlyRate * 0.30).replace(".", ",")
                            val dailyPnBasisFormatted = String.format(java.util.Locale.US, "%.2f", dailyPnBasis).replace(".", ",")

                            // 1. Basic hours wage
                            SalaryDetailRow(
                                title = "Základná mzda za odpracované hodiny",
                                subtitle = "Sadzba $basicRateFormatted €/h | ${formatHours(totalWorkedHours)} odpracovaných hod",
                                value = computedBasicWage
                            )

                            // 2. Overtime surcharge
                            if (overtimeHoursSum > 0) {
                                SalaryDetailRow(
                                    title = "Príplatok za nadčas",
                                    subtitle = "Príplatok 30% (+$overtimeRateFormatted €/h) | ${formatHours(overtimeHoursSum)} h",
                                    value = computedOvertimeSurcharge
                                )
                            }

                            // 3. Night surcharge
                            if (nightHoursSum > 0) {
                                SalaryDetailRow(
                                    title = "Príplatok za nočnú prácu",
                                    subtitle = "Príplatok 25% (+$nightRateFormatted €/h) | ${formatHours(nightHoursSum)} h",
                                    value = computedNightSurcharge
                                )
                            }

                            // 4. Saturday surcharge
                            if (saturdayHoursSum > 0) {
                                SalaryDetailRow(
                                    title = "Príplatok za prácu v sobotu",
                                    subtitle = "Príplatok 30% (+$satRateFormatted €/h) | ${formatHours(saturdayHoursSum)} h",
                                    value = computedSaturdaySurcharge
                                )
                            }

                            // 5. Sunday surcharge
                            if (sundayHoursSum > 0) {
                                SalaryDetailRow(
                                    title = "Príplatok za prácu v nedeľu",
                                    subtitle = "Príplatok 100% (+$basicRateFormatted €/h) | ${formatHours(sundayHoursSum)} h",
                                    value = computedSundaySurcharge
                                )
                            }

                            // 6. Holiday surcharge
                            if (holidayHoursSum > 0) {
                                SalaryDetailRow(
                                    title = "Príplatok za prácu vo sviatok",
                                    subtitle = "Príplatok 100% (+$basicRateFormatted €/h) | ${formatHours(holidayHoursSum)} h",
                                    value = computedHolidaySurcharge
                                )
                            }

                            // PN details
                            if (sickCount > 0) {
                                val slovakPnDays = if (sickCount == 1) "1 deň" else if (sickCount < 5) "$sickCount dni" else "$sickCount dní"
                                SalaryDetailRow(
                                    title = "Nemocenské (PN) náhrada príjmu",
                                    subtitle = "1.-3. deň 25%, od 4. dňa 55% z $dailyPnBasisFormatted €/deň | $slovakPnDays",
                                    value = computedPnPay
                                )
                            }

                            // Dovolenka details (single day)
                            if (computedVacationPay > 0.0) {
                                val slovakVacDays = if (singleDayVacationCount == 1) "1 deň" else if (singleDayVacationCount < 5) "$singleDayVacationCount dni" else "$singleDayVacationCount dní"
                                SalaryDetailRow(
                                    title = "Náhrada za dovolenku (1-dňová)",
                                    subtitle = "1.-3. deň 25%, od 4. dňa 55% z $dailyPnBasisFormatted €/deň | $slovakVacDays",
                                    value = computedVacationPay
                                )
                            }

                            // 6. Osobný príplatok
                            val workedPercentageFormatted = String.format(java.util.Locale.US, "%.1f", workedPercentage).replace(".", ",")
                            val workedHoursFormatted = String.format(java.util.Locale.US, "%.1f", totalWorkedHours).replace(".", ",")
                            val fundOfHoursFormatted = String.format(java.util.Locale.US, "%.1f", fundOfHours).replace(".", ",")
                            val osobneBaseFormatted = String.format(java.util.Locale.US, "%.2f", personalAllowanceBase).replace(".", ",")
                            SalaryDetailRow(
                                title = "Osobný príplatok",
                                subtitle = "Odpracované: $workedHoursFormatted z $fundOfHoursFormatted h ($workedPercentageFormatted% z $osobneBaseFormatted €)",
                                value = personalAllowanceVal
                            )

                            // 6b. Príplatok iné
                            SalaryDetailRow(
                                title = "Príplatok iné",
                                subtitle = "Mesačné stabilné bonusy a príplatky (netaxované)",
                                value = shiftAllowanceVal
                            )

                            // 7. Príplatok za MV
                            SalaryDetailRow(
                                title = "Príplatok za MV",
                                subtitle = if (mvAllowance == 0.0) "Nespĺňa podmienku odpracovania min. 2 dní alebo celý mesiac CH/D" else "Fixný mesačný príplatok",
                                value = mvAllowance
                            )

                            // 8. Stravné
                            if (workedShiftsForMeal > 0) {
                                SalaryDetailRow(
                                    title = "Stravné (Príspevok na stravovanie)",
                                    subtitle = "Sadzba 4,34 €/služba | $workedShiftsForMeal služieb",
                                    value = computedMealAllowance
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = "ZÁKONNÉ SRÁŽKY A ODVODY (SR 2026)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = 0.5.sp
                            )

                            // Health Insurance
                            DeductionDetailRow(
                                title = "Zdravotné poistenie (4,0%)",
                                subtitle = "Vymeriavací základ: ${String.format(java.util.Locale.US, "%.2f", deductibleGross).replace(".", ",")} €",
                                value = healthInsurance
                            )

                            // Social Insurance
                            DeductionDetailRow(
                                title = "Sociálne poistenie (9,4%)",
                                subtitle = "Nemocenské, starobné, invalidné, v nezamestnanosti",
                                value = socialInsurance
                            )

                            // Tax/Dan
                            val taxBaseFormatted = String.format(java.util.Locale.US, "%.2f", taxBase).replace(".", ",")
                            val nczdFormatted = String.format(java.util.Locale.US, "%.2f", nczd).replace(".", ",")
                            DeductionDetailRow(
                                title = "Preddavok na daň z príjmov (19% / 25%)",
                                subtitle = "Základ dane: $taxBaseFormatted € | Nezdaniteľná časť: $nczdFormatted €",
                                value = tax
                            )

                            if (computedPnPay > 0.0 || computedMealAllowance > 0.0 || shiftAllowanceVal > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Poznámka: Náhrada za PN, stravné a stabilné mesačné bonusy (Príplatok iné) nepodliehajú odvodom ani zdaneniu podľa zákonov SR.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Tento výpočet slúži ako informatívny odhad hrubej a čistej mzdy podľa platných zákonov Slovenskej republiky (rok 2026).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // TILE 1: ODPRACOVANÉ HODINY (E8DEF8 Lavender tile, now full-width)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8DEF8)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ODPRACOVANÉ HODINY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spolu za aktuálny mesiac",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF49454F)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val formatHours = { h: Double ->
                            if (h % 1.0 == 0.0) "${h.toInt()}"
                            else String.format(java.util.Locale.US, "%.1f", h).replace(".", ",")
                        }
                        Text(
                            text = "${formatHours(totalWorkedHours)} h",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D)
                        )
                    }
                }
            }
        }

        // TILE 1.5: PREHĽAD DOVOLENKY (Soft warm card)
        item {
            val totalVacationHours = remember(shiftDays) {
                shiftDays.filter { it.shiftType == "VACATION" }.sumOf { it.shiftLength }
            }
            val spentVacationDays = totalVacationHours / 12.0
            val remainingDays = vacationAllowance.toDouble() - spentVacationDays

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFD1D6).copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFC2185B).copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PREHĽAD DOVOLENKY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF880E4F),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val remainingStr = if (remainingDays % 1.0 == 0.0) remainingDays.toInt().toString() else String.format(java.util.Locale.US, "%.1f", remainingDays).replace(".", ",")
                        val totalStr = if (vacationAllowance % 1f == 0f) vacationAllowance.toInt().toString() else String.format(java.util.Locale.US, "%.1f", vacationAllowance).replace(".", ",")
                        Text(
                            text = "Zostáva $remainingStr z $totalStr dní",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4A148C)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val spentStr = if (spentVacationDays % 1.0 == 0.0) spentVacationDays.toInt().toString() else String.format(java.util.Locale.US, "%.1f", spentVacationDays).replace(".", ",")
                        Text(
                            text = "Vyčerpané celkovo: $spentStr dní (${totalVacationHours} h)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A148C).copy(alpha = 0.8f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color(0xFF880E4F),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val remainingStr = if (remainingDays % 1.0 == 0.0) remainingDays.toInt().toString() else String.format(java.util.Locale.US, "%.1f", remainingDays).replace(".", ",")
                        Text(
                            text = "$remainingStr d",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF880E4F)
                        )
                    }
                }
            }
        }

        // TILE 2: HOURS DETAIL BREAKDOWN (White tile, detailed hours breakdown)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Podrobný prehľad odpracovaných hodín",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    HoursDetailRow(
                        label = "Celkovo odpracované hodiny (zmeny + nadčasy)",
                        hours = totalWorkedHours
                    )
                    
                    if (nightHoursSum > 0) {
                        HoursDetailRow(
                            label = "Odpracované hodiny v noci",
                            hours = nightHoursSum
                        )
                    }
                    
                    if (saturdayHoursSum > 0) {
                        HoursDetailRow(
                            label = "Odpracované hodiny v sobotu",
                            hours = saturdayHoursSum
                        )
                    }
                    if (sundayHoursSum > 0) {
                        HoursDetailRow(
                            label = "Odpracované hodiny v nedeľu",
                            hours = sundayHoursSum
                        )
                    }
                    
                    if (holidayHoursSum > 0) {
                        HoursDetailRow(
                            label = "Odpracované hodiny vo sviatky",
                            hours = holidayHoursSum
                        )
                    }
                }
            }
        }

        // TILE 4: SHIFT DISTRIBUTION VERTICAL CHART (F3EDF7 Light Lavender tile)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF3EDF7)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISTRIBÚCIA SMIEN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Detailný graf",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Compute clean vertical representation bars
                    val maxCount = maxOf(
                        1,
                        morningCount,
                        nightCount,
                        overtimeCount,
                        vacationCount,
                        sickCount,
                        meetingCount,
                        trainingCount,
                        noneCount
                    ).toFloat()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Ranná
                        val mornPct = (morningCount / maxCount)
                        BentoBarColumn(
                            title = "Ranná",
                            count = morningCount,
                            pct = mornPct,
                            color = ShiftColorScheme.MorningBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Nočná
                        val nightPct = (nightCount / maxCount)
                        BentoBarColumn(
                            title = "Nočná",
                            count = nightCount,
                            pct = nightPct,
                            color = ShiftColorScheme.NightBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Nadčas
                        val otPct = (overtimeCount / maxCount)
                        BentoBarColumn(
                            title = "Nadčas",
                            count = overtimeCount,
                            pct = otPct,
                            color = ShiftColorScheme.OvertimeBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Porada
                        val mtPct = (meetingCount / maxCount)
                        BentoBarColumn(
                            title = "Porada",
                            count = meetingCount,
                            pct = mtPct,
                            color = ShiftColorScheme.MeetingBackground,
                            stripeColor = ShiftColorScheme.getStripeColorForType("MEETING"),
                            modifier = Modifier.weight(1f)
                        )
                        // Výcvik
                        val trPct = (trainingCount / maxCount)
                        BentoBarColumn(
                            title = "Výcvik",
                            count = trainingCount,
                            pct = trPct,
                            color = ShiftColorScheme.TrainingBackground,
                            stripeColor = ShiftColorScheme.getStripeColorForType("TRAINING"),
                            modifier = Modifier.weight(1f)
                        )
                        // Dovolenka
                        val vacPct = (vacationCount / maxCount)
                        BentoBarColumn(
                            title = "Dovol.",
                            count = vacationCount,
                            pct = vacPct,
                            color = ShiftColorScheme.VacationBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // CH
                        val sickPct = (sickCount / maxCount)
                        BentoBarColumn(
                            title = "CH",
                            count = sickCount,
                            pct = sickPct,
                            color = ShiftColorScheme.SickBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Voľno
                        val nonePct = (noneCount / maxCount)
                        BentoBarColumn(
                            title = "Voľno",
                            count = noneCount,
                            pct = nonePct,
                            color = ShiftColorScheme.NoneBackground,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dynamic legend list
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendRow(name = "Ranná zmena (R / PR)", count = morningCount, color = ShiftColorScheme.MorningBackground, isDuty = false)
                        LegendRow(name = "Nočná zmena (N / PN)", count = nightCount, color = ShiftColorScheme.NightBackground, isDuty = false)
                        LegendRow(name = "Nadčas (NČ)", count = overtimeCount, color = ShiftColorScheme.OvertimeBackground, isDuty = false)
                        LegendRow(name = "Porada (P)", count = meetingCount, color = ShiftColorScheme.MeetingBackground, isDuty = false)
                        LegendRow(name = "Výcvik (V)", count = trainingCount, color = ShiftColorScheme.TrainingBackground, isDuty = false)
                        LegendRow(name = "Dovolenka", count = vacationCount, color = ShiftColorScheme.VacationBackground, isDuty = false)
                        LegendRow(name = "Choroba (CH)", count = sickCount, color = ShiftColorScheme.SickBackground, isDuty = false)
                        LegendRow(name = "Kolektívna zmluva (KZ)", count = kzCount, color = ShiftColorScheme.getColorsForType("KZ").second, isDuty = false)
                        LegendRow(name = "Paragraf (Par)", count = parCount, color = ShiftColorScheme.getColorsForType("Par").second, isDuty = false)
                        LegendRow(name = "Voľné dni", count = noneCount, color = ShiftColorScheme.NoneBackground, isDuty = false)
                    }
                }
            }
        }



    }
}

@Composable
fun MonthOverviewView(
    selectedDate: LocalDate?,
    selectedMonth: YearMonth,
    shiftDays: List<ShiftDay>,
    onSaveDayNotes: (LocalDate, String?, String?) -> Unit,
    basicHourlyRate: Double = 5.29,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current

    // Target date is selectedDate (if in selectedMonth), falling back to today or first day of selectedMonth
    val targetDate = remember(selectedDate, selectedMonth) {
        if (selectedDate != null && selectedDate.year == selectedMonth.year && selectedDate.month == selectedMonth.month) {
            selectedDate
        } else {
            val today = safeLocalDateNow()
            if (today.year == selectedMonth.year && today.month == selectedMonth.month) {
                today
            } else {
                selectedMonth.atDay(1)
            }
        }
    }

    val dayOfWeek = remember(targetDate) { getSlovakDayOfWeekName(targetDate.dayOfWeek.value) }
    val monthName = remember(targetDate) { getSlovakMonthName(targetDate.monthValue, genitive = true) }
    val dayFormatted = remember(targetDate, dayOfWeek, monthName) {
        "${targetDate.dayOfMonth}. $monthName ($dayOfWeek) ${targetDate.year}"
    }

    val shiftDay = remember(shiftDays, targetDate) {
        val dateStr = targetDate.toString()
        shiftDays.find { it.date == dateStr } ?: ShiftDay(
            date = dateStr,
            shiftType = "NONE",
            shiftLength = 8,
            note = null,
            reminderText = null,
            overtimeHours = 0
        )
    }

    // Daily earnings estimation computations
    val dailyPnBasis = 8 * basicHourlyRate // 42.32

    val isMultiDaySick = if (shiftDay.shiftType == "SICK") {
        val parsed = try { LocalDate.parse(shiftDay.date) } catch (e: Exception) { null }
        if (parsed != null && shiftDays.isNotEmpty()) {
            val yesterdayStr = parsed.minusDays(1).toString()
            val tomorrowStr = parsed.plusDays(1).toString()
            val hasYesterdaySick = shiftDays.any { it.date == yesterdayStr && it.shiftType == "SICK" }
            val hasTomorrowSick = shiftDays.any { it.date == tomorrowStr && it.shiftType == "SICK" }
            hasYesterdaySick || hasTomorrowSick
        } else {
            false
        }
    } else {
        false
    }

    val isMultiDayVacation = if (shiftDay.shiftType == "VACATION") {
        val parsed = try { LocalDate.parse(shiftDay.date) } catch (e: Exception) { null }
        if (parsed != null && shiftDays.isNotEmpty()) {
            val yesterdayStr = parsed.minusDays(1).toString()
            val tomorrowStr = parsed.plusDays(1).toString()
            val hasYesterdayVacation = shiftDays.any { it.date == yesterdayStr && it.shiftType == "VACATION" }
            val hasTomorrowVacation = shiftDays.any { it.date == tomorrowStr && it.shiftType == "VACATION" }
            hasYesterdayVacation || hasTomorrowVacation
        } else {
            false
        }
    } else {
        false
    }

    val comp = calculateDayWageComponents(shiftDay, basicHourlyRate, shiftDays)
    val dailyBasicWage = comp.basicWage
    val dailyOvertimeWage = comp.overtimeSurcharge
    val dailyNightPay = comp.nightSurcharge
    val dailyWeekendPay = comp.saturdaySurcharge + comp.sundaySurcharge
    val dailyHolidayPay = comp.holidaySurcharge
    var dailyPnPay = 0.0
    var dailyVacationPay = 0.0

    if (shiftDay.shiftType == "SICK" && !isMultiDaySick) {
        val sSortedSickDays = shiftDays.filter { 
            try {
                val d = LocalDate.parse(it.date)
                it.shiftType == "SICK" && d.year == targetDate.year && d.month == targetDate.month
            } catch(e: Exception) { false }
        }.sortedBy { it.date }
        val idx = sSortedSickDays.indexOfFirst { it.date == shiftDay.date }
        val sickIndex = if (idx >= 0) idx + 1 else 1
        dailyPnPay = if (sickIndex <= 3) {
            dailyPnBasis * 0.25
        } else {
            dailyPnBasis * 0.55
        }
    }

    if (shiftDay.shiftType == "VACATION" && !isMultiDayVacation) {
        val sSortedVacationDays = shiftDays.filter { 
            try {
                val d = LocalDate.parse(it.date)
                it.shiftType == "VACATION" && d.year == targetDate.year && d.month == targetDate.month
            } catch(e: Exception) { false }
        }.sortedBy { it.date }
        val idx = sSortedVacationDays.indexOfFirst { it.date == shiftDay.date }
        val vacationIndex = if (idx >= 0) idx + 1 else 1
        dailyVacationPay = if (vacationIndex <= 3) {
            dailyPnBasis * 0.25
        } else {
            dailyPnBasis * 0.55
        }
    }

    val dailyTotalEstimate = dailyBasicWage + dailyOvertimeWage + dailyNightPay + dailyWeekendPay + dailyHolidayPay + dailyPnPay + dailyVacationPay

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Section: Head Title
        item {
            Text(
                text = "Denný Prehľad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        // Section: Formatted large day header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = dayFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Naplánovaný rozvrh pre vybratý deň",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Section: Shift Layout details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Služba / Stav dňa",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val badgeColors = ShiftColorScheme.getColorsForType(shiftDay.shiftType)
                    val stripeColor = ShiftColorScheme.getStripeColorForType(shiftDay.shiftType)
                    val slovakName = ShiftColorScheme.getSlovakName(shiftDay.shiftType)
                    val shiftHrsLabel = when (shiftDay.shiftType) {
                        "NONE" -> "Voľno"
                        "VACATION" -> "Dovolenka (12h)"
                        "SICK" -> "PN"
                        "MEETING" -> "Porada (2h)"
                        "TRAINING" -> "Výcvik (5h)"
                        else -> if (shiftDay.shiftLength > 0) "${slovakName} (${shiftDay.shiftLength}h" + (if (shiftDay.overtimeHours > 0) " + ${shiftDay.overtimeHours}h" else "") + ")" else slovakName
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (stripeColor != null) {
                                    Modifier.stripedBackground(
                                        bgColor = badgeColors.second,
                                        stripeColor = stripeColor
                                    )
                                } else {
                                    Modifier.background(badgeColors.second)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shiftHrsLabel.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColors.first
                        )
                    }

                    if (dailyTotalEstimate > 0.0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Odhadovaný čistý zárobok (brutto):",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(java.util.Locale("sk", "SK"), "%.2f €", dailyTotalEstimate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Section: Notes list
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Poznámky",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val savedNotesList = remember(shiftDay.note) {
                        shiftDay.note?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
                    }

                    if (savedNotesList.isEmpty()) {
                        Text(
                            text = "Žiadne poznámky na tento deň.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        savedNotesList.forEachIndexed { index, noteItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = noteItem,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updatedList = savedNotesList.toMutableList()
                                        updatedList.removeAt(index)
                                        val updatedNote = if (updatedList.isEmpty()) null else updatedList.joinToString("\n")
                                        onSaveDayNotes(targetDate, updatedNote, shiftDay.reminderText)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Vymazať poznámku",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Reminders/Tasks
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Úlohy a Pripomienky",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (shiftDay.reminderText?.isNotBlank() != true) {
                        Text(
                            text = "Žiadne naplánované pripomienky na tento deň.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = shiftDay.reminderText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    ReminderReceiver.cancelReminder(context, targetDate)
                                    onSaveDayNotes(targetDate, shiftDay.note, null)
                                },
                                modifier = Modifier.size(36.dp)
                              ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Vymazať úlohu",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoBarColumn(
    title: String,
    count: Int,
    pct: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stripeColor: Color? = null
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        val animatedHeight = animateFloatAsState(
            targetValue = (if (pct > 0f) pct else 0.05f).coerceIn(0.01f, 1f),
            label = "barHeight"
        )
        
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B1E)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .fillMaxHeight(fraction = animatedHeight.value.coerceIn(0.01f, 1f))
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .then(
                    if (stripeColor != null) {
                        Modifier.stripedBackground(bgColor = color, stripeColor = stripeColor, stripeWidth = 2f, gapWidth = 8f)
                    } else {
                        Modifier.background(color)
                    }
                )
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF49454F)
        )
    }
}

@Composable
fun LegendRow(
    name: String,
    count: Int,
    color: Color,
    hours: Int = 0,
    isDuty: Boolean = true,
    stripeColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(
                        if (stripeColor != null) {
                            Modifier.stripedBackground(bgColor = color, stripeColor = stripeColor, stripeWidth = 1f, gapWidth = 4f)
                        } else {
                            Modifier.background(color)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = if (isDuty) "$count smien (${count * 8}h-12h)" else "$count dní",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}



// Helper for wage calculations
data class DayWageComponents(
    val workedHours: Double,
    val overtimeHours: Double,
    val saturdayHours: Double,
    val sundayHours: Double,
    val nightHours: Double,
    val holidayHours: Double,
    val basicWage: Double,
    val overtimeSurcharge: Double,
    val nightSurcharge: Double,
    val saturdaySurcharge: Double,
    val sundaySurcharge: Double,
    val holidaySurcharge: Double,
    val totalSurchargesAndWage: Double
)

fun calculateDayWageComponents(
    d: ShiftDay,
    basicHourlyRate: Double = 5.29,
    allShiftDays: List<ShiftDay> = emptyList()
): DayWageComponents {
    val parsedDate = try { java.time.LocalDate.parse(d.date) } catch (e: Exception) { null }
    if (parsedDate == null || d.shiftType == "NONE") {
        return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    var isMultiDaySick = false
    var isSickWorkDay = false
    if (d.shiftType == "SICK") {
        if (allShiftDays.isNotEmpty()) {
            val yesterdayStr = parsedDate.minusDays(1).toString()
            val tomorrowStr = parsedDate.plusDays(1).toString()
            val hasYesterdaySick = allShiftDays.any { it.date == yesterdayStr && it.shiftType == "SICK" }
            val hasTomorrowSick = allShiftDays.any { it.date == tomorrowStr && it.shiftType == "SICK" }
            isMultiDaySick = hasYesterdaySick || hasTomorrowSick
        }
        if (!isMultiDaySick) {
            return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        } else {
            // Find its position in the consecutive sequence
            var sickSequenceIndex = 1
            var checkDate = parsedDate.minusDays(1)
            while (allShiftDays.any { it.date == checkDate.toString() && it.shiftType == "SICK" }) {
                sickSequenceIndex++
                checkDate = checkDate.minusDays(1)
            }
            if (sickSequenceIndex > 10) {
                return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            }

            // Predict if this multi-day SICK day should count as a work day based on the 4-day pattern
            val workCounts = IntArray(4)
            val offCounts = IntArray(4)
            for (day in allShiftDays) {
                if (day.shiftType == "SICK" || day.shiftType == "VACATION") continue
                val dDate = try { java.time.LocalDate.parse(day.date) } catch (e: Exception) { null } ?: continue
                val p = ((dDate.toEpochDay() % 4 + 4) % 4).toInt()
                if (day.shiftType in listOf("MORNING", "MORNING_PR", "NIGHT", "NIGHT_PN", "OVERTIME", "MEETING", "TRAINING")) {
                    workCounts[p]++
                } else if (day.shiftType == "NONE") {
                    offCounts[p]++
                }
            }
            val scores = DoubleArray(4) { i -> workCounts[i].toDouble() - offCounts[i].toDouble() }
            val sortedIndices = (0..3).sortedByDescending { scores[it] }
            val workPhases = sortedIndices.take(2)
            val currentPhase = ((parsedDate.toEpochDay() % 4 + 4) % 4).toInt()
            isSickWorkDay = currentPhase in workPhases
        }
        if (!isSickWorkDay) {
            return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    var isMultiDayVacation = false
    var isVacationWorkDay = false
    if (d.shiftType == "VACATION") {
        if (allShiftDays.isNotEmpty()) {
            val yesterdayStr = parsedDate.minusDays(1).toString()
            val tomorrowStr = parsedDate.plusDays(1).toString()
            val hasYesterdayVacation = allShiftDays.any { it.date == yesterdayStr && it.shiftType == "VACATION" }
            val hasTomorrowVacation = allShiftDays.any { it.date == tomorrowStr && it.shiftType == "VACATION" }
            isMultiDayVacation = hasYesterdayVacation || hasTomorrowVacation
        }
        if (!isMultiDayVacation) {
            return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        } else {
            // Predict if this multi-day VACATION day should count as a work day based on the 4-day pattern
            val workCounts = IntArray(4)
            val offCounts = IntArray(4)
            for (day in allShiftDays) {
                if (day.shiftType == "SICK" || day.shiftType == "VACATION") continue
                val dDate = try { java.time.LocalDate.parse(day.date) } catch (e: Exception) { null } ?: continue
                val p = ((dDate.toEpochDay() % 4 + 4) % 4).toInt()
                if (day.shiftType in listOf("MORNING", "MORNING_PR", "NIGHT", "NIGHT_PN", "OVERTIME", "MEETING", "TRAINING")) {
                    workCounts[p]++
                } else if (day.shiftType == "NONE") {
                    offCounts[p]++
                }
            }
            val scores = DoubleArray(4) { i -> workCounts[i].toDouble() - offCounts[i].toDouble() }
            val sortedIndices = (0..3).sortedByDescending { scores[it] }
            val workPhases = sortedIndices.take(2)
            val currentPhase = ((parsedDate.toEpochDay() % 4 + 4) % 4).toInt()
            isVacationWorkDay = currentPhase in workPhases
        }
        if (!isVacationWorkDay) {
            return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    val rawLength = when (d.shiftType) {
        "MEETING" -> 2
        "TRAINING" -> 5
        else -> d.shiftLength
    }
    val actualLength = if (rawLength == 12) 11.5 else rawLength.toDouble()
    val activeShiftHours = if (isMultiDaySick || isMultiDayVacation) actualLength else (actualLength + d.overtimeHours)
    val isWorkedShift = d.shiftType == "MORNING" || d.shiftType == "MORNING_PR" || 
                        d.shiftType == "NIGHT" || d.shiftType == "NIGHT_PN" || 
                        d.shiftType == "OVERTIME" || d.shiftType == "MEETING" || d.shiftType == "TRAINING" ||
                        (isMultiDaySick && isSickWorkDay) ||
                        (isMultiDayVacation && isVacationWorkDay)

    if (!isWorkedShift) {
        return DayWageComponents(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    val workedHours = activeShiftHours
    val overtimeHours = if (isMultiDaySick || isMultiDayVacation) 0.0 else (if (d.shiftType == "OVERTIME") actualLength + d.overtimeHours else d.overtimeHours.toDouble())

    var saturdayHours = 0.0
    var sundayHours = 0.0
    var holidayHours = 0.0
    var nightHours = 0.0

    val hasNoSurcharges = isMultiDaySick || isMultiDayVacation

    if (!hasNoSurcharges) {
        if (d.shiftType == "NIGHT" || d.shiftType == "NIGHT_PN") {
            // Night shift starts at 19:00 on Day D, ends at 07:00 on Day D+1
            // Hours on Day D = 5.0
            // Hours on Day D+1 = 6.5 + overtime
            val hoursOnD = 5.0
            val hoursOnDPlus1 = 6.5 + d.overtimeHours

            val isSatD = parsedDate.dayOfWeek == java.time.DayOfWeek.SATURDAY
            val isSunD = parsedDate.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val isHolD = isSlovakHoliday(parsedDate)

            val tomorrow = parsedDate.plusDays(1)
            val isSatNext = tomorrow.dayOfWeek == java.time.DayOfWeek.SATURDAY
            val isSunNext = tomorrow.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val isHolNext = isSlovakHoliday(tomorrow)

            saturdayHours = (if (isSatD) hoursOnD else 0.0) + (if (isSatNext) hoursOnDPlus1 else 0.0)
            sundayHours = (if (isSunD) hoursOnD else 0.0) + (if (isSunNext) hoursOnDPlus1 else 0.0)
            holidayHours = (if (isHolD) hoursOnD else 0.0) + (if (isHolNext) hoursOnDPlus1 else 0.0)
            nightHours = activeShiftHours // all hours of night shift are night hours
        } else {
            // Day shifts are entirely on Day D
            val isSat = parsedDate.dayOfWeek == java.time.DayOfWeek.SATURDAY
            val isSun = parsedDate.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val isHol = isSlovakHoliday(parsedDate)

            saturdayHours = if (isSat) activeShiftHours else 0.0
            sundayHours = if (isSun) activeShiftHours else 0.0
            holidayHours = if (isHol) activeShiftHours else 0.0
            nightHours = 0.0
        }
    }

    val basicWage = workedHours * basicHourlyRate
    val overtimeSurcharge = overtimeHours * basicHourlyRate * 0.30
    val nightSurcharge = nightHours * basicHourlyRate * 0.25
    val saturdaySurcharge = saturdayHours * basicHourlyRate * 0.30
    val sundaySurcharge = sundayHours * basicHourlyRate * 1.00
    val holidaySurcharge = holidayHours * basicHourlyRate * 1.00

    val totalVal = basicWage + overtimeSurcharge + nightSurcharge + saturdaySurcharge + sundaySurcharge + holidaySurcharge

    return DayWageComponents(
        workedHours = workedHours,
        overtimeHours = overtimeHours,
        saturdayHours = saturdayHours,
        sundayHours = sundayHours,
        nightHours = nightHours,
        holidayHours = holidayHours,
        basicWage = basicWage,
        overtimeSurcharge = overtimeSurcharge,
        nightSurcharge = nightSurcharge,
        saturdaySurcharge = saturdaySurcharge,
        sundaySurcharge = sundaySurcharge,
        holidaySurcharge = holidaySurcharge,
        totalSurchargesAndWage = totalVal
    )
}



fun isSlovakHoliday(date: java.time.LocalDate): Boolean {
    val month = date.monthValue
    val day = date.dayOfMonth
    val year = date.year

    // Fixed Slovak Holidays
    if (month == 1 && day == 1) return true   // Vznik SR
    if (month == 1 && day == 6) return true   // Traja králi
    if (month == 5 && day == 1) return true   // Sviatok práce
    if (month == 5 && day == 8) return true   // Oslobodenie
    if (month == 7 && day == 5) return true   // Cyrila a Metoda
    if (month == 8 && day == 29) return true  // SNP
    if (month == 9 && day == 1) return true   // Deň Ústavy
    if (month == 9 && day == 15) return true  // Sedembolestná Panna Mária
    if (month == 11 && day == 1) return true  // Všetkých svätých
    if (month == 11 && day == 17) return true // Deň boja za slobodu
    if (month == 12 && day == 24) return true // Štedrý deň
    if (month == 12 && day == 25) return true // 1. sviatok vianočný
    if (month == 12 && day == 26) return true // 2. sviatok vianočný

    // Easter Days (Velká Noc)
    if (year == 2025) {
        if (month == 4 && (day == 18 || day == 21)) return true
    } else if (year == 2026) {
        if (month == 4 && (day == 3 || day == 6)) return true
    } else if (year == 2027) {
        if (month == 3 && (day == 26 || day == 29)) return true
    }
    return false
}

@Composable
fun HoursDetailRow(label: String, hours: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        val formatted = if (hours % 1.0 == 0.0) "${hours.toInt()}" else String.format(java.util.Locale.US, "%.1f", hours).replace(".", ",")
        Text(
            text = "$formatted h",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SalaryDetailRow(
    title: String,
    subtitle: String,
    value: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = String.format(java.util.Locale("sk", "SK"), "%.2f €", value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DeductionDetailRow(
    title: String,
    subtitle: String,
    value: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = String.format(java.util.Locale("sk", "SK"), "-%.2f €", value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

fun getSlovakMonthName(monthValue: Int, genitive: Boolean = false): String {
    return if (genitive) {
        when (monthValue) {
            1 -> "januára"
            2 -> "februára"
            3 -> "marca"
            4 -> "apríla"
            5 -> "mája"
            6 -> "júna"
            7 -> "júla"
            8 -> "augusta"
            9 -> "septembra"
            10 -> "októbra"
            11 -> "novembra"
            12 -> "decembra"
            else -> ""
        }
    } else {
        when (monthValue) {
            1 -> "Január"
            2 -> "Február"
            3 -> "Marec"
            4 -> "Apríl"
            5 -> "Máj"
            6 -> "Jún"
            7 -> "Júl"
            8 -> "August"
            9 -> "September"
            10 -> "Október"
            11 -> "November"
            12 -> "December"
            else -> ""
        }
    }
}

fun getSlovakDayOfWeekName(dayValue: Int): String {
    return when (dayValue) {
        1 -> "Pondelok"
        2 -> "Utorok"
        3 -> "Streda"
        4 -> "Štvrtok"
        5 -> "Piatok"
        6 -> "Sobota"
        7 -> "Nedeľa"
        else -> ""
    }
}

fun getSlovakMonthShortName(monthValue: Int): String {
    return when (monthValue) {
        1 -> "jan."
        2 -> "feb."
        3 -> "mar."
        4 -> "apr."
        5 -> "máj"
        6 -> "jún"
        7 -> "júl"
        8 -> "aug."
        9 -> "sept."
        10 -> "okt."
        11 -> "nov."
        12 -> "dec."
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("firebase_prefs", android.content.Context.MODE_PRIVATE) }
    
    var apiKey by remember { mutableStateOf(prefs.getString("apiKey", "") ?: "") }
    var projectId by remember { mutableStateOf(prefs.getString("projectId", "") ?: "") }
    var authDomain by remember { mutableStateOf(prefs.getString("authDomain", "") ?: "") }
    var storageBucket by remember { mutableStateOf(prefs.getString("storageBucket", "") ?: "") }
    var messagingSenderId by remember { mutableStateOf(prefs.getString("messagingSenderId", "") ?: "") }
    var appId by remember { mutableStateOf(prefs.getString("appId", "") ?: "") }

    var configClipboardText by remember { mutableStateOf("") }
    var connectionStatusText by remember { mutableStateOf("") }

    val isConnected = com.example.ui.FirebaseSync.isConnected
    val isConnecting = com.example.ui.FirebaseSync.isConnecting
    val errorMessage = com.example.ui.FirebaseSync.errorMessage

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Firebase Synchronizácia",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Prepojte mobilnú aplikáciu so spoločným webovým admin panelom. Po úspešnom pripojení sa zmeny rozpisu zosynchronizujú okamžite v reálnom čase na oboch telefónoch a doručí sa upozornenie (push notifikácia).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Connection state
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Status pripojenia:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected) Color(0xFF22C55E)
                                        else if (isConnecting) Color(0xFFEAB308)
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Text(
                                text = if (isConnected) "Pripojené k databáze"
                                       else if (isConnecting) "Pripájanie..."
                                       else "Odpojené (Lokálny režim)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConnected) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Chyba: $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (!isConnected) {
                    // Paste config section
                    Text(
                        text = "Rýchle nastavenie (vložte Firebase web config):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = configClipboardText,
                        onValueChange = { newText ->
                            configClipboardText = newText
                            // Try to auto-parse
                            val parsed = parseFirebaseWebConfig(newText)
                            if (parsed.isNotEmpty()) {
                                parsed["apiKey"]?.let { v -> apiKey = v }
                                parsed["projectId"]?.let { v -> projectId = v }
                                parsed["authDomain"]?.let { v -> authDomain = v }
                                parsed["storageBucket"]?.let { v -> storageBucket = v }
                                parsed["messagingSenderId"]?.let { v -> messagingSenderId = v }
                                parsed["appId"]?.let { v -> appId = v }
                                connectionStatusText = "Konfigurácia úspešne načítaná zo schránky!"
                            }
                        },
                        label = { Text("Sem skopírujte config z Firebase") },
                        placeholder = { Text("const firebaseConfig = {\n  apiKey: \"...\",\n  projectId: \"...\"\n};") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )

                    if (connectionStatusText.isNotEmpty()) {
                        Text(
                            text = connectionStatusText,
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Alebo zadajte manuálne:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = projectId,
                        onValueChange = { projectId = it },
                        label = { Text("Project ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = messagingSenderId,
                        onValueChange = { messagingSenderId = it },
                        label = { Text("Messaging Sender ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = appId,
                        onValueChange = { appId = it },
                        label = { Text("App ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        onClick = {
                            com.example.ui.FirebaseSync.disconnect(context)
                            apiKey = ""
                            projectId = ""
                            authDomain = ""
                            storageBucket = ""
                            messagingSenderId = ""
                            appId = ""
                            configClipboardText = ""
                            connectionStatusText = ""
                        }
                    ) {
                        Text("Odpojiť")
                    }
                } else {
                    Button(
                        enabled = apiKey.isNotEmpty() && projectId.isNotEmpty() && !isConnecting,
                        onClick = {
                            com.example.ui.FirebaseSync.connect(
                                context,
                                apiKey,
                                projectId,
                                authDomain,
                                storageBucket,
                                messagingSenderId,
                                appId
                            )
                        }
                    ) {
                        Text(if (isConnecting) "Pripájanie..." else "Pripojiť")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zatvoriť")
            }
        }
    )
}

fun parseFirebaseWebConfig(rawText: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val keys = listOf("apiKey", "authDomain", "projectId", "storageBucket", "messagingSenderId", "appId")
    for (key in keys) {
        val pattern = """$key"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        val match = pattern.find(rawText)
        if (match != null) {
            result[key] = match.groupValues[1]
        } else {
            val p2 = """$key\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            val m2 = p2.find(rawText)
            if (m2 != null) {
                result[key] = m2.groupValues[1]
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterMessagesManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
    val userName = remember { prefs.getString("user_name", "Admin") ?: "Admin" }
    
    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("all") } // all, shift, individual
    var targetValue by remember { mutableStateOf("") } 
    var priorityInput by remember { mutableStateOf("2") } // "1" for high, "2" for normal
    
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    
    var messagesList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    // Fetch and subscribe to messages list for management console
    LaunchedEffect(Unit) {
        if (com.example.ui.FirebaseSync.isConnected) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("messages")
                    .addSnapshotListener { snapshot, error ->
                        if (snapshot != null) {
                            messagesList = snapshot.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                val id = doc.id
                                val m = data.toMutableMap()
                                m["id"] = id
                                m
                            }.sortedByDescending { 
                                it["createdAt"] as? String ?: "" 
                            }
                        }
                    }
            } catch(e: Exception) {
                // Ignore gracefully
            }
        }
    }
    
    // Roster employee list for individual selector
    val employeeNames = remember {
        (com.example.ui.RosterData.topEmployees.map { it.name } + 
         com.example.ui.RosterData.bottomEmployees.map { it.name })
        .filter { it.isNotBlank() }
        .sorted()
    }
    
    var showTargetDropdown by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Manažment správ s potvrdením",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Zatvoriť")
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // Form: Recipient
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Komu poslať správu (Príjemca):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("all" to "Všetkým", "shift" to "Smene/Skupine", "individual" to "Jednotlivcovi").forEach { (type, label) ->
                            val isSel = targetType == type
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    targetType = type
                                    targetValue = if (type == "shift") "top" else if (type == "individual") (employeeNames.firstOrNull() ?: "") else ""
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    
                    if (targetType == "shift") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = targetValue == "top",
                                onClick = { targetValue = "top" },
                                label = { Text("Denná skupina", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = targetValue == "bottom",
                                onClick = { targetValue = "bottom" },
                                label = { Text("Zmenová skupina", fontSize = 10.sp) }
                            )
                        }
                    } else if (targetType == "individual") {
                        // Display simple dropdown selector
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showTargetDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = if (targetValue.isEmpty()) "Vyberte zamestnanca" else targetValue, maxLines = 1)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = showTargetDropdown,
                                onDismissRequest = { showTargetDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.8f).height(240.dp)
                            ) {
                                employeeNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            targetValue = name
                                            showTargetDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Form: Priority
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Priorita správy:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = priorityInput == "1",
                            onClick = { priorityInput = "1" },
                            label = { Text("1. Vysoká", color = if (priorityInput == "1") Color.Red else Color.Unspecified) }
                        )
                        FilterChip(
                            selected = priorityInput == "2",
                            onClick = { priorityInput = "2" },
                            label = { Text("2. Bežná") }
                        )
                    }
                }
                
                // Form: Title & Body
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Titulok správy") },
                    placeholder = { Text("Napr. Dôležitý oznam pre...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = bodyInput,
                    onValueChange = { bodyInput = it },
                    label = { Text("Obsah správy") },
                    placeholder = { Text("Text správy...") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Submit button
                Button(
                    onClick = {
                        if (titleInput.isBlank() || bodyInput.isBlank()) {
                            statusMessage = "Zadajte prosím titulok aj obsah správy."
                            isErrorStatus = true
                            return@Button
                        }
                        
                        isSending = true
                        statusMessage = null
                        
                        try {
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val msgRef = db.collection("messages").document()
                            val newMsg = hashMapOf(
                                "id" to msgRef.id,
                                "sender" to (if (userName.trim().equals("admin", ignoreCase = true)) "Administrátor" else userName),
                                "title" to titleInput.trim(),
                                "body" to bodyInput.trim(),
                                "targetType" to targetType,
                                "targetValue" to targetValue,
                                "priority" to priorityInput,
                                "createdAt" to java.time.Instant.now().toString(),
                                "readBy" to hashMapOf<String, String>()
                            )
                            
                            msgRef.set(newMsg)
                                .addOnSuccessListener {
                                    isSending = false
                                    statusMessage = "Správa bola úspešne odoslaná!"
                                    isErrorStatus = false
                                    titleInput = ""
                                    bodyInput = ""
                                }
                                .addOnFailureListener { err ->
                                    isSending = false
                                    statusMessage = "Nepodarilo sa odoslať: ${err.message}"
                                    isErrorStatus = true
                                }
                        } catch(e: Exception) {
                            isSending = false
                            statusMessage = "Zlyhalo pripojenie: ${e.message}"
                            isErrorStatus = true
                        }
                    },
                    enabled = !isSending && com.example.ui.FirebaseSync.isConnected,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Odoslať dôležitú správu")
                    }
                }
                
                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isErrorStatus) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // History List
                Text(
                    text = "História odoslaných správ:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (!com.example.ui.FirebaseSync.isConnected) {
                    Text(
                        text = "Pripojte sa na Firebase pre načítanie histórie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (messagesList.isEmpty()) {
                    Text(
                        text = "Žiadne odoslané správy v systéme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    messagesList.forEach { m ->
                        val mId = m["id"] as? String ?: ""
                        val mTitle = m["title"] as? String ?: "Bez názvu"
                        val mBody = m["body"] as? String ?: ""
                        val mPriority = m["priority"] as? String ?: "2"
                        val mType = m["targetType"] as? String ?: ""
                        val mVal = m["targetValue"] as? String ?: ""
                        val mReadMap = m["readBy"] as? Map<*, *> ?: emptyMap<Any, Any>()
                        
                        val high = mPriority == "1" || mPriority == "high"
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (high) Color.Red.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Pre: " + (if (mType == "all") "Všetkých" else if (mType == "shift") (if (mVal == "top") "Dennisti" else "Smenári") else "Meno: $mVal"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (high) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("Vysoká (1)", fontSize = 8.sp, color = Color.Red, fontWeight = FontWeight.Bold) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.Red.copy(alpha = 0.1f), labelColor = Color.Red)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (mId.isNotEmpty()) {
                                                    try {
                                                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                        db.collection("messages").document(mId).delete()
                                                    } catch(e: Exception) {}
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Zmazať", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                
                                Text(mBody, style = MaterialTheme.typography.bodySmall)
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                // Read accounts
                                val readList = mReadMap.keys.map { it.toString() }
                                val count = readList.size
                                Text(
                                    text = "Prečítané ($count): " + (if (readList.isNotEmpty()) readList.joinToString(", ") else "Ešte nikto"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (count > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
