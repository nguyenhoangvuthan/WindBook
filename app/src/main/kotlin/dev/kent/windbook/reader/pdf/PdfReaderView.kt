package dev.kent.windbook.reader.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

const val PDF_VERTICAL_TAG = "pdf-vertical"
const val PDF_PAGED_TAG = "pdf-paged"

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val PagedSurround = Color(0xFF141210)

/**
 * WindBook's PDF view (spec §3.1). [initialPage] is read once; afterwards the view owns the current page
 * and keeps it when [layout] changes.
 */
@Composable
fun PdfReaderView(
    source: PdfPageSource,
    layout: PdfLayout,
    cropMargins: Boolean,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by rememberSaveable(source) { mutableIntStateOf(initialPage.coerceIn(0, source.pageCount - 1)) }
    var plan by remember(source, cropMargins) { mutableStateOf<CropPlan?>(null) }
    val pageCrops = remember(source, cropMargins) { mutableStateMapOf<Int, CropBox>() }
    LaunchedEffect(source, cropMargins) {
        if (cropMargins) plan = source.cropPlan()
    }
    val cropFor: (Int) -> CropBox = { page ->
        if (!cropMargins) CropBox.FULL else pageCrops[page] ?: plan?.boxFor(page) ?: CropBox.FULL
    }
    // A new lambda once the plan lands: pages already on screen re-run their effect and get their own box.
    val resolveCrop: suspend (Int) -> Unit = remember(source, cropMargins, plan) {
        { page ->
            val p = plan
            if (cropMargins && p != null && page !in pageCrops) pageCrops[page] = source.pageCrop(page, p)
        }
    }
    val report: (Int) -> Unit = { page ->
        currentPage = page
        onPageChanged(page)
    }
    when (layout) {
        PdfLayout.VERTICAL -> VerticalPages(source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier)
        PdfLayout.PAGED -> PagedPages(source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier)
    }
}

