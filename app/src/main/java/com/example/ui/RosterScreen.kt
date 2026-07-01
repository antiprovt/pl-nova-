package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance

sealed class RosterItem {
    data class Header(val title: String, val badgeText: String? = null) : RosterItem()
    data class Employee(val employee: RosterEmployee) : RosterItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RosterScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedHighlightCode by remember { mutableStateOf<String?>(null) }
    var selectedCellDetail by remember { mutableStateOf<RosterCellDetail?>(null) }
    var highlightedEmployeeName by remember { mutableStateOf<String?>(null) }
    var highlightedDay by remember { mutableStateOf<Int?>(null) }
    var editingEmployee by remember { mutableStateOf<RosterEmployee?>(null) }
    var movingEmployeeName by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("shift_prefs", android.content.Context.MODE_PRIVATE) }
    val loggedInUser = remember { prefs.getString("logged_in_user_name", "") ?: "" }
    val canEdit = remember(loggedInUser) {
        val u = loggedInUser.trim().lowercase()
        u == "admin" || u == "riegert" || u == "rieger t."
    }
    val canEditThisMonth = remember(canEdit, RosterData.activeRosterMonth) {
        canEdit && (RosterData.activeRosterMonth >= RosterData.getCurrentMonthIndex())
    }

    LaunchedEffect(Unit) {
        RosterData.isJanuaryPublished = prefs.getBoolean("roster_january_published", false)
        RosterData.loadInAppNotifications(context)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Keep horizontal scroll states in sync safely and cleanly
    val verticalScrollState = rememberScrollState()
    val headerHorizontalScrollState = rememberScrollState()
    val bodyHorizontalScrollState = rememberScrollState()

    // Keep horizontal scroll states in sync safely and cleanly
    LaunchedEffect(headerHorizontalScrollState.value) {
        if (bodyHorizontalScrollState.value != headerHorizontalScrollState.value) {
            bodyHorizontalScrollState.scrollTo(headerHorizontalScrollState.value)
        }
    }
    LaunchedEffect(bodyHorizontalScrollState.value) {
        if (headerHorizontalScrollState.value != bodyHorizontalScrollState.value) {
            headerHorizontalScrollState.scrollTo(bodyHorizontalScrollState.value)
        }
    }

    // Filter employees based on search - combined together per user request
    val filteredEmployees = remember(searchQuery, RosterData.topEmployees, RosterData.bottomEmployees, RosterData.activeRosterMonth) {
        val combined = RosterData.topEmployees + RosterData.bottomEmployees
        if (searchQuery.isBlank()) {
            combined
        } else {
            combined.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val rosterItems = remember(filteredEmployees) {
        val list = mutableListOf<RosterItem>()
        var shiftCount = 0
        
        // Find index of first leader to see if day group header is needed at the top
        val firstLeaderIdx = filteredEmployees.indexOfFirst { emp ->
            val nameLower = emp.name.lowercase()
            nameLower.contains("bielik") ||
            nameLower.contains("kelo") ||
            (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
            (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
        }

        if (firstLeaderIdx > 0) {
            list.add(RosterItem.Header("Denná skupina", "Zmeny"))
        } else if (firstLeaderIdx < 0 && filteredEmployees.isNotEmpty()) {
            list.add(RosterItem.Header("Príslušníci"))
        }

        filteredEmployees.forEach { employee ->
            val nameLower = employee.name.lowercase()
            val isLeader = nameLower.contains("bielik") ||
                           nameLower.contains("kelo") ||
                           (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
                           (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
            
            if (isLeader) {
                shiftCount++
                list.add(RosterItem.Header("Smena $shiftCount", "Veliteľ"))
            }
            list.add(RosterItem.Employee(employee))
        }
        list
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Screen Header & Summary Tool
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val SlovakMonthNames = listOf(
                            "December 2025",
                            "Január 2026",
                            "Február 2026",
                            "Marec 2026",
                            "Apríl 2026",
                            "Máj 2026",
                            "Jún 2026",
                            "Júl 2026",
                            "August 2026",
                            "September 2026",
                            "Október 2026",
                            "November 2026",
                            "December 2026"
                        )
                        var isMonthDropdownExpanded by remember { mutableStateOf(false) }

                        // Left Arrow Button
                        val hasPrev = RosterData.activeRosterMonth > 0 && RosterData.isMonthAccessibleForUser(RosterData.activeRosterMonth - 1, canEdit)
                        IconButton(
                            onClick = {
                                if (hasPrev) {
                                    RosterData.switchMonth(RosterData.activeRosterMonth - 1)
                                }
                            },
                            enabled = hasPrev,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Predchádzajúci mesiac",
                                tint = if (hasPrev) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Month Selection Dropdown Clickable Text
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { isMonthDropdownExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ROZPIS - ${SlovakMonthNames.getOrElse(RosterData.activeRosterMonth) { "" }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "▼",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }

                            DropdownMenu(
                                expanded = isMonthDropdownExpanded,
                                onDismissRequest = { isMonthDropdownExpanded = false }
                            ) {
                                SlovakMonthNames.forEachIndexed { index, name ->
                                    val isAccessible = RosterData.isMonthAccessibleForUser(index, canEdit)
                                    val isCurrentActive = RosterData.activeRosterMonth == index
                                    
                                    if (isAccessible) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = name,
                                                        fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    
                                                    if (index > 0) {
                                                        val isPub = RosterData.publishedMonthsMap[index] == true
                                                        if (isPub) {
                                                            Text(
                                                                text = "Zverejnený",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF22C55E),
                                                                modifier = Modifier.padding(start = 8.dp)
                                                            )
                                                        } else if (canEdit) {
                                                            Text(
                                                                text = "Predpríprava",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.outline,
                                                                modifier = Modifier.padding(start = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                RosterData.switchMonth(index)
                                                isMonthDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Right Arrow Button
                        val hasNext = RosterData.activeRosterMonth < 12 && RosterData.isMonthAccessibleForUser(RosterData.activeRosterMonth + 1, canEdit)
                        IconButton(
                            onClick = {
                                if (hasNext) {
                                    RosterData.switchMonth(RosterData.activeRosterMonth + 1)
                                }
                            },
                            enabled = hasNext,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ďalší mesiac",
                                tint = if (hasNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    val ym = RosterData.getYearMonthForIndex(RosterData.activeRosterMonth)
                    val numDays = ym.lengthOfMonth()
                    var wkDays = 0
                    for (day in 1..numDays) {
                        if (!RosterData.isWeekend(day) && !RosterData.isHoliday(day)) {
                            wkDays++
                        }
                    }
                    val shiftFundValue = wkDays * 8.0
                    val regularFundValue = wkDays * 7.5
                    val fondText = if (RosterData.activeRosterMonth == 0) {
                        "Fond pracovného času: 172,5 | 161 hod."
                    } else if (RosterData.activeRosterMonth == 1) {
                        "Fond pracovného času: 180,0 | 168 hod."
                    } else if (RosterData.activeRosterMonth == 7) {
                        "Fond pracovného času: 172,5 | 161 hod."
                    } else {
                        "Fond pracovného času: ${String.format(java.util.Locale.US, "%.1f", shiftFundValue).replace(".0", ",0")} | ${regularFundValue.toInt()} hod."
                    }
                    Text(
                        text = fondText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tip: Podržaním mena ho môžete presúvať hore/dole.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Active highlight indicator row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (highlightedEmployeeName != null) {
                        AssistantBadge(
                            text = "Hľadáte: $highlightedEmployeeName",
                            onClear = { highlightedEmployeeName = null }
                        )
                    }
                    if (highlightedDay != null) {
                        AssistantBadge(
                            text = "Deň: $highlightedDay",
                            onClear = { highlightedDay = null }
                        )
                    }
                    if (selectedHighlightCode != null) {
                        AssistantBadge(
                            text = "Kód: $selectedHighlightCode",
                            onClear = { selectedHighlightCode = null }
                        )
                    }
                }
            }

            // Search and quick filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Vyhľadať meno...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Zmazať", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("roster_search_field"),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    singleLine = true
                )

                if (canEditThisMonth) {
                    var showAddDialog by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nový", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showAddDialog) {
                        AddEmployeeDialog(
                            onDismiss = { showAddDialog = false },
                            onAdd = { name, isTop ->
                                RosterData.addEmployee(name, isTop)
                                showAddDialog = false
                            }
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Náhľad",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (RosterData.activeRosterMonth > 0 && canEditThisMonth) {
                var showResetConfirmDialog by remember { mutableStateOf(false) }

                if (showResetConfirmDialog) {
                    val SlovakMonthNames = listOf(
                        "December 2025",
                        "Január 2026",
                        "Február 2026",
                        "Marec 2026",
                        "Apríl 2026",
                        "Máj 2026",
                        "Jún 2026",
                        "Júl 2026",
                        "August 2026",
                        "September 2026",
                        "Október 2026",
                        "November 2026",
                        "December 2026"
                    )
                    AlertDialog(
                        onDismissRequest = { showResetConfirmDialog = false },
                        title = { Text("Resetovať rozpis?", fontWeight = FontWeight.Bold) },
                        text = { 
                            val mName = SlovakMonthNames.getOrElse(RosterData.activeRosterMonth) { "tento mesiac" }
                            Text("Naozaj chcete obnoviť rozpis pre mesiac $mName do pôvodného stavu? Všetky vaše zmeny v tomto mesiaci budú trvalo vymazané.") 
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    RosterData.resetToDefaultPattern(context, RosterData.activeRosterMonth)
                                    showResetConfirmDialog = false
                                    RosterData.triggerRosterNotification(
                                        context,
                                        "Rozpis resetovaný",
                                        "Rozpis bol úspešne obnovený do predvoleného stavu."
                                    )
                                }
                            ) {
                                Text("Resetovať", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirmDialog = false }) {
                                Text("Zrušiť")
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val SlovakMonthNames = listOf(
                        "December 2025",
                        "Január 2026",
                        "Február 2026",
                        "Marec 2026",
                        "Apríl 2026",
                        "Máj 2026",
                        "Jún 2026",
                        "Júl 2026",
                        "August 2026",
                        "September 2026",
                        "Október 2026",
                        "November 2026",
                        "December 2026"
                    )
                    
                    // Kolobeh (Shift Rotation) button
                    Button(
                        onClick = {
                            RosterData.applyKolobehForMonth(RosterData.activeRosterMonth)
                            val mName = SlovakMonthNames.getOrElse(RosterData.activeRosterMonth) { "" }
                            RosterData.triggerRosterNotification(
                                context,
                                "Kolobeh spustený",
                                "Služby pre mesiac $mName boli automaticky vyplnené na základe kolobehu z minulého mesiaca."
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kolobeh", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Reset button
                    OutlinedButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Publish button
                    val isMonthPub = RosterData.publishedMonthsMap[RosterData.activeRosterMonth] == true
                    if (!isMonthPub) {
                        Button(
                            onClick = {
                                RosterData.publishMonth(context, RosterData.activeRosterMonth)
                                val mName = SlovakMonthNames.getOrElse(RosterData.activeRosterMonth) { "" }
                                RosterData.triggerRosterNotification(
                                    context,
                                    "Nový rozpis zverejnený",
                                    "Nový rozpis služieb pre mesiac $mName bol zverejnený a sprístupnený všetkým!"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zverejniť", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        // Already published badge
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zverejnený",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Dual-scrolling Table Layout (100% Loop-Safe, high performance, and sticky header)
            Column(modifier = Modifier.weight(1f)) {
                // Keep Days Header ALWAYS at the top. 
                // Left 132.dp corner + Right scrollable days
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                ) {
                    // Left corner (Meno / Fond label)
                    Box(
                        modifier = Modifier
                            .width(132.dp)
                            .fillMaxHeight()
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Meno / Fond",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Horizontal Days Header row
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(headerHorizontalScrollState)
                    ) {
                        val activeYM = RosterData.getYearMonthForIndex(RosterData.activeRosterMonth)
                        val numDays = activeYM.lengthOfMonth()
                        for (day in 1..numDays) {
                            val isWeekend = RosterData.isWeekend(day)
                            val isHoliday = RosterData.isHoliday(day)
                            val dayOfWeek = RosterData.getDayOfWeek(day)
                            
                            val bg = when {
                                isHoliday -> if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2) // beautiful theme-adapted holiday color
                                isWeekend -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9) // beautiful theme-adapted weekend color
                                else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            }

                            val isDaySelected = (day == highlightedDay)
                            val headerCellBg = if (isDaySelected) {
                                if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                bg
                            }

                            Column(
                                modifier = Modifier
                                    .width(42.dp)
                                    .fillMaxHeight()
                                    .background(headerCellBg)
                                    .clickable {
                                        highlightedDay = if (highlightedDay == day) null else day
                                    }
                                    .border(
                                        if (isDaySelected) 1.5.dp else 0.25.dp,
                                        if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHoliday || isWeekend) {
                                        if (isDark) Color(0xFFFCA5A5) else Color.Red
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    lineHeight = 12.sp
                                )
                                Text(
                                    text = dayOfWeek,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isHoliday || isWeekend) {
                                        if (isDark) Color(0xFFF87171) else Color.Red
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    lineHeight = 10.sp
                                )
                            }
                        }
                        
                        // Total heading (FOND)
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "FOND",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Table content: Vertically scrollable body of employees and cell tables
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(verticalScrollState)
                ) {
                    // Left Employee names
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                    ) {
                        rosterItems.forEach { item ->
                            when (item) {
                                is RosterItem.Header -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = if (isDark) {
                                                        listOf(
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                                        )
                                                    } else {
                                                        listOf(
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                                        )
                                                    }
                                                )
                                            )
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                        item.badgeText?.let { badge ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(start = 2.dp)
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                is RosterItem.Employee -> {
                                    val employee = item.employee
                            val isLeader = remember(employee.name) {
                                val nameLower = employee.name.lowercase()
                                nameLower.contains("bielik") ||
                                nameLower.contains("kelo") ||
                                (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
                                (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
                            }
                            
                            val isSelectedRow = employee.name == highlightedEmployeeName
                            val isAnyRowHighlighted = highlightedEmployeeName != null
                            val isRowFaded = isAnyRowHighlighted && !isSelectedRow

                            // Background color for name cell
                            val isMovingThis = employee.name == movingEmployeeName
                            val nameBgColor = if (isMovingThis) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else if (isSelectedRow) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (isRowFaded) {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }

                            // Base color for employee name
                            val baseColor = if (isLeader) {
                                if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            // Text color for employee name
                            val textColor = if (isMovingThis) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else if (isSelectedRow) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                if (isRowFaded) baseColor.copy(alpha = 0.35f) else baseColor
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(nameBgColor)
                                    .combinedClickable(
                                        onClick = {
                                            highlightedEmployeeName = if (isSelectedRow) null else employee.name
                                        },
                                        onLongClick = {
                                            if (canEdit) {
                                                movingEmployeeName = if (isMovingThis) null else employee.name
                                            }
                                        }
                                    )
                                    .border(
                                        if (isMovingThis) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                                        } else if (isSelectedRow) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        }
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMovingThis) {
                                    IconButton(
                                        onClick = { RosterData.moveEmployee(employee.name, up = true) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Posunúť hore",
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Text(
                                        text = employee.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { RosterData.moveEmployee(employee.name, up = false) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Posunúť dole",
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = employee.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelectedRow) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor,
                                        modifier = Modifier.weight(1f).padding(start = 2.dp)
                                    )

                                    IconButton(
                                        onClick = { if (canEdit) { editingEmployee = employee } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Upraviť príslušníka",
                                            tint = if (canEdit) textColor.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                        }
                        }

                        // Footer counts row labels (placed at bottom of vertical scroll if search empty)
                        if (searchQuery.isBlank()) {
                            listOf("Denné hliadky", "Nočné hliadky").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Right side Table cells and total counts (vertical + horizontal scroll)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(bodyHorizontalScrollState)
                    ) {
                        Column(
                            modifier = Modifier
                        ) {
                            // Render cell rows for each employee
                            rosterItems.forEach { item ->
                                when (item) {
                                    is RosterItem.Header -> {
                                        Row(
                                            modifier = Modifier
                                                .height(40.dp)
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = if (isDark) {
                                                            listOf(
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                                                                MaterialTheme.colorScheme.surface
                                                            )
                                                        } else {
                                                            listOf(
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                                                                MaterialTheme.colorScheme.surface
                                                            )
                                                        }
                                                    )
                                                )
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ) {
                                            val activeYM = RosterData.getYearMonthForIndex(RosterData.activeRosterMonth)
                                            val numDays = activeYM.lengthOfMonth()
                                            for (day in 1..numDays) {
                                                val isWeekend = RosterData.isWeekend(day)
                                                val isHoliday = RosterData.isHoliday(day)
                                                val cellBg = if (isHoliday) {
                                                    if (isDark) Color(0xFF451A1A) else Color(0xFFFFECEC)
                                                } else if (isWeekend) {
                                                    if (isDark) Color(0xFF131B2E) else Color(0xFFF1F5F9)
                                                } else {
                                                    Color.Transparent
                                                }
                                                val isDaySelected = (day == highlightedDay)
                                                val finalCellBg = if (isDaySelected) {
                                                    if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                } else {
                                                    cellBg
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .width(42.dp)
                                                        .fillMaxHeight()
                                                        .background(finalCellBg)
                                                        .clickable {
                                                            highlightedDay = if (highlightedDay == day) null else day
                                                        }
                                                        .border(
                                                            if (isDaySelected) 1.5.dp else 0.25.dp,
                                                            if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                                                        )
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(48.dp)
                                                    .fillMaxHeight()
                                                    .background(Color.Transparent)
                                                    .border(0.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            )
                                        }
                                    }
                                    is RosterItem.Employee -> {
                                        val employee = item.employee
                                val isSelectedRow = employee.name == highlightedEmployeeName
                                val isAnyRowHighlighted = highlightedEmployeeName != null
                                val isRowFaded = isAnyRowHighlighted && !isSelectedRow
                                val isMovingThis = employee.name == movingEmployeeName

                                val rowBgColor = if (isMovingThis) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
                                } else if (isSelectedRow) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }

                                Row(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .background(rowBgColor)
                                ) {
                                    val activeYM = RosterData.getYearMonthForIndex(RosterData.activeRosterMonth)
                                    val numDays = activeYM.lengthOfMonth()
                                    for (day in 1..numDays) {
                                        val isWeekend = RosterData.isWeekend(day)
                                        val cell = employee.shifts[day] ?: RosterCell(day, null, null)

                                        val isDaySelected = (day == highlightedDay)
                                        val isCodeFaded = selectedHighlightCode != null && cell.code != null && !cell.code.contains(selectedHighlightCode!!, ignoreCase = true)
                                        val isCellFaded = (isRowFaded && !isDaySelected) || isCodeFaded

                                        // Query adapted colors
                                        val visualColors = RosterData.getCellColorScheme(cell.code, isWeekend, isDark = isDark, isDayGroup = RosterData.isDailyGroupEmployee(employee.name))
                                        
                                        val baseBg = if (isDaySelected) {
                                            if (cell.code != null) {
                                                visualColors.background.copy(alpha = if (isSelectedRow) 0.85f else 0.65f)
                                            } else {
                                                if (isDark) {
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                                }
                                            }
                                        } else {
                                            visualColors.background
                                        }

                                        val cellBgColor = if (isDaySelected && isSelectedRow) {
                                            if (isDark) baseBg.copy(alpha = 0.7f) else baseBg.copy(alpha = 0.8f)
                                        } else if (isDaySelected) {
                                            baseBg
                                        } else if (isCellFaded) {
                                            visualColors.background.copy(alpha = 0.12f)
                                        } else {
                                            visualColors.background
                                        }

                                        // Highlighted cells draw a prominent border, non-highlighted/faded cells draw faded lines
                                        val cellBorder = if (isMovingThis) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                                        } else if (isDaySelected) {
                                            BorderStroke(if (isSelectedRow) 2.5.dp else 1.5.dp, if (isSelectedRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                        } else if (isSelectedRow) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else if (isCellFaded) {
                                            BorderStroke(0.15.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                                        } else {
                                            BorderStroke(0.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        }

                                        val textColor = if (isCellFaded) {
                                            visualColors.text.copy(alpha = 0.18f)
                                        } else {
                                            visualColors.text
                                        }

                                        val subTextColor = if (isCellFaded) {
                                            visualColors.text.copy(alpha = 0.15f)
                                        } else {
                                            visualColors.text.copy(alpha = 0.7f)
                                        }

                                        Column(
                                            modifier = Modifier
                                                .width(42.dp)
                                                .fillMaxHeight()
                                                .background(cellBgColor)
                                                .border(cellBorder)
                                                .clickable {
                                                    selectedCellDetail = RosterCellDetail(
                                                        employeeName = employee.name,
                                                        day = cell.day,
                                                        code = cell.code,
                                                        hours = cell.hours
                                                    )
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (cell.code != null) {
                                                Text(
                                                    text = cell.code,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    color = textColor,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                            if (cell.hours != null) {
                                                Text(
                                                    text = cell.hours,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 8.sp,
                                                    color = subTextColor,
                                                    lineHeight = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    // Total hours cell (Fond)
                                    val fondBg = if (isSelectedRow) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isRowFaded) 0.1f else 0.3f)
                                    }
                                    
                                    val fondBorder = if (isSelectedRow) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isRowFaded) 0.15f else 0.4f))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .fillMaxHeight()
                                            .background(fondBg)
                                            .border(fondBorder)
                                            .clickable {
                                                highlightedEmployeeName = if (isSelectedRow) null else employee.name
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = employee.totalHours,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isRowFaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            }
                            }

                            // Footer counts (for day and night patrol counts)
                            if (searchQuery.isBlank()) {
                                listOf(
                                    RosterData.getDynamicDayPatrolCounts(),
                                    RosterData.getDynamicNightPatrolCounts()
                                ).forEach { counts ->
                                    Row(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        counts.forEachIndexed { idx, count ->
                                            val day = idx + 1
                                            val isWeekend = RosterData.isWeekend(day)
                                            
                                            val isDaySelected = (day == highlightedDay)
                                            // Adapt weekend background colors for dark theme
                                            val bg = if (isDaySelected) {
                                                if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else if (isWeekend) {
                                                if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                            } else {
                                                Color.Transparent
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(42.dp)
                                                    .fillMaxHeight()
                                                    .background(bg)
                                                    .clickable {
                                                        highlightedDay = if (highlightedDay == day) null else day
                                                    }
                                                    .border(
                                                        if (isDaySelected) 1.5.dp else 0.25.dp,
                                                        if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val countStr = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString().replace('.', ',')
                                                Text(
                                                    text = countStr,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // empty FOND corner for footers
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .fillMaxHeight()
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legend panel at the bottom of the card
            LegendPanel(isDark = isDark)
        }
    }

    // Interactive tooltip dialog when cell is tapped
    selectedCellDetail?.let { detail ->
        CellDetailDialog(
            detail = detail,
            canEdit = canEditThisMonth,
            onDismiss = { selectedCellDetail = null }
        )
    }

    // Interactive Employee Edit dialog
    editingEmployee?.let { employee ->
        EditEmployeeDialog(
            employee = employee,
            onDismiss = { editingEmployee = null }
        )
    }
}

// Custom interactive badges or tags
@Composable
fun AssistantBadge(
    text: String,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Zrušiť filter",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(12.dp)
                    .clickable { onClear() }
            )
        }
    }
}

@Composable
fun LegendPanel(isDark: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = "Legenda smien a skratiek (Skenovaný papier):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem("D", "Dovolenka", isDark)
                LegendItem("CH", "Choroba", isDark)
                LegendItem("SR", "Ranná služba (07:00 – 19:00)", isDark)
                LegendItem("SN", "Nočná služba (19:00 – 07:00)", isDark)
                LegendItem("Par", "Paragraf (Lekár/Sprievod)", isDark)
                LegendItem("KZ", "Kolektívna zmluva (KZ)", isDark)
                LegendItem("P", "Služba v ranej (Fondoá) 7,5h", isDark)
            }
        }
    }
}

@Composable
fun LegendItem(code: String, label: String, isDark: Boolean) {
    val colors = RosterData.getCellColorScheme(code, isDark = isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.background)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                lineHeight = 10.sp
            )
        }
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class RosterCellDetail(
    val employeeName: String,
    val day: Int,
    val code: String?,
    val hours: String?
)

@Composable
fun AddEmployeeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectTopGroup by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pridať nového príslušníka",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meno a priezvisko") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Skupina (Sila):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectTopGroup = true }
                    ) {
                        RadioButton(selected = selectTopGroup, onClick = { selectTopGroup = true })
                        Text("Vedenie/Top", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectTopGroup = false }
                    ) {
                        RadioButton(selected = !selectTopGroup, onClick = { selectTopGroup = false })
                        Text("Smeny/Bottom", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Zrušiť")
                    }
                    Button(
                        onClick = { onAdd(name, selectTopGroup) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pridať")
                    }
                }
            }
        }
    }
}

@Composable
fun EditEmployeeDialog(
    employee: RosterEmployee,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(employee.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Upraviť príslušníka",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meno a priezvisko") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showDeleteConfirm) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Naozaj chcete vymazať tohto príslušníka z rozpisu?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Nie")
                                }
                                Button(
                                    onClick = {
                                        RosterData.deleteEmployee(employee.name)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Áno, vymazať")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Odstrániť")
                        }
                        
                        Button(
                            onClick = {
                                RosterData.renameEmployee(employee.name, name)
                                onDismiss()
                            },
                            enabled = name.isNotBlank() && name != employee.name,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Uložiť")
                        }
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Späť")
                }
            }
        }
    }
}

private fun checkShiftConflict(employeeName: String, day: Int, newCode: String?): String? {
    val normNewCode = if (newCode == "NONE" || newCode.isNullOrBlank()) null else newCode
    
    val nightCodes = setOf("N", "SN", "PN")
    val dayCodes = setOf("R", "SR", "PR")
    
    val currentMonth = RosterData.activeRosterMonth
    val combined = RosterData.topEmployees + RosterData.bottomEmployees
    val emp = combined.find { it.name == employeeName } ?: return null
    
    // Check previous day (day - 1)
    if (day > 1 && normNewCode != null && dayCodes.contains(normNewCode)) {
        val prevCell = emp.shifts[day - 1]
        val prevCode = prevCell?.code
        if (prevCode != null && nightCodes.contains(prevCode)) {
            val prevName = when (prevCode) {
                "N" -> "Nočná služba"
                "SN" -> "Nočná služba (SN)"
                "PN" -> "PCO nočná (PN)"
                else -> prevCode
            }
            return "Dňa ${day - 1}. má službu $prevName ($prevCode) a dňa $day. sa pokúšate naplánovať dennú službu ($normNewCode). Po nočnej službe nenasleduje dostatočný nepretržitý odpočinok, ale denná služba, čo nie je dovolené!"
        }
    }
    
    // Check next day (day + 1)
    val ym = RosterData.getYearMonthForIndex(currentMonth)
    val numDays = ym.lengthOfMonth()
    if (day < numDays && normNewCode != null && nightCodes.contains(normNewCode)) {
        val nextCell = emp.shifts[day + 1]
        val nextCode = nextCell?.code
        if (nextCode != null && dayCodes.contains(nextCode)) {
            val nextName = when (nextCode) {
                "R" -> "Ranná služba"
                "SR" -> "Ranná služba (SR)"
                "PR" -> "PCO ranná (PR)"
                else -> nextCode
            }
            return "Dňa $day. sa pokúšate naplánovať nočnú službu ($normNewCode) a dňa ${day + 1}. má službu $nextName ($nextCode). Po nočnej službe nesmie hneď na druhý deň nasledovať denná služba!"
        }
    }
    
    return null
}

@Composable
fun CellDetailDialog(
    detail: RosterCellDetail,
    canEdit: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (!canEdit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detail služby",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val monthLabel = if (RosterData.activeRosterMonth == 0) "Dec 2025" else "Jan 2026"
                            Text(
                                text = "${detail.employeeName} — ${detail.day}. $monthLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Zavrieť")
                        }
                    }

                    Divider()

                    // Visual configurations
                    val isWeekendVal = RosterData.isWeekend(detail.day)
                    val styleColors = RosterData.getCellColorScheme(
                        code = if (detail.code == "NONE" || detail.code.isNullOrEmpty()) null else detail.code,
                        isWeekend = isWeekendVal,
                        isDark = isDark,
                        isDayGroup = RosterData.isDailyGroupEmployee(detail.employeeName)
                    )

                    val shiftTypeDesc = when (detail.code) {
                        "R" -> "Ranná služba (R)"
                        "SR" -> "Ranná služba (SR)"
                        "N" -> "Nočná služba (N)"
                        "SN" -> "Nočná služba (SN)"
                        "PR" -> "PCO ranná (PR)"
                        "PN" -> "PCO nočná (PN)"
                        "D" -> "Dovolenka (D)"
                        "CH" -> "Choroba (CH)"
                        "KZ", "KZS", "KZV", "KZVS" -> "Kĺzavé voľno (${detail.code})"
                        "Par" -> "Paragraf (Par)"
                        "P" -> {
                            val isDayGroup = RosterData.isDailyGroupEmployee(detail.employeeName)
                            if (isDayGroup) "Ranná služba (P)" else "Porada (P)"
                        }
                        "V" -> "Vzdelávanie (V)"
                        null, "", "NONE", "Voľno" -> "Voľno"
                        else -> detail.code
                    }

                    val hoursDesc = if (!detail.hours.isNullOrBlank()) "${detail.hours} hod" else "0 hod"

                    // Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Row for Shift Type
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(styleColors.background)
                                        .border(1.dp, styleColors.text, androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Typ služby / status",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = shiftTypeDesc,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Row for Shift Length
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Dĺžka smeny",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = hoursDesc,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Zavrieť", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    var selectedCode by remember { mutableStateOf(detail.code ?: "") }
    var customHours by remember { mutableStateOf(detail.hours ?: "") }
    var targetDay by remember { mutableIntStateOf(detail.day) }

    var showWarningAlert by remember { mutableStateOf(false) }
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var onAlertConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showWarningAlert) {
        AlertDialog(
            onDismissRequest = { showWarningAlert = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = alertTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(text = alertMessage, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningAlert = false
                        onAlertConfirm?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uložiť napriek tomu", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningAlert = false }) {
                    Text("Zrušiť", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with custom title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upraviť službu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val monthLabel = if (RosterData.activeRosterMonth == 0) "Dec 2025" else "Jan 2026"
                        Text(
                            text = "${detail.employeeName} — ${detail.day}. $monthLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Zavrieť")
                    }
                }

                Divider()

                // Section 1: Code Selection (Typ služby / zmeny)
                Text(
                    text = "Typ služby / Status dňa:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Safe columns/rows grid rendering layout
                val presetRows = listOf(
                    listOf("SR", "SN", "R", "N"),
                    listOf("PR", "PN", "P", "D"),
                    listOf("CH", "Par", "KZ", "V"),
                    listOf("NONE", "", "", "")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { code ->
                                if (code.isBlank()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    val label = if (code == "NONE") "Voľno" else code
                                    val isSelected = (code == selectedCode || (code == "NONE" && selectedCode.isBlank()))
                                    
                                    val styleColors = RosterData.getCellColorScheme(
                                        code = if (code == "NONE") null else code,
                                        isWeekend = RosterData.isWeekend(detail.day),
                                        isDark = isDark,
                                        isDayGroup = RosterData.isDailyGroupEmployee(detail.employeeName)
                                    )

                                    val bg = if (isSelected) {
                                        styleColors.background
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    }

                                    val strokeColor = if (isSelected) {
                                        styleColors.text
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(if (isSelected) 2.dp else 1.dp, strokeColor, RoundedCornerShape(8.dp))
                                            .clickable(enabled = canEdit) {
                                                if (code == "NONE") {
                                                    selectedCode = ""
                                                    customHours = ""
                                                } else {
                                                    selectedCode = code
                                                    customHours = when (code) {
                                                        "P", "D", "CH", "Par", "V" -> "7,5"
                                                        else -> "11,5"
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = if (isSelected) styleColors.text else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val activeConflictMessage = remember(selectedCode, detail.employeeName, detail.day) {
                    checkShiftConflict(detail.employeeName, detail.day, selectedCode)
                }

                if (activeConflictMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Chyba",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "CHYBNÉ PORADIE SLUŽIEB / NEPRÍPUSTNÁ KOMBINÁCIA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeConflictMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Section 2: Hours selection
                Text(
                    text = "Počet hodín tejto služby:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customHours,
                        onValueChange = { customHours = it },
                        enabled = canEdit,
                        placeholder = { Text("0,0") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        label = { Text("Hodiny") }
                    )

                    // Quick hour preset action button chips
                    val presetHours = listOf("7,5", "11,5", "13,5")
                    presetHours.forEach { hrs ->
                        val isSelHrs = customHours == hrs
                        Button(
                            onClick = { customHours = hrs },
                            enabled = canEdit,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelHrs) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isSelHrs) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(hrs, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider()

                // Section 3: Move/Copy Day Option (zmeniť deň služby na iný)
                Text(
                    text = "Kopírovať / Presunúť na iný deň v mesiaci:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cieľový deň:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { if (targetDay > 1) targetDay-- },
                            enabled = canEdit
                        ) {
                            Text("<", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val mLabel = if (RosterData.activeRosterMonth == 0) "Dec" else "Jan"
                            Text(
                                text = "${targetDay}. $mLabel",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        TextButton(
                            onClick = { if (targetDay < 31) targetDay++ },
                            enabled = canEdit
                        ) {
                            Text(">", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val conflict = checkShiftConflict(detail.employeeName, targetDay, selectedCode)
                            val copyAction = {
                                RosterData.moveOrCopyShift(
                                    employeeName = detail.employeeName,
                                    fromDay = detail.day,
                                    toDay = targetDay,
                                    deleteSource = false
                                )
                                val mName = if (RosterData.activeRosterMonth == 0) "december" else "január"
                                RosterData.triggerRosterNotification(
                                    context,
                                    "Zmena v rozpise: ${detail.employeeName}",
                                    "Vaša služba na deň $targetDay. $mName 2026 bola skopírovaná."
                                )
                                val leader = findLeaderForEmployee(detail.employeeName)
                                if (leader != null) {
                                    RosterData.triggerRosterNotification(
                                        context,
                                        "Zmena v rozpise: ${detail.employeeName} (Veliteľ $leader)",
                                        "Služba príslušníka ${detail.employeeName} na deň $targetDay. $mName 2026 bola skopírovaná."
                                    )
                                }
                                onDismiss()
                            }
                            if (conflict != null) {
                                alertTitle = "Upozornenie: Chybné poradie služieb"
                                alertMessage = "$conflict\n\nChcete napriek tomu skopírovať túto službu na cieľový deň ${targetDay}.?"
                                onAlertConfirm = copyAction
                                showWarningAlert = true
                            } else {
                                copyAction()
                            }
                        },
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Kopírovať", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val conflict = checkShiftConflict(detail.employeeName, targetDay, selectedCode)
                            val moveAction = {
                                RosterData.moveOrCopyShift(
                                    employeeName = detail.employeeName,
                                    fromDay = detail.day,
                                    toDay = targetDay,
                                    deleteSource = true
                                )
                                val mName = if (RosterData.activeRosterMonth == 0) "december" else "január"
                                RosterData.triggerRosterNotification(
                                    context,
                                    "Zmena v rozpise: ${detail.employeeName}",
                                    "Vaša služba bola presunutá z dňa ${detail.day}. na deň $targetDay. $mName."
                                )
                                val leader = findLeaderForEmployee(detail.employeeName)
                                if (leader != null) {
                                    RosterData.triggerRosterNotification(
                                        context,
                                        "Zmena v rozpise: ${detail.employeeName} (Veliteľ $leader)",
                                        "Služba príslušníka ${detail.employeeName} bola presunutá z dňa ${detail.day}. na deň $targetDay. $mName."
                                    )
                                }
                                onDismiss()
                            }
                            if (conflict != null) {
                                alertTitle = "Upozornenie: Chybné poradie služieb"
                                alertMessage = "$conflict\n\nChcete napriek tomu presunúť túto službu na cieľový deň ${targetDay}.?"
                                onAlertConfirm = moveAction
                                showWarningAlert = true
                            } else {
                                moveAction()
                            }
                        },
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Presunúť", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider()

                // Save or Clear Cell buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            RosterData.updateCell(detail.employeeName, detail.day, null, null)
                            val mName = if (RosterData.activeRosterMonth == 0) "december" else "január"
                            RosterData.triggerRosterNotification(
                                context,
                                "Zmena v rozpise: ${detail.employeeName}",
                                "Vaša služba na deň ${detail.day}. $mName bola zrušená (Voľno)."
                            )
                            val leader = findLeaderForEmployee(detail.employeeName)
                            if (leader != null) {
                                RosterData.triggerRosterNotification(
                                    context,
                                    "Zmena v rozpise: ${detail.employeeName} (Veliteľ $leader)",
                                    "Služba príslušníka ${detail.employeeName} na deň ${detail.day}. $mName bola zrušená (Voľno)."
                                )
                            }
                            onDismiss()
                        },
                        enabled = canEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vynulovať", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val conflict = checkShiftConflict(detail.employeeName, detail.day, selectedCode)
                            val saveAction = {
                                RosterData.updateCell(
                                    employeeName = detail.employeeName,
                                    day = detail.day,
                                    code = selectedCode,
                                    hours = customHours
                                )
                                val cellDesc = if (selectedCode.isBlank()) "Voľno" else "$selectedCode ($customHours hod)"
                                val mName = if (RosterData.activeRosterMonth == 0) "december" else "január"
                                RosterData.triggerRosterNotification(
                                    context,
                                    "Zmena v rozpise: ${detail.employeeName}",
                                    "Vaša služba na deň ${detail.day}. $mName bola upravená na $cellDesc."
                                )
                                val leader = findLeaderForEmployee(detail.employeeName)
                                if (leader != null) {
                                    RosterData.triggerRosterNotification(
                                        context,
                                        "Zmena v rozpise: ${detail.employeeName} (Veliteľ $leader)",
                                        "Služba príslušníka ${detail.employeeName} na deň ${detail.day}. $mName bola upravená na $cellDesc."
                                    )
                                }
                                onDismiss()
                            }
                            if (conflict != null) {
                                alertTitle = "Upozornenie: Chybné poradie služieb"
                                alertMessage = "$conflict\n\nChcete napriek tomu uložiť túto službu?"
                                onAlertConfirm = saveAction
                                showWarningAlert = true
                            } else {
                                saveAction()
                            }
                        },
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Uložiť", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            color = color
        )
    }
}

fun findLeaderForEmployee(employeeName: String): String? {
    val combinedList = RosterData.topEmployees + RosterData.bottomEmployees
    val targetIndex = combinedList.indexOfFirst { it.name.trim().equals(employeeName.trim(), ignoreCase = true) }
    if (targetIndex == -1) return null
    for (i in targetIndex downTo 0) {
        val emp = combinedList[i]
        val nameLower = emp.name.lowercase()
        val isLeader = nameLower.contains("bielik") ||
                       nameLower.contains("kelo") ||
                       (nameLower.contains("krenčan") && !nameLower.contains("mgr")) ||
                       (nameLower.contains("obert") && !nameLower.contains("oberfranc"))
        if (isLeader && !emp.name.trim().equals(employeeName.trim(), ignoreCase = true)) {
            return emp.name
        }
    }
    return null
}
