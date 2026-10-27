package dev.kent.windbook.reader.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import dev.kent.windbook.core.model.MAX_PDF_ZOOM
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val PDF_VERTICAL_TAG = "pdf-vertical"
const val PDF_PAGED_TAG = "pdf-paged"

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
    initialZoom: Float = 1f,
    initialZoomX: Float = 0f,
    onZoomChanged: (zoom: Float, zoomX: Float) -> Unit = { _, _ -> },
    annotations: PdfAnnotationUi? = null,
    reveal: PdfRevealRequest? = null,
    fitRequestId: Long = 0L,
) {
    var currentPage by rememberSaveable(source, reveal?.requestId) { mutableIntStateOf((reveal?.anchor?.page ?: initialPage).coerceIn(0, source.pageCount - 1)) }
    var revealing by remember(source,reveal?.requestId) { mutableStateOf(reveal!=null) }
    var plan by remember(source, cropMargins) { mutableStateOf<CropPlan?>(null) }
    val pageCrops = remember(source, cropMargins) { mutableStateMapOf<Int, CropBox>() }
    LaunchedEffect(source, cropMargins) {
        if (cropMargins) plan = source.cropPlan()
    }
    val cropFor: (Int) -> CropBox = { page ->
        if (!cropMargins || (revealing && page==reveal?.anchor?.page)) CropBox.FULL else pageCrops[page] ?: plan?.boxFor(page) ?: CropBox.FULL
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
        if(reveal!=null && page!=reveal.anchor.page) revealing=false
        onPageChanged(page)
    }
    key(reveal?.requestId) { when (layout) {
        PdfLayout.VERTICAL -> VerticalPages(
            source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier,
            if(revealing) 1f else initialZoom, if(revealing) 0f else initialZoomX,
            { z,x -> if(!revealing) onZoomChanged(z,x) }, annotations, reveal?.anchor?.takeIf { revealing },
        )
        PdfLayout.PAGED -> PagedPages(source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier,annotations,fitRequestId)
    } }
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
    initialZoom: Float,
    initialZoomX: Float,
    onZoomChanged: (Float, Float) -> Unit,
    annotations: PdfAnnotationUi?,
    revealAnchor: dev.kent.windbook.core.model.PdfTextAnchor?,
) {
    val gesturesLocked by rememberUpdatedState(annotations?.locked==true)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Read once: later saves come back through the same parameters and must not reset a gesture.
    var zoom by remember { mutableFloatStateOf(initialZoom.coerceIn(1f, MAX_PDF_ZOOM)) }
    val reportZoom by rememberUpdatedState(onZoomChanged)
    val details = remember { mutableStateMapOf<Int, Pair<PixelRegion, ImageBitmap>>() }
    var pinching by remember { mutableStateOf(false) }
    LaunchedEffect(zoom) {
        delay(150)
        pinching = false
    }
    val density = LocalDensity.current

    ReportPage(listState, onPageChanged)

    // Back to the saved horizontal position once the zoomed width is known.
    LaunchedEffect(Unit) {
        if (zoom > 1f && initialZoomX > 0f) {
            val max = snapshotFlow { hScroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
            hScroll.scrollTo((initialZoomX.coerceIn(0f, 1f) * max).roundToInt())
        }
    }

    // Saves the zoom per book (spec §3) once a pinch, double-tap or sideways drag has settled.
    LaunchedEffect(Unit) {
        var saved = zoom to initialZoomX.coerceIn(0f, 1f)
        snapshotFlow { Triple(zoom, hScroll.value, hScroll.isScrollInProgress || pinching) }
            .distinctUntilChanged()
            .collectLatest { (z, value, moving) ->
                if (moving) return@collectLatest
                delay(500)
                val max = hScroll.maxValue
                val x = if (z <= 1f || max <= 0 || max == Int.MAX_VALUE) 0f else value.toFloat() / max
                if (abs(z - saved.first) > 0.01f || abs(x - saved.second) > 0.01f) {
                    saved = z to x
                    reportZoom(z, x)
                }
            }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val pageWidthPx = (viewportW * zoom).roundToInt()
        LaunchedEffect(revealAnchor,viewportW,viewportH) {
            revealAnchor?.let { anchor ->
                val size=source.measure(anchor.page)
                val height=renderedHeight(size,CropBox.FULL,viewportW)
                listState.scrollToItem(anchor.page,dev.kent.windbook.core.model.pdfRevealScrollOffset(anchor,height,viewportH))
            }
        }

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
            if(gesturesLocked) return@PageTurnKeys true
            scope.launch {
                if (zoom > 1.01f) {
                    readingOrderStep(forward, hScroll, listState, stepX = (viewportW * 0.9f).roundToInt(), stepY = viewportH * 0.9f)
                } else {
                    listState.animateScrollBy(viewportH * 0.9f * if (forward) 1 else -1)
                }
            }
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

        val zoomAt: (Offset)->Unit = { tap ->
            if(!gesturesLocked) {
                val target=if(zoom>1.01f) 1f else DOUBLE_TAP_ZOOM
                val ratio=target/zoom
                zoom=target
                scope.launch {
                    hScroll.scrollTo(((hScroll.value+tap.x)*ratio-tap.x).roundToInt().coerceAtLeast(0))
                    listState.scrollBy((listState.firstVisibleItemScrollOffset+tap.y)*(ratio-1f))
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .testTag(PDF_VERTICAL_TAG)
                .pinchZoom { change, centroid ->
                    if(gesturesLocked) return@pinchZoom
                    pinching = true
                    val next = (zoom * change).coerceIn(1f, MAX_PDF_ZOOM)
                    val ratio = next / zoom
                    if (ratio != 1f) {
                        zoom = next
                        scope.launch {
                            hScroll.scrollTo(((hScroll.value + centroid.x) * ratio - centroid.x).roundToInt().coerceAtLeast(0))
                            listState.scrollBy((listState.firstVisibleItemScrollOffset + centroid.y) * (ratio - 1f))
                        }
                    }
                }
                .pointerInput(gesturesLocked) {
                    detectTapGestures(onTap={if(!gesturesLocked) onCenterTap()},onDoubleTap=zoomAt)
                }
                .horizontalScroll(hScroll, enabled = zoom > 1f && !gesturesLocked),
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = !gesturesLocked,
                modifier = Modifier.width(with(density) { pageWidthPx.toDp() }).height(with(density) { viewportH.toDp() }),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = source.pageCount, key = { it }) { page ->
                    var measured by remember(page) { mutableStateOf(source.pageSize(page)) }
                    LaunchedEffect(page, resolveCrop) {
                        measured=source.measure(page)
                        resolveCrop(page)
                    }
                    val crop = cropFor(page)
                    val heightPx = renderedHeight(measured, crop, pageWidthPx)
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
                        annotations?.let { ui ->
                            PdfTextPageOverlay(source,page,crop,measured,ui,
                                onPageTap={onCenterTap()},onPageDoubleTap={ tap ->
                                    val offset=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.index==page}?.offset ?: 0
                                    zoomAt(Offset(tap.x-hScroll.value,tap.y+offset))
                                },motionSnapshot=remember(listState,hScroll) {
                                    { Triple(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,hScroll.value,pinching) }
                                })
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
    annotations: PdfAnnotationUi?,
    fitRequestId: Long,
) {
    val gesturesLocked by rememberUpdatedState(annotations?.locked==true)
    val pager = rememberPagerState(initialPage = startPage) { source.pageCount }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(fitRequestId) {
        zoom = 1f
        pan = Offset.Zero
    }

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
            if(gesturesLocked) return@PageTurnKeys true
            val target = (pager.currentPage + if (forward) 1 else -1).coerceIn(0, source.pageCount - 1)
            scope.launch { pager.animateScrollToPage(target) }
            true
        }

        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom == 1f && !gesturesLocked,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize().testTag(PDF_PAGED_TAG),
        ) { page ->
            var size by remember(source, page) { mutableStateOf<dev.kent.windbook.core.model.PageSize?>(null) }
            var measureFailed by remember(source, page) { mutableStateOf(false) }
            LaunchedEffect(source, page, resolveCrop) {
                try {
                    size = source.measure(page)
                    measureFailed = false
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    size = null
                    measureFailed = true
                }
                if (size != null) resolveCrop(page)
            }
            val turnAt: (Offset)->Unit = { tap ->
                if(!gesturesLocked) when {
                    tap.x<viewportW*.25f -> scope.launch {pager.animateScrollToPage((page-1).coerceAtLeast(0))}
                    tap.x>viewportW*.75f -> scope.launch {pager.animateScrollToPage((page+1).coerceAtMost(source.pageCount-1))}
                    else -> onCenterTap()
                }
            }
            // Native rendering must never draw a differently-shaped page into fallback geometry.
            val measured = size
            if (measured == null) {
                Box(
                    Modifier.fillMaxSize().pointerInput(page, gesturesLocked) {
                        detectTapGestures(onTap = turnAt)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (measureFailed) Text(stringResource(R.string.page_render_failed), color = Color.White)
                }
                return@HorizontalPager
            }
            val crop = cropFor(page)
            val pageAspect = (crop.width * measured.width) / (crop.height * measured.height)
            val fitW = if (viewportW / pageAspect <= viewportH) viewportW else (viewportH * pageAspect).roundToInt()
            val fitH = (fitW / pageAspect).roundToInt()
            val zoomAt: (Offset)->Unit = { tap ->
                if(!gesturesLocked) {
                    if(zoom>1f) {zoom=1f;pan=Offset.Zero}
                    else {zoom=DOUBLE_TAP_ZOOM;pan=(Offset(viewportW/2f,viewportH/2f)-tap)*(DOUBLE_TAP_ZOOM-1f)}
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page,gesturesLocked) {
                        detectTapGestures(onTap=turnAt,onDoubleTap=zoomAt)
                    }
                    .then(if (zoom > 1f && !gesturesLocked) Modifier.pointerInput(Unit) { detectDragGestures { _, drag -> pan += drag } } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            if (pager.currentPage == page) {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                        }
                        .testTag("pdf-page-$page"),
                ) {
                    PageBitmap(source,page,crop,if(zoom>1f && pager.currentPage==page) fitW*2 else fitW,Modifier.fillMaxSize())
                    annotations?.let { ui ->
                        fun viewportPoint(tap: Offset)=Offset((viewportW-fitW)/2f+fitW/2f+(tap.x-fitW/2f)*zoom+pan.x,
                            (viewportH-fitH)/2f+fitH/2f+(tap.y-fitH/2f)*zoom+pan.y)
                        PdfTextPageOverlay(source,page,crop,measured,ui,{turnAt(viewportPoint(it))},{zoomAt(viewportPoint(it))},motionSnapshot=remember(pager) {
                            { Triple(pager.currentPage to pager.currentPageOffsetFraction,zoom,pan) }
                        })
                    }
                }
            }
        }
    }
}

/** Reports the page being read: the first visible one, or the last when the list can't scroll further. */
@Composable
private fun ReportPage(listState: LazyListState, onPageChanged: (Int) -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            // Pages are shorter than the screen, so at the end the last page is never the first visible one.
            if (listState.canScrollForward) {
                listState.firstVisibleItemIndex
            } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex
            }
        }.distinctUntilChanged().collect(onPageChanged)
    }
}

