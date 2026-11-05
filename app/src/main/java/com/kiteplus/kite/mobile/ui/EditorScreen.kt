package com.kiteplus.kite.mobile.ui

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.kiteplus.kite.mobile.AppContainer
import com.kiteplus.kite.mobile.data.ContentRepository
import com.kiteplus.kite.mobile.data.DraftPatch
import com.kiteplus.kite.mobile.data.KindInfo
import com.kiteplus.kite.mobile.data.local.ContentEntity
import com.kiteplus.kite.mobile.api.model.Draft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

/**
 * The editor, laid out the way the admin page is: what the item says at the
 * top, the text in the middle, and everything that is not the text behind one
 * properties sheet — its address, its standing, its categories, and the fields
 * this site's content type declares.
 *
 * Text is written to the database as it settles — there is no save button,
 * because on a phone the moment you leave the app is not a moment you choose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * The editor. Same shape as the admin page: title, summary and cover at the top,
 * the text in the middle, and everything that is not the text behind one
 * properties sheet — address, standing, terms, and the fields this site's content
 * type declares.
 * 
 * There is no save button. The text is written to the database as it settles,
 * because the moment the author leaves the app is not a moment they choose.
 */
fun EditorScreen(
    container: AppContainer,
    id: String,
    onBack: () -> Unit,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()

    var row by remember(id) { mutableStateOf<ContentEntity?>(null) }

    // What the author typed outlives a rotation: the screen is rebuilt, the
    // text is not thrown away and re-read a few hundred milliseconds stale.
    var loaded by rememberSaveable(id) { mutableStateOf(false) }
    var title by rememberSaveable(id) { mutableStateOf("") }
    var body by rememberSaveable(id, stateSaver = BodySaver) { mutableStateOf(TextFieldValue("")) }
    var slug by rememberSaveable(id) { mutableStateOf("") }
    var status by rememberSaveable(id) { mutableStateOf("draft") }
    var publishedAt by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var terms by remember(id) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var meta by remember(id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var serverWords by remember { mutableStateOf<Int?>(null) }

    var sheet by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingTerm by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var previewHtml by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    // Which slot the next picked picture goes into: the text, or the cover.
    var uploadTarget by remember { mutableStateOf("body") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = uploadTarget
        scope.launch {
            message = "上传中…"
            try {
                // A bundle is a folder, so the item has to exist on the
                // server before a file can go beside it.
                var serverId = id
                if (serverId.startsWith(ContentRepository.LOCAL_PREFIX)) {
                    container.syncEngine.sync()
                    serverId = repository.load(id)?.id ?: id
                }
                if (serverId.startsWith(ContentRepository.LOCAL_PREFIX)) {
                    message = "这篇还没同步到电脑上，先连上再插图片"
                    return@launch
                }
                val picked = withContext(Dispatchers.IO) { readImage(context, uri) }
                val media = container.uploader.upload(serverId, picked.name, picked.mime, picked.bytes)
                if (target == "cover") {
                    meta = meta + ("cover" to media.link)
                    message = "封面已设为 ${media.link}"
                } else {
                    body = insertImage(body, media.link, picked.name)
                    message = "已插入 ${media.link}"
                }
                if (media.locationRemoved == true) message = "$message（拍摄地点已去掉）"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "上传失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
    val pickImage: (String) -> Unit = { target ->
        uploadTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(id) {
        val loadedRow = repository.load(id) ?: return@LaunchedEffect
        row = loadedRow
        // A restored screen already holds what was typed; only a fresh open
        // takes the text from the database.
        if (loaded) return@LaunchedEffect
        title = loadedRow.title
        body = TextFieldValue(loadedRow.body)
        slug = loadedRow.slug
        status = loadedRow.status
        publishedAt = loadedRow.publishedAt
        terms = ContentRepository.termsOf(loadedRow)
        meta = metaValues(ContentRepository.metaOf(loadedRow))
        loaded = true
    }

    val type = row?.let { repository.typeOf(it.kind) }

    // Everything the author can change, written once it stops changing. The
    // whole patch goes each time: the row is small and a partial write is one
    // more thing that can disagree with what the screen shows.
    LaunchedEffect(id, loaded, title, body.text, slug, status, publishedAt, terms, meta) {
        if (!loaded) return@LaunchedEffect
        delay(600.milliseconds)
        repository.saveDraft(
            id,
            DraftPatch(
                title = title,
                body = body.text,
                slug = slug,
                status = status,
                publishedAt = publishedAt,
                taxonomies = terms,
                meta = metaJsonOf(meta, type),
            ),
        )
        row = repository.load(id)
    }

    fun currentDraft(): Draft = Draft(
        kind = row?.kind ?: "post",
        title = title.ifBlank { "未命名" },
        slug = slug.ifBlank { null },
        status = status,
        body = body.text,
        meta = metaMapOf(meta, type),
        taxonomies = terms.ifEmpty { null },
        publishedAt = publishedAt,
    )

    // The count the page will print, asked of the server once the typing stops.
    // Offline it falls back to counting here, which is only a stand-in.
    LaunchedEffect(id, body.text, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(800)
        serverWords = null
        serverWords = runCatching {
            container.render.wordCount(currentDraft(), id.takeIf { !it.startsWith(ContentRepository.LOCAL_PREFIX) })
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        StatusChip(status) { statusMenu = true }
                        DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                            listOf(
                                "draft" to "草稿",
                                "published" to "已发布",
                                "scheduled" to "定时发布",
                                "archived" to "归档",
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (value == status) "✓ $label" else label) },
                                    onClick = {
                                        statusMenu = false
                                        status = value
                                        if (value == "scheduled" && publishedAt == null) pickingDate = true
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        previewHtml = null
                        previewError = null
                        showPreview = true
                    }) { Text("预览") }
                    IconButton(onClick = { sheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "属性")
                    }
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("删除文章") },
                            onClick = {
                                overflow = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("标题") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            BodyToolbar(
                onHeading = { body = prefixLines(body, "## ") },
                onBold = { body = wrap(body, "**") },
                onItalic = { body = wrap(body, "_") },
                onLink = { body = wrap(body, "[", "](https://)") },
                onQuote = { body = prefixLines(body, "> ") },
                onList = { body = prefixLines(body, "- ") },
                onCode = { body = wrap(body, "`") },
            onImage = { pickImage("body") },
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("正文，Markdown") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${serverWords ?: wordCount(body.text)} 字 · ${if (row?.dirty == true) "待上传" else "已同步"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            row?.let { current ->
                if (current.hasConflict) {
                    ConflictCard(
                        theirsTitle = current.conflictTitle,
                        theirsBody = current.conflictBody,
                        onKeepLocal = {
                            scope.launch {
                                repository.keepLocal(id)
                                row = repository.load(id)
                            }
                        },
                        onKeepTheirs = {
                            scope.launch {
                                repository.keepTheirs(id)
                                val fresh = repository.load(id)
                                row = fresh
                                if (fresh != null) {
                                    title = fresh.title
                                    body = TextFieldValue(fresh.body)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (sheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = sheetState) {
            PropertiesSheet(
                type = type,
                slug = slug,
                onSlug = { slug = it },
                status = status,
                publishedAt = publishedAt,
                onPickDate = { pickingDate = true },
                onClearDate = { publishedAt = null },
                terms = terms,
                onAddTerm = { taxonomy -> editingTerm = taxonomy },
                onRemoveTerm = { taxonomy, term ->
                    terms = terms + (taxonomy to terms[taxonomy].orEmpty().filterNot { it == term })
                },
                meta = meta,
                onMeta = { key, value -> meta = meta + (key to value) },
                onPickCover = { pickImage("cover") },
            )
        }
    }

    if (showPreview) {
        // Rendered when the sheet opens rather than on every keystroke: a
        // preview that chases the cursor costs a request per character.
        LaunchedEffect(showPreview) {
            if (previewHtml == null && previewError == null) {
                try {
                    previewHtml = container.render.preview(
                        currentDraft(),
                        id.takeIf { !it.startsWith(ContentRepository.LOCAL_PREFIX) },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    previewError = "预览失败：${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
        PreviewDialog(
            baseUrl = previewBase(container.settings.serverUrl, row?.url),
            html = previewHtml,
            error = previewError,
            onDismiss = { showPreview = false },
        )
    }

    editingTerm?.let { taxonomy ->
        var text by remember(taxonomy) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingTerm = null },
            title = { Text("添加 $taxonomy") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = text.trim()
                    if (value.isNotEmpty()) {
                        terms = terms + (taxonomy to (terms[taxonomy].orEmpty() + value).distinct())
                    }
                    editingTerm = null
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { editingTerm = null }) { Text("取消") } },
        )
    }

    if (pickingDate) {
        val picker = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { publishedAt = rfc3339(it) }
                    if (publishedAt != null) status = "scheduled"
                    pickingDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("取消") } },
        ) {
            DatePicker(state = picker)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这篇文章？") },
            text = { Text("文章会进回收站，下一次同步时在服务器上一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repository.delete(id)
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
/**
 * The article's standing, shown as a chip that opens the menu to change it.
 */
private fun StatusChip(status: String, onClick: () -> Unit) {
    val label = when (status) {
        "published" -> "已发布"
        "scheduled" -> "定时"
        "archived" -> "归档"
        else -> "草稿"
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = when (status) {
                "published" -> MaterialTheme.colorScheme.primary
                "scheduled" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    )
}

@Composable
/**
 * The little row of Markdown actions above the text. They insert the syntax around
 * the selection rather than styling it: the file on disk is Markdown, and the site
 * build is what turns it into a page.
 */
private fun BodyToolbar(
    onHeading: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onLink: () -> Unit,
    onQuote: () -> Unit,
    onList: () -> Unit,
    onCode: () -> Unit,
    onImage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            "H2" to onHeading,
            "B" to onBold,
            "I" to onItalic,
            "•" to onList,
            "❝" to onQuote,
            "🔗" to onLink,
            "<>" to onCode,
            "🖼" to onImage,
        ).forEach { (label, action) ->
            TextButton(onClick = action) { Text(label) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
/**
 * The bottom sheet for everything the text is not: the address, the publish date,
 * one chip row per taxonomy, and each field the content type declares. Field types
 * decide the control — a switch for a boolean, a numeric keyboard for a number, a
 * plain box plus a cover picker for an image.
 */
private fun PropertiesSheet(
    type: KindInfo?,
    slug: String,
    onSlug: (String) -> Unit,
    status: String,
    publishedAt: String?,
    onPickDate: () -> Unit,
    onClearDate: () -> Unit,
    terms: Map<String, List<String>>,
    onAddTerm: (String) -> Unit,
    onRemoveTerm: (String, String) -> Unit,
    meta: Map<String, String>,
    onMeta: (String, String) -> Unit,
    onPickCover: () -> Unit,
) {
    val taxonomies = (type?.taxonomies.orEmpty() + terms.keys).distinct()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("属性", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = slug,
            onValueChange = onSlug,
            label = { Text("文件地址") },
            prefix = { Text(type?.slugPrefix.orEmpty()) },
            singleLine = true,
            placeholder = { Text("留空则由标题生成") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (status == "scheduled" || publishedAt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(publishedAt ?: "还没选日期", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onPickDate) { Text("选择日期") }
                if (publishedAt != null) {
                    TextButton(onClick = onClearDate) { Text("清空") }
                }
            }
        }

        if (taxonomies.isNotEmpty()) {
            HorizontalDivider()
            Text("分类", style = MaterialTheme.typography.titleSmall)
            taxonomies.forEach { taxonomy ->
                Text(taxonomy, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    terms[taxonomy].orEmpty().forEach { term ->
                        AssistChip(
                            onClick = { onRemoveTerm(taxonomy, term) },
                            label = { Text("$term ✕") },
                        )
                    }
                    AssistChip(
                        onClick = { onAddTerm(taxonomy) },
                        label = { Text("＋ 添加") },
                    )
                }
            }
        }

        if (!type?.fields.isNullOrEmpty()) {
            HorizontalDivider()
            Text("描述", style = MaterialTheme.typography.titleSmall)
            type.fields.forEach { field ->
                if (field.type == "boolean") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(field.label.orEmpty().ifBlank { field.key })
                        Switch(
                            checked = meta[field.key] == "true",
                            onCheckedChange = { onMeta(field.key, it.toString()) },
                        )
                    }
                } else {
                    if (field.type == "image") {
                        OutlinedButton(onClick = onPickCover, modifier = Modifier.fillMaxWidth()) {
                            Text(if (meta[field.key].isNullOrBlank()) "从相册选一张作封面" else "换一张封面")
                        }
                    }
                    OutlinedTextField(
                        value = meta[field.key].orEmpty(),
                        onValueChange = { onMeta(field.key, it) },
                        label = { Text(field.label.orEmpty().ifBlank { field.key }) },
                        singleLine = field.type != "text",
                        keyboardOptions = if (field.type == "number") {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else {
                            KeyboardOptions.Default
                        },
                        supportingText = if (field.type == "image" && !field.key.contains("cover")) {
                            { Text("图片上传还没做，这里只能填已有路径") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
/**
 * The two-version card shown when the same article changed on both sides.
 */
private fun ConflictCard(
    theirsTitle: String?,
    theirsBody: String?,
    onKeepLocal: () -> Unit,
    onKeepTheirs: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("这篇文章在别处改过", style = MaterialTheme.typography.titleSmall)
            theirsBody?.let {
                Text(it.take(400), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onKeepLocal) { Text("保留本地") }
                TextButton(onClick = onKeepTheirs) { Text("保留远端") }
            }
            theirsTitle?.let { Text("远端标题：$it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// --- editing helpers -------------------------------------------------------

/**
 * Keeps the text and the caret across a configuration change. Only the string
 * is stored; a selection is two integers in front of it.
 */
private val BodySaver = Saver<TextFieldValue, String>(
    save = { "${'$'}{it.selection.start},${'$'}{it.selection.end},${'$'}{it.text}" },
    restore = {
        val first = it.indexOf(',')
        val second = it.indexOf(',', first + 1)
        if (first < 0 || second < 0) {
            TextFieldValue(it)
        } else {
            TextFieldValue(
                text = it.substring(second + 1),
                selection = TextRange(
                    it.substring(0, first).toIntOrNull() ?: 0,
                    it.substring(first + 1, second).toIntOrNull() ?: 0,
                ),
            )
        }
    },
)

/**
 * Puts a marker around the selection (or around the cursor when nothing is selected).
 */
private fun wrap(value: TextFieldValue, before: String, after: String = before): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val chosen = value.text.substring(start, end)
    val text = value.text.replaceRange(start, end, before + chosen + after)
    val cursor = start + before.length + chosen.length
    return TextFieldValue(text = text, selection = TextRange(cursor))
}

/** Puts the marker at the start of the line the cursor is on. */
private fun prefixLines(value: TextFieldValue, prefix: String): TextFieldValue {
    val start = value.selection.min
    val lineStart = value.text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let {
        if (it < 0) 0 else it + 1
    }
    val text = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    return TextFieldValue(text = text, selection = TextRange(start + prefix.length))
}

@Composable
/**
 * The page as the site would render it, in a WebView with scripting off: the HTML
 * comes from the server, and a preview is not a place to execute anything that
 * happened to be in the article.
 */
private fun PreviewDialog(
    baseUrl: String,
    html: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("预览", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                when {
                    error != null -> Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )

                    html == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                // The page is the site's own output; scripts in
                                // a preview would be a way to run code from an
                                // article, so they stay off.
                                settings.javaScriptEnabled = false
                                settings.loadsImagesAutomatically = true
                            }
                        },
                        update = { view ->
                            // Loading on every recomposition would restart the
                            // page while it is being read.
                            if (view.tag != html) {
                                view.tag = html
                                view.loadDataWithBaseURL(
                                    baseUrl.trimEnd('/') + "/",
                                    html,
                                    "text/html",
                                    "utf-8",
                                    null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The taxonomy map in the shape the row stores it.
 */
private fun termsJsonOf(values: Map<String, List<String>>): JsonObject {
    val out = JsonObject()
    values.forEach { (taxonomy, terms) ->
        if (terms.isNotEmpty()) {
            val list = com.google.gson.JsonArray()
            terms.forEach { list.add(it) }
            out.add(taxonomy, list)
        }
    }
    return out
}

/**
 * Inserts an uploaded image at the cursor as a Markdown image, on its own line: a
 * picture jammed into the middle of a sentence reads badly on the site.
 */
private fun insertImage(value: TextFieldValue, link: String, fileName: String): TextFieldValue {
    val alt = fileName.substringBeforeLast('.').ifBlank { "图片" }
    val snippet = "\n![$alt]($link)\n"
    // After the cursor rather than replacing the selection: a picture just
    // inserted is selected, and the next one would replace it.
    val at = value.selection.max
    val text = value.text.substring(0, at) + snippet + value.text.substring(at)
    return TextFieldValue(text = text, selection = TextRange(at + snippet.length))
}

private class PickedImage(val bytes: ByteArray, val name: String, val mime: String)

/**
 * Reads the picked picture into bytes. The picker gives a content URI, which is
 * valid only for the life of the process, so the bytes travel with the upload
 * instead of the URI.
 */
private fun readImage(context: Context, uri: Uri): PickedImage {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: ("image-" + System.currentTimeMillis() + ".jpg")
    val mime = resolver.getType(uri) ?: "image/jpeg"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("读不到这张图片")
    return PickedImage(bytes, name, mime)
}

/**
 * A local count, used only when the server cannot be asked; the number the page prints comes from the renderer.
 */
private fun wordCount(text: String): Int = text.count { !it.isWhitespace() }

/**
 * The row's stored metadata as plain text, the way the form binds to it.
 */
private fun metaValues(meta: JsonObject): Map<String, String> =
    meta.entrySet().mapNotNull { (key, value) ->
        if (value.isJsonPrimitive) key to value.asString else null
    }.toMap()

/**
 * The same values as [metaJsonOf], typed the way the server's draft wants them:
 * a declared boolean travels as a boolean, everything else as text.
 */
private fun metaMapOf(values: Map<String, String>, type: KindInfo?): Map<String, Any>? {
    val out = values.mapNotNull { (key, value) ->
        val isBoolean = type?.fields?.firstOrNull { it.key == key }?.type == "boolean"
        when {
            isBoolean -> key to (value == "true")
            value.isNotBlank() -> key to value
            else -> null
        }
    }.toMap()
    return out.ifEmpty { null }
}

/**
 * The form's text values back into the JSON the row stores.
 */
private fun metaJsonOf(values: Map<String, String>, type: KindInfo?): JsonObject {
    val out = JsonObject()
    values.forEach { (key, value) ->
        val isBoolean = type?.fields?.firstOrNull { it.key == key }?.type == "boolean"
        when {
            isBoolean -> out.addProperty(key, value == "true")
            value.isNotBlank() -> out.addProperty(key, value)
        }
    }
    return out
}

/**
 * A picked date as the RFC 3339 instant the server expects for a publish time.
 */
private fun rfc3339(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))

/**
 * The address an article's relative links were written against.
 *
 * Images live beside the article and are linked by bare file name — the same
 * way the admin page links them — so a preview loaded against the site root
 * asks for them at the root and shows nothing. The item's own address is the
 * base those links expect; an item the server has never seen has none.
 */
private fun previewBase(root: String, itemUrl: String?): String {
    val site = root.trimEnd('/')
    val path = itemUrl?.takeIf { it.isNotBlank() } ?: return "$site/"
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val suffix = if (path.startsWith("/")) path else "/$path"
    return "$site$suffix"
}
