package dev.kent.windbook.reader

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.EffectiveDisplay
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.reader.pdf.PdfReaderView
import dev.kent.windbook.ui.components.ProgressLine
import dev.kent.windbook.ui.components.formatPercent
import dev.kent.windbook.ui.components.uppercaseForUi
import dev.kent.windbook.ui.icons.WindBookIcons

@Composable
fun ReaderRoute(bookId: String, onBack: () -> Unit, onReplaceReader: (String) -> Unit, annotationId: String? = null, revealRequestId: String? = null) {
    val viewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        key = bookId,
        creationCallback = { factory -> factory.create(bookId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val reconnectPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                require(result.data!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.reconnect(BookSource.External(uri.toString(),
                    DocumentKey(requireNotNull(uri.authority), DocumentsContract.getDocumentId(uri))))
            } catch (_: Exception) { /* Retain the reconnect screen when the provider denies the grant. */ }
        }
    }
    val onReconnect = {
        reconnectPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }

    ImmersiveReadingWindow()

    var encodingPicker by remember { mutableStateOf(false) }
    if (encodingPicker) androidx.compose.material3.AlertDialog(
        onDismissRequest = { encodingPicker = false },
        title = { Text(stringResource(R.string.reader_text_encoding)) },
        text = { TextEncodingChoices { encodingPicker = false; viewModel.chooseTextEncoding(it) } },
        confirmButton = { TextButton(onClick = { encodingPicker = false }) { Text(stringResource(R.string.reader_encoding_cancel)) } },
    )
    ReaderViewport {
        when (val current = state) {
            ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderUiState.Error -> ReaderErrorScreen(
                error = current.error,
                onBack = onBack,
                onRemove = { viewModel.removeBook(onRemoved = onBack) },
            )
            ReaderUiState.EncodingRequired -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.reader_encoding_required), style = MaterialTheme.typography.titleLarge)
                TextEncodingChoices(viewModel::chooseTextEncoding)
                TextButton(onClick = onBack) { Text(stringResource(R.string.reader_encoding_back)) }
            }
            ReaderUiState.Reconnect -> ReaderSourceMessage(
                R.string.reader_source_unavailable, R.string.reader_reconnect, onReconnect, onBack,
            )
            is ReaderUiState.Changed -> ReaderSourceMessage(
                R.string.reader_source_changed, R.string.reader_open_revision,
                { onReplaceReader(current.newBookId) }, onBack,
            )
            is ReaderUiState.HiddenMatch -> ReaderSourceMessage(
                R.string.reader_hidden_match, R.string.reader_restore, viewModel::restoreHiddenMatch, onBack,
            )
            is ReaderUiState.Redirect -> {
                LaunchedEffect(current.bookId) { onReplaceReader(current.bookId) }
            }
            is ReaderUiState.Ready -> ReaderReadyScreen(bookId,current,viewModel,onBack,annotationId,revealRequestId) {encodingPicker=true}
        }
    }
}

/** Reserve real system-bar and camera bounds once for every reader state and format. */
@Composable
internal fun ReaderViewport(
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(insets),
    ) { content() }
}

