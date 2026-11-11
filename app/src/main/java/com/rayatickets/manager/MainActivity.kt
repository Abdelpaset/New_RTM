package com.rayatickets.manager

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
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF0369A1),
            surface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFF1E293B),
            background = Color(0xFF0B0F19),
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

    var isPendingSelected by remember { mutableStateOf(true) }
    var isResolvedSelected by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedAssignee by remember { mutableStateOf<String?>(null) }

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
                isPendingOnly = isPendingSelected && !isResolvedSelected,
                isResolvedOnly = isResolvedSelected && !isPendingSelected,
                leaderId = 131
            )
            isLoading = false
            result.onSuccess { list ->
                tickets = list
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to fetch tickets"
            }
        }
    }

    val filteredTickets = remember(tickets, searchQuery, selectedType, selectedAssignee, isPendingSelected, isResolvedSelected) {
        tickets.filter { ticket ->
            val matchesStatus = when {
                isPendingSelected && isResolvedSelected -> true
                isPendingSelected -> ticket.isPending
                isResolvedSelected -> ticket.isResolved
                else -> false
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                ticket.ticketNumber.contains(searchQuery, ignoreCase = true) ||
                ticket.bank.contains(searchQuery, ignoreCase = true) ||
                ticket.siteName.contains(searchQuery, ignoreCase = true) ||
                ticket.machineSerial.contains(searchQuery, ignoreCase = true) ||
                ticket.assignee.contains(searchQuery, ignoreCase = true)
            }
            val matchesType = selectedType == null || ticket.type.equals(selectedType, ignoreCase = true)
            val matchesAssignee = selectedAssignee == null || ticket.displayAssignee.equals(selectedAssignee, ignoreCase = true)

            matchesStatus && matchesSearch && matchesType && matchesAssignee
        }
    }

    // Safely render bitmap ONLY when filteredTickets.size < 70 to completely prevent app crash and memory exhaustion
    LaunchedEffect(filteredTickets) {
        if (filteredTickets.isNotEmpty() && filteredTickets.size < 70) {
            withContext(Dispatchers.Default) {
                tableBitmap = TableImage.render(filteredTickets)
            }
        } else {
            tableBitmap = null
        }
    }

    val availableTypes = remember(tickets) {
        tickets.map { it.type }.filter { it.isNotBlank() }.distinct()
    }

    val availableAssignees = remember(tickets) {
        tickets.map { it.displayAssignee }.filter { it.isNotBlank() && it != "Unassigned" }.distinct()
    }

    val selectedAssigneePhone = remember(selectedAssignee) {
        EngineerDirectory.getPhone(selectedAssignee)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = api.currentUserName ?: "Raya Tickets",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ATM Field Operations",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { refreshTickets() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
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
                ControlsCard(
                    startDate = startDate,
                    endDate = endDate,
                    onStartDateChange = { startDate = it; refreshTickets() },
                    onEndDateChange = { endDate = it; refreshTickets() },
                    isPendingSelected = isPendingSelected,
                    onPendingToggle = { isPendingSelected = !isPendingSelected },
                    isResolvedSelected = isResolvedSelected,
                    onResolvedToggle = { isResolvedSelected = !isResolvedSelected }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by ATM ID, Bank, Serial, Site...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                FilterPillsRow(
                    selectedType = selectedType,
                    onSelectType = { selectedType = if (selectedType == it) null else it },
                    selectedAssignee = selectedAssignee,
                    onSelectAssignee = { selectedAssignee = if (selectedAssignee == it) null else it },
                    types = if (availableTypes.isNotEmpty()) availableTypes else TicketParser.standardCallTypes,
                    assignees = availableAssignees
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Condition: Show Table Image preview ONLY when tickets are less than 70
                if (filteredTickets.size in 1..69) {
                    tableBitmap?.let { bmp ->
                        TablePreviewCard(
                            bitmap = bmp,
                            ticketCount = filteredTickets.size,
                            selectedAssignee = selectedAssignee,
                            assigneePhone = selectedAssigneePhone,
                            onShareGeneral = {
                                val file = TableImage.saveBitmapToFile(context, bmp)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Table Image"))
                            },
                            onShareWhatsApp = {
                                val file = TableImage.saveBitmapToFile(context, bmp, "tickets_${selectedAssignee ?: "team"}.png")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val caption = "تذاكر العمل (${filteredTickets.size}) - ${selectedAssignee ?: "الفريق"}"
                                shareToWhatsAppDirect(context, uri, selectedAssigneePhone, caption)
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (filteredTickets.size >= 70) {
                    // Safe Mode Notice Card for 70+ tickets (prevents OOM / app crash)
                    LargeTicketNoticeCard(
                        ticketCount = filteredTickets.size,
                        onExportCsv = {
                            val csvFile = TableImage.exportToCsv(context, filteredTickets)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", csvFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Download CSV"))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredTickets, key = { it.id }) { ticket ->
                        TicketCard(
                            ticket = ticket,
                            onCopy = {
                                val text = buildString {
                                    appendLine("Ticket: #${ticket.ticketNumber}")
                                    appendLine("Bank: ${ticket.bank}")
                                    appendLine("Site: ${ticket.siteName}")
                                    if (ticket.machineSerial.isNotBlank()) appendLine("Serial: ${ticket.machineSerial}")
                                    appendLine("Status: ${ticket.status}")
                                    appendLine("Type: ${ticket.type}")
                                    appendLine("Assignee: ${ticket.displayAssignee}")
                                    if (ticket.startTime.isNotBlank()) appendLine("Time: ${ticket.startTime}")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ticket", text))
                                Toast.makeText(context, "Copied #${ticket.ticketNumber}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
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
        context.startActivity(Intent.createChooser(fallback, "Share Tickets"))
    }
}

@Composable
fun FilterPillsRow(
    selectedType: String?,
    onSelectType: (String) -> Unit,
    selectedAssignee: String?,
    onSelectAssignee: (String) -> Unit,
    types: List<String>,
    assignees: List<String>
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedType == null && selectedAssignee == null,
                onClick = { 
                    selectedType?.let { onSelectType(it) }
                    selectedAssignee?.let { onSelectAssignee(it) }
                },
                label = { Text("All", fontSize = 12.sp) }
            )
        }
        items(types) { typeName ->
            FilterChip(
                selected = selectedType.equals(typeName, ignoreCase = true),
                onClick = { onSelectType(typeName) },
                label = { Text(typeName, fontSize = 12.sp) }
            )
        }
        items(assignees) { engName ->
            FilterChip(
                selected = selectedAssignee.equals(engName, ignoreCase = true),
                onClick = { onSelectAssignee(engName) },
                label = { Text(engName, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun ControlsCard(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    isPendingSelected: Boolean,
    onPendingToggle: () -> Unit,
    isResolvedSelected: Boolean,
    onResolvedToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Date selection */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { /* Date selection */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isPendingSelected,
                    onClick = onPendingToggle,
                    label = { Text("Pending", fontSize = 12.sp) },
                    leadingIcon = {
                        if (isPendingSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isResolvedSelected,
                    onClick = onResolvedToggle,
                    label = { Text("Resolved", fontSize = 12.sp) },
                    leadingIcon = {
                        if (isResolvedSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LargeTicketNoticeCard(
    ticketCount: Int,
    onExportCsv: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tickets Count: $ticketCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download CSV", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "معاينة الصورة متوقفة تلقائياً لأن عدد التذاكر ($ticketCount) يتجاوز 70 تذكرة لحماية التطبيق من الإغلاق المفاجئ واستهلاك الذاكرة. يمكنك تحميلها كـ CSV أو الفلترة حسب المهندس.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TablePreviewCard(
    bitmap: Bitmap,
    ticketCount: Int,
    selectedAssignee: String?,
    assigneePhone: String?,
    onShareGeneral: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onExportCsv: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tickets Count: $ticketCount",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // WhatsApp direct share button when assignee is selected
                    if (!selectedAssignee.isNullOrBlank()) {
                        Button(
                            onClick = onShareWhatsApp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    FilledTonalButton(
                        onClick = onShareGeneral,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = onExportCsv,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Table Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun TicketCard(
    ticket: Ticket,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${ticket.ticketNumber}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ticket.bank,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Ticket",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = ticket.siteName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (ticket.machineSerial.isNotBlank()) {
                    Text(
                        text = "Serial: ${ticket.machineSerial}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusBadge(ticket.status)
                        TypeBadge(ticket.type)
                    }
                    Text(
                        text = ticket.displayAssignee,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "pending" -> Color(0xFF78350F) to Color(0xFFFDE68A)
        "resolved", "solved by phone" -> Color(0xFF14532D) to Color(0xFFBBF7D0)
        "cancelled" -> Color(0xFF7F1D1D) to Color(0xFFFECACA)
        else -> Color(0xFF334155) to Color(0xFFE2E8F0)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = status,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TypeBadge(type: String) {
    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
        Text(
            text = type,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Login to Raya ATM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In")
                }
            }
        }
    }
}
