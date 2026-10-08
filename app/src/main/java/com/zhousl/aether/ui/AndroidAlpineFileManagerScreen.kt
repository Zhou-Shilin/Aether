package com.zhousl.aether.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import com.zhousl.aether.runtime.AndroidAlpineFileEntry
import com.zhousl.aether.runtime.AndroidAlpineFileManagerRuntime
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSettingsBackground
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import io.github.rosemoe.sora.widget.CodeEditor
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class AndroidFileDialog { None, NewFile, NewFolder, Rename, Delete }
private enum class AndroidFileSort { Name, Date, Size }

@Composable
internal fun AndroidAlpineFileManagerScreen(
    runtime: AndroidAlpineFileManagerRuntime,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<AndroidAlpineFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(AndroidFileSort.Name) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var createExpanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AndroidAlpineFileEntry?>(null) }
    var dialog by remember { mutableStateOf(AndroidFileDialog.None) }
    var draftName by remember { mutableStateOf("") }
    var editorFile by remember { mutableStateOf<AndroidAlpineFileEntry?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var filePreview by remember { mutableStateOf<AlpineFilePreview?>(null) }
    var pendingDownload by remember { mutableStateOf<AndroidAlpineFileEntry?>(null) }

    fun refresh() {
        val requestedPath = path
        loading = true
        scope.launch {
            runCatching { runtime.listDirectory(requestedPath) }
                .onSuccess { if (path == requestedPath) entries = it }
                .onFailure { error = it.message ?: "Unable to load directory." }
            if (path == requestedPath) loading = false
        }
    }

    fun importDocuments(uris: List<Uri>, folders: Boolean) {
        if (uris.isEmpty()) return
        scope.launch {
            loading = true
            runCatching {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val document = if (folders) {
                        DocumentFile.fromTreeUri(context, uri)
                    } else {
                        DocumentFile.fromSingleUri(context, uri)
                    } ?: error("Unable to open the selected item.")
                    importAndroidDocument(context, runtime, path, document)
                }
            }.onFailure { error = it.message ?: "Unable to import the selected item." }
            refresh()
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { importDocuments(it, folders = false) },
    )
    val folderImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri -> importDocuments(listOfNotNull(uri), folders = true) },
    )
    val fileExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
        onResult = { uri ->
            val entry = pendingDownload
            pendingDownload = null
            if (uri != null && entry != null) {
                scope.launch {
                    loading = true
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Unable to open the selected download location.")
                        output.use { runtime.exportFile(entry.path, it) }
                    }.onFailure { failure ->
                        runCatching { context.contentResolver.delete(uri, null, null) }
                        error = failure.message ?: "Unable to download the file."
                    }
                    loading = false
                }
            }
        },
    )

    fun open(entry: AndroidAlpineFileEntry) {
        selected = null
        if (entry.isDirectory) {
            path = entry.path
            query = ""
            return
        }
        scope.launch {
            loading = true
            runCatching {
                val prefix = runtime.fileSystem.readPrefix(entry.path, 4096)
                val kind = alpinePreviewKind(entry.name, prefix)
                val text = if (kind == AlpinePreviewKind.Text && entry.size <= 8L * 1024 * 1024) {
                    val bytes = runtime.fileSystem.read(entry.path, 8L * 1024 * 1024)
                    runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
                } else null
                if (text != null) {
                    editorContent = text
                    editorFile = entry
                } else {
                    require(entry.size <= 128L * 1024 * 1024) { "File is too large to preview (maximum 128 MB)." }
                    val directory = java.io.File(context.cacheDir, "assistant-local-open/alpine-preview-${java.util.UUID.randomUUID()}")
                    val file = java.io.File(directory, entry.name)
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            check(directory.mkdirs()) { "Unable to prepare preview." }
                            file.outputStream().use { runtime.exportFile(entry.path, it) }
                        }
                        filePreview = AlpineFilePreview(entry.name, file, if (kind == AlpinePreviewKind.Text) AlpinePreviewKind.External else kind)
                    } catch (failure: Throwable) {
                        directory.deleteRecursively()
                        throw failure
                    }
                }
            }.onFailure { error = it.message ?: "Unable to open file." }
            loading = false
        }
    }

    fun navigateBack(): Boolean {
        if (filePreview != null) {
            filePreview = null
            return true
        }
        if (editorFile != null) {
            editorFile = null
            return true
        }
        if (path != "/") {
            path = path.substringBeforeLast('/', "").ifBlank { "/" }
            query = ""
            return true
        }
        return false
    }

    BackHandler { if (!navigateBack()) onBack() }
    LaunchedEffect(path) { refresh() }

    filePreview?.let { preview ->
        AlpineFilePreviewScreen(preview, onBack = { filePreview = null })
        return
    }

    if (editorFile != null) {
        SoraEditorScreen(
            entry = editorFile!!,
            initialContent = editorContent,
            onSave = { content ->
                val target = editorFile ?: return@SoraEditorScreen
                scope.launch {
                    runCatching { runtime.fileSystem.write(target.path, content.encodeToByteArray()) }
                        .onFailure { error = it.message ?: "Unable to save file." }
                }
            },
            onBack = { editorFile = null },
        )
        return
    }

    val visibleEntries = entries
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<AndroidAlpineFileEntry> { it.isDirectory }.thenComparator { left, right ->
            when (sort) {
                AndroidFileSort.Name -> left.name.compareTo(right.name, ignoreCase = true)
                AndroidFileSort.Date -> right.modifiedAtMillis.compareTo(left.modifiedAtMillis)
                AndroidFileSort.Size -> right.size.compareTo(left.size)
            }
        })
        .toList()

    Scaffold(
        containerColor = AetherSettingsBackground,
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(AetherSurface).statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Alpine Files", style = MaterialTheme.typography.titleMedium, color = AetherOnSurface)
                        Text(path, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant, maxLines = 1)
                    }
                    IconButton(
                        onClick = {
                            if (path != "/") path = path.substringBeforeLast('/', "").ifBlank { "/" }
                        },
                        enabled = path != "/",
                    ) { Icon(Icons.Rounded.ArrowUpward, "Parent folder") }
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) query = "" }) {
                        Icon(if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search, "Search")
                    }
                    if (loading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = ::refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    }
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "View options")
                        }
                        DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (grid) "List view" else "Grid view") },
                                onClick = { grid = !grid; optionsExpanded = false },
                                leadingIcon = { Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, null) },
                            )
                            AndroidFileSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${option.name.lowercase()}") },
                                    onClick = { sort = option; optionsExpanded = false },
                                    leadingIcon = { Icon(Icons.Rounded.Sort, null) },
                                )
                            }
                        }
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        placeholder = { Text("Search this folder") },
                        singleLine = true,
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { createExpanded = true }, containerColor = AetherPrimary) {
                    Icon(Icons.Rounded.Add, "Create", tint = Color.White)
                }
                DropdownMenu(expanded = createExpanded, onDismissRequest = { createExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("New file") },
                        onClick = { draftName = ""; dialog = AndroidFileDialog.NewFile; createExpanded = false },
                        leadingIcon = { Icon(Icons.Rounded.Description, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        onClick = { draftName = ""; dialog = AndroidFileDialog.NewFolder; createExpanded = false },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Import files") },
                        onClick = { createExpanded = false; fileImportLauncher.launch(arrayOf("*/*")) },
                        leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Import folder") },
                        onClick = { createExpanded = false; folderImportLauncher.launch(null) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFolderUpload, null) },
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(
            Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading && entries.isEmpty() -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                visibleEntries.isEmpty() -> Text(if (query.isBlank()) "This folder is empty" else "No matching files", color = AetherOnSurfaceVariant)
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(visibleEntries, key = { it.path }) { entry ->
                        AndroidFileGridItem(entry, onOpen = { open(entry) }, onMore = { selected = entry })
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleEntries, key = { it.path }) { entry ->
                        AndroidFileListItem(entry, onOpen = { open(entry) }, onMore = { selected = entry })
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        Dialog(onDismissRequest = { selected = null }) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(8.dp), color = AetherSurface) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = { open(entry) },
                        leadingIcon = { Icon(if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null) },
                    )
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Open with another app") },
                            onClick = {
                                selected = null
                                scope.launch {
                                    loading = true
                                    val directory = java.io.File(context.cacheDir, "assistant-local-open/alpine-open-${java.util.UUID.randomUUID()}")
                                    runCatching {
                                        val file = java.io.File(directory, entry.name)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            directory.parentFile?.listFiles()?.filter {
                                                it.name.startsWith("alpine-open-") && System.currentTimeMillis() - it.lastModified() > 86_400_000
                                            }?.forEach { it.deleteRecursively() }
                                            check(directory.mkdirs()) { "Unable to prepare file." }
                                            file.outputStream().use { runtime.exportFile(entry.path, it) }
                                        }
                                        openAlpineFileWithOtherApp(context, file)
                                    }.onFailure {
                                        directory.deleteRecursively()
                                        error = it.message ?: "Unable to open file."
                                    }
                                    loading = false
                                }
                            },
                            leadingIcon = { Icon(Icons.Rounded.Description, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { draftName = entry.name; dialog = AndroidFileDialog.Rename },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    )
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = {
                                pendingDownload = entry
                                selected = null
                                fileExportLauncher.launch(entry.name)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { dialog = AndroidFileDialog.Delete },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
        }
    }

    if (dialog != AndroidFileDialog.None) {
        AlertDialog(
            onDismissRequest = { dialog = AndroidFileDialog.None; selected = null },
            title = {
                Text(when (dialog) {
                    AndroidFileDialog.NewFile -> "New file"
                    AndroidFileDialog.NewFolder -> "New folder"
                    AndroidFileDialog.Rename -> "Rename"
                    AndroidFileDialog.Delete -> "Delete ${selected?.name.orEmpty()}?"
                    AndroidFileDialog.None -> ""
                })
            },
            text = {
                if (dialog == AndroidFileDialog.Delete) {
                    Text(if (selected?.isDirectory == true) "The folder and all of its contents will be permanently deleted." else "This item will be permanently deleted.")
                } else {
                    OutlinedTextField(value = draftName, onValueChange = { draftName = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = dialog == AndroidFileDialog.Delete || validAndroidFileName(draftName),
                    onClick = {
                        val operation = dialog
                        val item = selected
                        val name = draftName.trim()
                        dialog = AndroidFileDialog.None
                        scope.launch {
                            runCatching {
                                when (operation) {
                                    AndroidFileDialog.NewFile -> {
                                        val target = joinAndroidFilePath(path, name)
                                        require(!runtime.fileSystem.exists(target)) { "An item named $name already exists." }
                                        runtime.fileSystem.write(target, ByteArray(0))
                                    }
                                    AndroidFileDialog.NewFolder -> {
                                        val target = joinAndroidFilePath(path, name)
                                        require(!runtime.fileSystem.exists(target)) { "An item named $name already exists." }
                                        runtime.fileSystem.createDirectories(target)
                                    }
                                    AndroidFileDialog.Rename -> {
                                        val target = joinAndroidFilePath(path, name)
                                        if (item!!.path != target) runtime.move(item.path, target)
                                    }
                                    AndroidFileDialog.Delete -> runtime.fileSystem.remove(item!!.path, recursive = item.isDirectory)
                                    AndroidFileDialog.None -> Unit
                                }
                            }.onFailure { error = it.message ?: "File operation failed." }
                            selected = null
                            refresh()
                        }
                    },
                ) { Text(if (dialog == AndroidFileDialog.Delete) "Delete" else "Confirm") }
            },
            dismissButton = { TextButton(onClick = { dialog = AndroidFileDialog.None; selected = null }) { Text("Cancel") } },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("File operation failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun AndroidFileListItem(entry: AndroidAlpineFileEntry, onOpen: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null, tint = if (entry.isDirectory) AetherPrimary else AetherOnSurfaceVariant, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = AetherOnSurface, maxLines = 1)
            Text(if (entry.isDirectory) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.modifiedAtMillis)) else formatAndroidFileSize(entry.size), color = AetherOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "More") }
    }
}

@Composable
private fun AndroidFileGridItem(entry: AndroidAlpineFileEntry, onOpen: () -> Unit, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AetherSurface, RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "More") }
        }
        Icon(if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null, tint = if (entry.isDirectory) AetherPrimary else AetherOnSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(8.dp))
        Text(entry.name, maxLines = 2, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SoraEditorScreen(
    entry: AndroidAlpineFileEntry,
    initialContent: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var editor by remember(entry.path) { mutableStateOf<CodeEditor?>(null) }
    var wrapsLines by remember(entry.path) { mutableStateOf(false) }

    Scaffold(
        containerColor = AetherSettingsBackground,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(AetherSurface).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(entry.path, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { wrapsLines = !wrapsLines; editor?.setWordwrap(wrapsLines) }) {
                    Icon(Icons.Rounded.WrapText, "Toggle line wrapping", tint = if (wrapsLines) AetherPrimary else AetherOnSurfaceVariant)
                }
                IconButton(onClick = {
                    val content = editor?.text?.toString() ?: initialContent
                    onSave(content)
                }) { Icon(Icons.Rounded.Save, "Save") }
            }
        },
    ) { padding ->
        AndroidView(
            factory = {
                CodeEditor(context).apply {
                    setText(initialContent)
                    setLineNumberEnabled(true)
                    setWordwrap(wrapsLines)
                    editor = this
                }
            },
            update = { it.setWordwrap(wrapsLines) },
            onRelease = { released ->
                if (editor === released) editor = null
                released.release()
            },
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
        )
    }

    DisposableEffect(entry.path) {
        onDispose {
            // AndroidView owns release(); clear the reference so no stale editor is reused.
            editor = null
        }
    }
}


private suspend fun importAndroidDocument(
    context: Context,
    runtime: AndroidAlpineFileManagerRuntime,
    parentPath: String,
    document: DocumentFile,
) {
    val name = document.name?.trim().orEmpty()
    require(validAndroidFileName(name)) { "The selected item has an invalid name." }
    val target = joinAndroidFilePath(parentPath, name)
    require(!runtime.fileSystem.exists(target)) { "An item named $name already exists." }

    if (!document.isDirectory) {
        val input = context.contentResolver.openInputStream(document.uri)
            ?: error("Unable to read $name.")
        input.use { runtime.importFile(target, it) }
        return
    }

    runtime.fileSystem.createDirectories(target)
    try {
        document.listFiles().forEach { child ->
            importAndroidDocument(context, runtime, target, child)
        }
    } catch (error: Throwable) {
        runCatching { runtime.fileSystem.remove(target, recursive = true) }
        throw error
    }
}

private fun validAndroidFileName(name: String): Boolean =
    name.trim().let { it.isNotEmpty() && it != "." && it != ".." && '/' !in it }

private fun joinAndroidFilePath(parent: String, name: String): String =
    if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"

private fun formatAndroidFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