@Composable
private fun VerticalPages(
    source: PdfPageSource,
    startPage: Int,
    cropFor: (Int) -> CropBox,
    resolveCrop: suspend (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    val details = remember { mutableStateMapOf<Int, Pair<PixelRegion, ImageBitmap>>() }
    var pinching by remember { mutableStateOf(false) }
    LaunchedEffect(zoom) {
        delay(150)
        pinching = false
    }
    val density = LocalDensity.current

    LaunchedEffect(listState) {
        snapshotFlow {
            // Pages are shorter than the screen, so at the end the last page is never the first visible one.
            if (listState.canScrollForward) listState.firstVisibleItemIndex
            else listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect(onPageChanged)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val pageWidthPx = (viewportW * zoom).roundToInt()

        LaunchedEffect(zoom, pageWidthPx) {
            details.clear()
            if (zoom <= 1.05f) return@LaunchedEffect
            snapshotFlow {
                Triple(listState.isScrollInProgress || hScroll.isScrollInProgress || pinching, listState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }, hScroll.value)
            }.distinctUntilChanged().collect { (moving, visible, scrollX) ->
                if (moving) return@collect
                // Only visible pages keep a tile (spec §3.3); the rest would pile up while reading zoomed in.
                val onScreen = visible.map { it.first }.toSet()
                details.keys.filter { it !in onScreen }.forEach { details.remove(it) }
                delay(150)
                for ((page, offsetY) in visible) {
                    val crop = cropFor(page)
                    val pageH = renderedHeight(source.pageSize(page), crop, pageWidthPx)
                    val top = (-offsetY).coerceAtLeast(0)
                    val bottom = (viewportH - offsetY).coerceAtMost(pageH)
                    if (bottom <= top) continue
                    val region = PixelRegion(scrollX, top, viewportW.coerceAtMost(pageWidthPx - scrollX), bottom - top)
                    if (details[page]?.first == region) continue
                    runCatching { source.render(page, crop, pageWidthPx, region) }
                        .onSuccess { details[page] = region to it.asImageBitmap() }
                }
            }
        }

        PageTurnKeys { forward ->
            scope.launch { listState.animateScrollBy(viewportH * 0.9f * if (forward) 1 else -1) }
            true
        }

        // Prefetch ±2 pages into the cache so scrolling lands on ready bitmaps (spec §3.3).
        LaunchedEffect(listState, source, viewportW) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { first ->
                for (p in (first - 2)..(first + 3)) {
                    if (p !in 0 until source.pageCount) continue
                    try {
                        source.render(p, cropFor(p), viewportW)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A broken page shows its own message when it scrolls in.
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .testTag(PDF_VERTICAL_TAG)
                .pinchZoom { change, centroid ->
                    pinching = true
                    val next = (zoom * change).coerceIn(1f, MAX_ZOOM)
                    val ratio = next / zoom
                    if (ratio != 1f) {
                        zoom = next
                        scope.launch {
                            hScroll.scrollTo(((hScroll.value + centroid.x) * ratio - centroid.x).roundToInt().coerceAtLeast(0))
                            listState.scrollBy((listState.firstVisibleItemScrollOffset + centroid.y) * (ratio - 1f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCenterTap() },
                        onDoubleTap = { tap ->
                            val target = if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM
                            val ratio = target / zoom
                            zoom = target
                            scope.launch {
                                hScroll.scrollTo(((hScroll.value + tap.x) * ratio - tap.x).roundToInt().coerceAtLeast(0))
                                listState.scrollBy((listState.firstVisibleItemScrollOffset + tap.y) * (ratio - 1f))
                            }
                        },
                    )
                }
                .horizontalScroll(hScroll, enabled = zoom > 1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(with(density) { pageWidthPx.toDp() }).height(with(density) { viewportH.toDp() }),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = source.pageCount, key = { it }) { page ->
                    LaunchedEffect(page, resolveCrop) {
                        source.measure(page)
                        resolveCrop(page)
                    }
                    val crop = cropFor(page)
                    val heightPx = renderedHeight(source.pageSize(page), crop, pageWidthPx)
                    Box(Modifier.width(with(density) { pageWidthPx.toDp() }).height(with(density) { heightPx.toDp() })) {
                        PageBitmap(source, page, crop, renderWidthPx = viewportW, modifier = Modifier.fillMaxSize())
                        details[page]?.let { (region, tile) ->
                            Image(
                                tile,
                                contentDescription = null,
                                modifier = Modifier
                                    .offset { IntOffset(region.x, region.y) }
                                    .size(with(density) { region.width.toDp() }, with(density) { region.height.toDp() }),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedPages(
    source: PdfPageSource,
    startPage: Int,
    cropFor: (Int) -> CropBox,
    resolveCrop: suspend (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier,
) {
    val pager = rememberPagerState(initialPage = startPage) { source.pageCount }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            zoom = 1f
            pan = Offset.Zero
            onPageChanged(page)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PagedSurround)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight

        PageTurnKeys { forward ->
            val target = (pager.currentPage + if (forward) 1 else -1).coerceIn(0, source.pageCount - 1)
            scope.launch { pager.animateScrollToPage(target) }
            true
        }

        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom == 1f,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize().testTag(PDF_PAGED_TAG),
        ) { page ->
            LaunchedEffect(page, resolveCrop) {
                source.measure(page)
                resolveCrop(page)
            }
            val crop = cropFor(page)
            val size = source.pageSize(page)
            val pageAspect = (crop.width * size.width) / (crop.height * size.height)
            val fitW = if (viewportW / pageAspect <= viewportH) viewportW else (viewportH * pageAspect).roundToInt()
            val fitH = (fitW / pageAspect).roundToInt()
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = { tap ->
                                when {
                                    tap.x < viewportW * 0.25f -> scope.launch { pager.animateScrollToPage((page - 1).coerceAtLeast(0)) }
                                    tap.x > viewportW * 0.75f -> scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(source.pageCount - 1)) }
                                    else -> onCenterTap()
                                }
                            },
                            onDoubleTap = { tap ->
                                if (zoom > 1f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    zoom = DOUBLE_TAP_ZOOM
                                    pan = (Offset(viewportW / 2f, viewportH / 2f) - tap) * (DOUBLE_TAP_ZOOM - 1f)
                                }
                            },
                        )
                    }
                    .then(if (zoom > 1f) Modifier.pointerInput(Unit) { detectDragGestures { _, drag -> pan += drag } } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                PageBitmap(
                    source = source,
                    page = page,
                    crop = crop,
                    renderWidthPx = if (zoom > 1f && pager.currentPage == page) fitW * 2 else fitW,
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            if (pager.currentPage == page) {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                        },
                )
            }
        }
    }
}

/** One page bitmap; a failed page shows a message and leaves its neighbours alone. */
@Composable
private fun PageBitmap(source: PdfPageSource, page: Int, crop: CropBox, renderWidthPx: Int, modifier: Modifier) {
    var image by remember(page, crop, renderWidthPx) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(page, crop, renderWidthPx) { mutableStateOf(false) }
    LaunchedEffect(page, crop, renderWidthPx) {
        try {
            image = source.render(page, crop, renderWidthPx).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    Box(modifier.background(Color.White)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        if (failed) {
            Text(
                stringResource(R.string.page_render_failed),
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Registers hardware page-turn keys (volume, page up/down) while this view is shown. */
@Composable
private fun PageTurnKeys(turn: (forward: Boolean) -> Boolean) {
    val dispatcher = LocalKeyEventDispatcher.current
    DisposableEffect(dispatcher, turn) {
        val turner = KeyEventDispatcher.PageTurner { forward -> turn(forward) }
        dispatcher.register(turner)
        onDispose { dispatcher.unregister(turner) }
    }
}

/** Two-finger pinch only; one-finger drags fall through to scrolling. */
private fun Modifier.pinchZoom(onZoom: (change: Float, centroid: Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val change = event.calculateZoom()
                if (change != 1f) onZoom(change, event.calculateCentroid(useCurrent = true))
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