@Composable
internal fun ReaderSurface(state: ReaderUiState.Ready, viewModel: ReaderViewModel, controls: ReaderControlsViewModel, bookId: String = "", annotationId: String? = null, revealRequestId: String? = null) {
    val dispatcher = LocalKeyEventDispatcher.current
    val darkTheme = isSystemInDarkTheme()
    val typography by controls.typography.collectAsStateWithLifecycle()
    val navigation by controls.request.collectAsStateWithLifecycle()
    val hostRef = remember { mutableStateOf<ReaderHostFragment?>(null) }
    var selection by remember(state.session) {mutableStateOf<dev.kent.windbook.core.model.EpubTextAnchor?>(null)}
    var selectedAi by remember(state.session) {mutableStateOf(false)}
    var activeNote by remember(state.session) {mutableStateOf<String?>(null)}
    val noteLabel=stringResource(R.string.notes_note)
    val aiLabel=stringResource(R.string.ai_translate)
    val callbacks = remember(viewModel, state.session) {
        object : ReaderHostFragment.Callbacks {
            override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) =
                viewModel.onLocatorChanged(locatorJson, progression, chapterTitle, expectedSession = state.session)
            override fun onCenterTap() = viewModel.toggleOverlay()
            override fun onTextSelected(anchor: dev.kent.windbook.core.model.EpubTextAnchor,ai: Boolean) {selection=anchor;selectedAi=ai;activeNote=null}
            override fun onDecorationActivated(id: String) {activeNote=id;selection=null}
        }
    }

    AndroidFragment<ReaderHostFragment>(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(readerPageBackground(typography,darkTheme)))
            // Text never touches the screen edge or sits under a punch-hole camera (spec §3.6).
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Vertical).union(WindowInsets(top = 16.dp, bottom = 16.dp)),
            ),
        arguments = ReaderHostFragment.arguments(state.session.surface),
    ) { fragment ->
        hostRef.value = fragment
        fragment.noteActionLabel=noteLabel
        fragment.aiActionLabel=aiLabel
        // A re-created fragment (rotation) must open where the reader is now, not where the book was opened.
        fragment.bind(
            session = state.session,
            initialLocatorJson = viewModel.latestLocatorJson ?: state.initialLocatorJson,
            callbacks = callbacks,
            darkTheme = darkTheme,
            typography = typography,
        )
    }

    if(state.session.format==dev.kent.windbook.core.model.BookFormat.EPUB) dev.kent.windbook.reader.epub.EpubNotesReader(
        bookId,state.session,viewModel,hostRef.value,selection,selectedAi,activeNote,annotationId,revealRequestId,
        clear={selection=null;activeNote=null;hostRef.value?.clearSelection()})

    LaunchedEffect(hostRef.value,typography,darkTheme) {
        selection=null
        hostRef.value?.applyTypography(typography,darkTheme)
    }

    LaunchedEffect(hostRef.value,navigation?.id) {
        val pending=navigation ?: return@LaunchedEffect
        val fragment=hostRef.value ?: return@LaunchedEffect
        controls.completed(pending.id,fragment.navigate(pending.locatorJson))
    }

    DisposableEffect(dispatcher) {
        val turner = KeyEventDispatcher.PageTurner { forward -> hostRef.value?.turnPage(forward) ?: false }
        dispatcher.register(turner)
        onDispose { dispatcher.unregister(turner) }
    }
}

/** Keep status information visible; only the navigation bar hides during reading. */
@Composable
private fun ImmersiveReadingWindow() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

