package com.rayatickets.manager

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val api = RayaApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RayaTheme {
                TicketManagerApp(api = api)
            }
        }
    }
}

@Composable
fun RayaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2DD4BF),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF134E4A),
            surface = Color(0xFF0B1320),
            surfaceVariant = Color(0xFF131D2E),
            background = Color(0xFF070D18),
            onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFF94A3B8)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketManagerApp(api: RayaApi) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("engineer@raya.com") }
    var password by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }

    var isPendingTab by remember { mutableStateOf(true) }

    var selectedLeader by remember { mutableStateOf<String?>(null) }
    var isLeaderMenuOpen by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPill by remember { mutableStateOf("All") }

    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var tableBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun refreshTickets() {
        if (!isLoggedIn) return
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = api.fetchTickets(
                startDate = startDate,
                endDate = endDate,
                isPendingOnly = isPendingTab,
                isResolvedOnly = !isPendingTab
            )
            isLoading = false
            result.onSuccess { list ->
                tickets = list
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to fetch tickets"
            }
        }
    }

    val showDatePicker = { initialDate: LocalDate, onDateSelected: (LocalDate) -> Unit ->
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    val availableLeaders = remember(tickets) {
        tickets.map { it.displayLeader }.filter { it.isNotBlank() && it != "Unassigned" }.distinct()
    }

    val filteredTickets = remember(tickets, searchQuery, isPendingTab, selectedLeader, selectedPill) {
        tickets.filter { ticket ->
            val matchesStatus = if (isPendingTab) ticket.isPending else ticket.isResolved

            val matchesLeader = selectedLeader == null || ticket.displayLeader.equals(selectedLeader, ignoreCase = true)

            val matchesSearch = if (searchQuery.isBlank()) true else {
                ticket.serial.contains(searchQuery, ignoreCase = true) ||
                ticket.bank.contains(searchQuery, ignoreCase = true) ||
                ticket.siteName.contains(searchQuery, ignoreCase = true) ||
                ticket.atmId.contains(searchQuery, ignoreCase = true) ||
                ticket.assignee.contains(searchQuery, ignoreCase = true) ||
                ticket.description.contains(searchQuery, ignoreCase = true)
            }

            val matchesPill = when (selectedPill.lowercase(Locale.ROOT)) {
                "call" -> ticket.type.contains("call", ignoreCase = true)
                "pm" -> ticket.type.contains("pm", ignoreCase = true)
                else -> true
            }

            matchesStatus && matchesLeader && matchesSearch && matchesPill
        }
    }

    LaunchedEffect(filteredTickets) {
        if (filteredTickets.isNotEmpty() && filteredTickets.size < 70) {
            withContext(Dispatchers.Default) {
                tableBitmap = TableImage.render(filteredTickets)
            }
        } else {
            tableBitmap = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E3A5F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AB",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Raya Tickets",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = api.currentUserName ?: "Abdel Baset",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(
                            onClick = {
                                api.logout()
                                isLoggedIn = false
                                tickets = emptyList()
                                tableBitmap = null
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070D18)
                )
            )
        },
        bottomBar = {
            if (isLoggedIn) {
                ExportReportBottomBar(
                    ticketCount = filteredTickets.size,
                    onShareWhatsApp = {
                        tableBitmap?.let { bmp ->
                            val file = TableImage.saveBitmapToFile(context, bmp, "tickets_report.png")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val caption = "تقرير التذاكر (${filteredTickets.size})"
                            shareToWhatsAppDirect(context, uri, null, caption)
                        }
                    },
                    onShareGeneral = {
                        tableBitmap?.let { bmp ->
                            val file = TableImage.saveBitmapToFile(context, bmp)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                        }
                    },
                    onSave = {
                        tableBitmap?.let { bmp ->
                            val file = TableImage.saveBitmapToFile(context, bmp)
                            Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportCsv = {
                        val csvFile = TableImage.exportToCsv(context, filteredTickets)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", csvFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            if (!isLoggedIn) {
                LoginCard(
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onLoginClick = {
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = api.login(email, password)
                            isLoading = false
                            result.onSuccess {
                                isLoggedIn = true
                                refreshTickets()
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Login failed"
                            }
                        }
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))

                SegmentedStatusPill(
                    isPending = isPendingTab,
                    onSelectPending = {
                        if (!isPendingTab) {
                            isPendingTab = true
                            refreshTickets()
                        }
                    },
                    onSelectResolved = {
                        if (isPendingTab) {
                            isPendingTab = false
                            refreshTickets()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showDatePicker(startDate) { newStart ->
                                    startDate = newStart
                                    showDatePicker(endDate) { newEnd ->
                                        endDate = newEnd
                                        refreshTickets()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Date range", fontSize = 11.sp, color = Color(0xFF64748B))
                                val dateText = "${startDate.format(DateTimeFormatter.ofPattern("dd MMM"))} – ${endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                                Text(dateText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isLeaderMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Leader", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text(
                                        text = selectedLeader ?: "All Leaders",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8))
                            }
                        }

                        DropdownMenu(
                            expanded = isLeaderMenuOpen,
                            onDismissRequest = { isLeaderMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Leaders") },
                                onClick = {
                                    selectedLeader = null
                                    isLeaderMenuOpen = false
                                }
                            )
                            availableLeaders.forEach { leaderName ->
                                DropdownMenuItem(
                                    text = { Text(leaderName) },
                                    onClick = {
                                        selectedLeader = leaderName
                                        isLeaderMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tickets", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF64748B))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF131D2E),
                        unfocusedContainerColor = Color(0xFF131D2E),
                        focusedBorderColor = Color(0xFF1E3A5F),
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                FilterPillsRow(
                    selectedPill = selectedPill,
                    onSelectPill = { selectedPill = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPendingTab) "PENDING TICKETS" else "RESOLVED TICKETS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${filteredTickets.size} tickets",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF2DD4BF))
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredTickets, key = { it.id }) { ticket ->
                        TicketCardExact(
                            ticket = ticket,
                            onCopy = {
                                val text = buildString {
                                    appendLine("Call: ${ticket.serial}")
                                    appendLine("Site: ${ticket.bank} – ${ticket.siteName}")
                                    if (ticket.atmId.isNotBlank()) appendLine("ATM ID: ${ticket.atmId}")
                                    appendLine("Status: ${ticket.fullStatusText}")
                                    if (ticket.description.isNotBlank()) appendLine("Fault: ${ticket.description}")
                                    appendLine("Assignee: ${ticket.displayAssignee}")
                                    if (ticket.startTime.isNotBlank()) appendLine("Time: ${ticket.startTime}")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ticket", text))
                                Toast.makeText(context, "Copied Call #${ticket.serial}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedStatusPill(
    isPending: Boolean,
    onSelectPending: () -> Unit,
    onSelectResolved: () -> Unit
) {
    Surface(
        color = Color(0xFF131D2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPending) Color(0xFF0F3A3A) else Color.Transparent)
                    .clickable { onSelectPending() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pending",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPending) Color(0xFF2DD4BF) else Color(0xFF64748B)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isPending) Color(0xFF0F3A3A) else Color.Transparent)
                    .clickable { onSelectResolved() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Resolved",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isPending) Color(0xFF2DD4BF) else Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun FilterPillsRow(
    selectedPill: String,
    onSelectPill: (String) -> Unit
) {
    val pills = listOf("All", "Call", "PM", "Assignee", "Site")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(pills) { pill ->
            val isSelected = selectedPill.equals(pill, ignoreCase = true)
            Surface(
                color = if (isSelected) Color(0xFF2DD4BF) else Color(0xFF131D2E),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { onSelectPill(pill) }
            ) {
                Text(
                    text = pill,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun TicketCardExact(
    ticket: Ticket,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "ATM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "Call ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = ticket.serial,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (bgColor, textColor, dotColor) = when {
                            ticket.isPending -> Triple(Color(0xFF3B270C), Color(0xFFFBBF24), Color(0xFFFBBF24))
                            ticket.isResolved -> Triple(Color(0xFF0F3A3A), Color(0xFF34D399), Color(0xFF34D399))
                            else -> Triple(Color(0xFF0F2B48), Color(0xFF38BDF8), Color(0xFF38BDF8))
                        }

                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = ticket.fullStatusText.ifBlank { "Pending · Spare Part" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${ticket.bank.ifBlank { "NBE" }} – ${ticket.siteName.ifBlank { "Main Branch" }}",
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ticket.description.ifBlank { "Dispensar Jam" },
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ticket.startTime.ifBlank { ticket.createdAt.ifBlank { "11-11-2026 · 10:30 AM" } },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun ExportReportBottomBar(
    ticketCount: Int,
    onShareWhatsApp: () -> Unit,
    onShareGeneral: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "EXPORT REPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShareWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onShareGeneral,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp, color = Color.White)
                }

                FilledTonalButton(
                    onClick = onSave,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp, color = Color.White)
                }

                FilledTonalButton(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

fun shareToWhatsAppDirect(context: Context, imageUri: Uri, phone: String?, caption: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, caption)
        if (!phone.isNullOrBlank()) {
            val cleanPhone = phone.replace(Regex("[^0-9]"), "")
            putExtra("jid", "$cleanPhone@s.whatsapp.net")
        }
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, "Share Report"))
    }
}

@Composable
fun LoginCard(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onLoginClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Login to Raya ATM", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onLoginClick,
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0F172A))
                } else {
                    Text("Sign In", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}