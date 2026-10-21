package dev.kent.windbook.reader

import androidx.activity.compose.LocalActivity
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
fun ReaderRoute(bookId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        key = bookId,
        creationCallback = { factory -> factory.create(bookId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overlay by viewModel.overlayVisible.collectAsStateWithLifecycle()
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val chapterTitle by viewModel.chapterTitle.collectAsStateWithLifecycle()
    val canMarkFinished by viewModel.canMarkFinished.collectAsStateWithLifecycle()

    ImmersiveReadingWindow()

    when (val current = state) {
        ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ReaderUiState.Error -> ReaderErrorScreen(
            error = current.error,
            onBack = onBack,
            onRemove = { viewModel.removeBook(onRemoved = onBack) },
        )
        is ReaderUiState.Ready -> Box(Modifier.fillMaxSize()) {
            val pdf = current.session as? PdfSession
            val pdfDisplay by viewModel.pdfDisplay.collectAsStateWithLifecycle()
            if (pdf != null) {
                pdfDisplay?.let { display ->
                    val initialPage = remember(pdf) { viewModel.initialPdfPage() }
                    PdfReaderView(
                        source = pdf.pages,
                        layout = display.layout,
                        cropMargins = display.cropMargins,
                        initialPage = initialPage,
                        onPageChanged = viewModel::onPdfPageChanged,
                        onCenterTap = viewModel::toggleOverlay,
                        initialZoom = display.zoom,
                        initialZoomX = display.zoomX,
                        onZoomChanged = viewModel::setPdfZoom,
                    )
                }
            } else {
                ReaderSurface(current, viewModel)
            }
            ReaderOverlay(
                visible = overlay,
                title = current.title,
                chapterTitle = chapterTitle,
                progression = progression,
                canMarkFinished = canMarkFinished,
                onMarkFinished = viewModel::markFinished,
                onBack = onBack,
                pdfDisplay = if (pdf != null) pdfDisplay else null,
                onPdfLayoutChange = viewModel::setPdfLayout,
                onCropMarginsChange = viewModel::setCropMargins,
            )
        }
    }
}

@Composable
private fun ReaderSurface(state: ReaderUiState.Ready, viewModel: ReaderViewModel) {
    val dispatcher = LocalKeyEventDispatcher.current
    val darkTheme = isSystemInDarkTheme()
    val hostRef = remember { mutableStateOf<ReaderHostFragment?>(null) }
    val callbacks = remember(viewModel) {
        object : ReaderHostFragment.Callbacks {
            override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) =
                viewModel.onLocatorChanged(locatorJson, progression, chapterTitle)
            override fun onCenterTap() = viewModel.toggleOverlay()
        }
    }

    AndroidFragment<ReaderHostFragment>(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(epubPageBackground(darkTheme)))
            // Text never touches the screen edge or sits under a punch-hole camera (spec §3.6).
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Vertical).union(WindowInsets(top = 16.dp, bottom = 16.dp)),
            ),
        arguments = ReaderHostFragment.arguments(state.session.format),
    ) { fragment ->
        hostRef.value = fragment
        // A re-created fragment (rotation) must open where the reader is now, not where the book was opened.
        fragment.bind(
            session = state.session,
            initialLocatorJson = viewModel.latestLocatorJson ?: state.initialLocatorJson,
            callbacks = callbacks,
            darkTheme = darkTheme,
        )
    }

    DisposableEffect(dispatcher) {
        val turner = KeyEventDispatcher.PageTurner { forward -> hostRef.value?.turnPage(forward) ?: false }
        dispatcher.register(turner)
        onDispose { dispatcher.unregister(turner) }
    }
}

/** Hides system bars (swipe to peek) and keeps the screen on while the reader is shown. */
@Composable
private fun ImmersiveReadingWindow() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
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
) {
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
                if (pdfDisplay != null) {
                    ViewMenu(pdfDisplay, onPdfLayoutChange, onCropMarginsChange)
                } else {
                    // Balances the back button so the title is truly centred.
                    Spacer(Modifier.width(48.dp))
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(bar)
                    .navigationBarsPadding(),
            ) {
                ProgressLine(progression)
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val percent = formatPercent(progression)
                    Text(
                        text = chapterTitle?.let { "$it · $percent" } ?: percent,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canMarkFinished) {
                        TextButton(onClick = onMarkFinished) { Text(stringResource(R.string.mark_finished)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewMenu(
    display: EffectiveDisplay,
    onLayoutChange: (PdfLayout) -> Unit,
    onCropChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(WindBookIcons.ViewMode, contentDescription = stringResource(R.string.cd_view_mode))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                PdfLayout.VERTICAL to R.string.layout_vertical,
                PdfLayout.PAGED to R.string.layout_paged,
            ).forEach { (layout, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = display.layout == layout, onClick = null) },
                    onClick = {
                        open = false
                        if (display.layout != layout) onLayoutChange(layout)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.crop_margins)) },
                leadingIcon = { Checkbox(checked = display.cropMargins, onCheckedChange = null) },
                onClick = {
                    open = false
                    onCropChange(!display.cropMargins)
                },
                // TalkBack hears "Crop margins, checkbox, checked".
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(display.cropMargins)
                },
            )
        }
    }
}

@Composable
private fun ReaderErrorScreen(error: ReaderError, onBack: () -> Unit, onRemove: () -> Unit) {
    val message = when (error) {
        ReaderError.NOT_FOUND -> R.string.reader_error_not_found
        ReaderError.FILE_MISSING -> R.string.reader_error_file_missing
        ReaderError.CANNOT_OPEN -> R.string.reader_error_cannot_open
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