/** One page bitmap; a failed page shows a message and leaves its neighbours alone. */
@Composable
private fun PageBitmap(source: PdfPageSource, page: Int, crop: CropBox, renderWidthPx: Int, modifier: Modifier) {
    var image by remember(source, page, crop, renderWidthPx) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source, page, crop, renderWidthPx) { mutableStateOf(false) }
    LaunchedEffect(source, page, crop, renderWidthPx) {
        try {
            image = source.render(page, crop, renderWidthPx).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    Box(modifier.background(Color.White)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
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

/**
 * Volume keys while zoomed read like a page: across the line, then back to the left edge and down (spec §3).
 * Each step overlaps the previous view by 10 %.
 */
private suspend fun readingOrderStep(forward: Boolean, h: ScrollState, list: LazyListState, stepX: Int, stepY: Float) {
    if (forward) {
        if (h.value < h.maxValue) {
            h.animateScrollTo(minOf(h.value + stepX, h.maxValue))
        } else {
            h.animateScrollTo(0)
            list.animateScrollBy(stepY)
        }
    } else {
        if (h.value > 0) {
            h.animateScrollTo(maxOf(h.value - stepX, 0))
        } else {
            h.animateScrollTo(h.maxValue)
            list.animateScrollBy(-stepY)
        }
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