@Composable
fun ReaderOverlay(
    visible: Boolean,
    title: String,
    chapterTitle: String?,
    progression: Double,
    canMarkFinished: Boolean,
    onMarkFinished: () -> Unit,
    onBack: () -> Unit,
    pdfDisplay: EffectiveDisplay? = null,
    onPdfLayoutChange: (PdfLayout) -> Unit = {},
    onCropMarginsChange: (Boolean) -> Unit = {},
    onFitPdfPage: () -> Unit = {},
    onContents: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onSearch: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onEncoding: (() -> Unit)? = null,
    canBookmark: Boolean = true,
    onSeek: ((Double) -> Unit)? = null,
    canSeek: Boolean = true,
    pdfPage: Int? = null,
    pdfPageCount: Int? = null,
    onPageEntry: () -> Unit = {},
    canReturn: Boolean = false,
    onReturn: () -> Unit = {},
    seekFraction: Double = progression,
) {
    var preview by remember { mutableStateOf<Double?>(null) }
    val bar = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bar)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(WindBookIcons.Back, contentDescription = stringResource(R.string.back)) }
                Text(
                    title.uppercaseForUi(),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.08.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick=onContents) {Icon(WindBookIcons.List,contentDescription=stringResource(R.string.reader_contents))}
                ReaderMoreMenu(pdfDisplay,onPdfLayoutChange,onCropMarginsChange,onFitPdfPage,
                    onBookmark,onBookmarks,onSearch,onAppearance,onEncoding,canBookmark)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(bar)
                    .navigationBarsPadding(),
            ) {
                if(onSeek!=null) ReaderPositionSlider(seekFraction,canSeek,Modifier.padding(horizontal=16.dp),onPreview={preview=it},onSeek=onSeek) else ProgressLine(progression)
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if(pdfPage!=null && pdfPageCount!=null) TextButton(onClick=onPageEntry) {
                        Text(stringResource(R.string.reader_page_count,preview?.let {dev.kent.windbook.core.model.seekPage(it,pdfPageCount)?.plus(1)} ?: pdfPage,pdfPageCount))
                    }
                    if(canReturn) IconButton(onClick=onReturn) {Icon(WindBookIcons.Back,contentDescription=stringResource(R.string.reader_return))}
                    // Chapter on the left (ellipsized), percentage on the right, never one joined caption.
                    val chapter = chapterTitle?.takeIf { it.isNotBlank() }
                    if (chapter != null) {
                        Text(
                            text = chapter,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (pdfPage == null) {
                        Text(
                            text = formatPercent(preview ?: seekFraction),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        )
                    }
                    if (canMarkFinished) {
                        TextButton(onClick = onMarkFinished) { Text(stringResource(R.string.mark_finished)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderMoreMenu(display: EffectiveDisplay?,onLayoutChange: (PdfLayout)->Unit,onCropChange: (Boolean)->Unit,
    onFitPage: ()->Unit,onBookmark: ()->Unit,onBookmarks: ()->Unit,onSearch: ()->Unit,onAppearance: ()->Unit,
    onEncoding: (() -> Unit)?,canBookmark: Boolean) {
    var open by remember {mutableStateOf(false)}
    var showView by remember {mutableStateOf(false)}
    Box {
        IconButton(onClick={open=true}) {Icon(WindBookIcons.MoreHoriz,stringResource(R.string.reader_more),modifier=Modifier.rotate(90f))}
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            DropdownMenuItem(text={Text(stringResource(R.string.reader_bookmark_add))},enabled=canBookmark,onClick={open=false;onBookmark()})
            DropdownMenuItem(text={Text(stringResource(R.string.reader_bookmarks))},onClick={open=false;onBookmarks()})
            if(display==null) {
                DropdownMenuItem(text={Text(stringResource(R.string.reader_search))},onClick={open=false;onSearch()})
                DropdownMenuItem(text={Text(stringResource(R.string.reader_appearance))},onClick={open=false;onAppearance()})
            } else {
                val viewLabel=stringResource(R.string.cd_view_mode)
                DropdownMenuItem(text={Text(viewLabel)},onClick={open=false;showView=true},modifier=Modifier.semantics {contentDescription=viewLabel})
            }
            if(onEncoding!=null) DropdownMenuItem(text={Text(stringResource(R.string.reader_text_encoding))},onClick={open=false;onEncoding()})
        }
    }
    if(showView && display!=null) ReaderViewDialog(display,onLayoutChange,onCropChange,onFitPage) {showView=false}
}

@Composable
private fun ReaderViewDialog(display: EffectiveDisplay,onLayoutChange: (PdfLayout)->Unit,onCropChange: (Boolean)->Unit,onFitPage: ()->Unit,onDismiss: ()->Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.cd_view_mode))},text={
        Column {
            DropdownMenuItem(text={Text(stringResource(R.string.fit_page))},onClick={onDismiss();onFitPage()})
            listOf(PdfLayout.VERTICAL to R.string.layout_vertical,PdfLayout.PAGED to R.string.layout_paged).forEach {(layout,label)->
                DropdownMenuItem(text={Text(stringResource(label))},leadingIcon={RadioButton(selected=display.layout==layout,onClick=null)},
                    onClick={onDismiss();if(display.layout!=layout) onLayoutChange(layout)})
            }
            HorizontalDivider()
            DropdownMenuItem(text={Text(stringResource(R.string.crop_margins))},leadingIcon={Checkbox(display.cropMargins,onCheckedChange=null)},
                onClick={onDismiss();onCropChange(!display.cropMargins)},modifier=Modifier.semantics {role=Role.Checkbox;toggleableState=ToggleableState(display.cropMargins)})
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.close))}})
}

@Composable
private fun ReaderErrorScreen(error: ReaderError, onBack: () -> Unit, onRemove: () -> Unit) {
    val message = when (error) {
        ReaderError.NOT_FOUND -> R.string.reader_error_not_found
        ReaderError.FILE_MISSING -> R.string.reader_error_file_missing
        ReaderError.CANNOT_OPEN -> R.string.reader_error_cannot_open
        ReaderError.TEXT_TOO_LARGE -> R.string.reader_text_too_large
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        if (error == ReaderError.FILE_MISSING) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_from_library)) }
        }
    }
}

@Composable
internal fun ReaderSourceMessage(message: Int, action: Int, onAction: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(stringResource(action)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun TextEncodingChoices(onChoose: (dev.kent.windbook.reader.formats.TextEncoding) -> Unit) {
    val labels = listOf(R.string.reader_encoding_utf8, R.string.reader_encoding_utf16_le, R.string.reader_encoding_utf16_be, R.string.reader_encoding_windows1258)
    Column {
        dev.kent.windbook.reader.formats.TextEncoding.entries.forEachIndexed { index, encoding ->
            TextButton(onClick = { onChoose(encoding) }) { Text(stringResource(labels[index])) }
        }
    }
}
