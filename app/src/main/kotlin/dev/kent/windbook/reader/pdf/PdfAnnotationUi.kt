package dev.kent.windbook.reader.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import dev.kent.windbook.notes.labelRes
import dev.kent.windbook.notes.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** An explicit navigation event; each request is consumed even for the same annotation/book. */
data class PdfRevealRequest(val requestId: String, val anchor: PdfTextAnchor)

data class PdfAnnotationUi(
    val records: List<PdfTextAnnotation>, val selection: PdfTextSelection?, val activeIds: List<String>,
    val onSelect: (PdfTextSelection?)->Unit, val onExisting: (List<String>)->Unit,
    val onNote: ()->Unit, val onColor: (AnnotationColor)->Unit, val onDelete: (String)->Unit,
    val onClear: ()->Unit, val onNoText: (Boolean)->Unit, val saving: Boolean=false,
    val editorVisible: Boolean=false, val failed: Boolean=false,
) { val locked: Boolean get() = selection!=null || activeIds.isNotEmpty() || editorVisible }

/** Shares the page container's transform with the bitmap and tiles; every gesture is page-local. */
@Composable
internal fun PdfTextPageOverlay(source: PdfPageSource,page: Int,crop: CropBox,size: PageSize,ui: PdfAnnotationUi,
    onPageTap: (Offset)->Unit,onPageDoubleTap: (Offset)->Unit,motionSnapshot: ()->Any?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geometry=remember(size,crop,constraints.maxWidth,constraints.maxHeight) {
            PdfPageGeometry(size,crop,constraints.maxWidth.toFloat(),constraints.maxHeight.toFloat())
        }
        val currentUi by rememberUpdatedState(ui)
        val currentMotion by rememberUpdatedState(motionSnapshot)
        val scope=rememberCoroutineScope()
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var selecting by remember { mutableStateOf(false) }
        val queue=remember(source,page,geometry) {
            PdfSelectionQueue<Triple<PdfPoint,PdfPoint,Any?>,Pair<Any?,Result<PdfTextSelection?>>>(scope,
                work={ (start,end,motion) ->
                    val result=try { Result.success(source.selectText(page,start,end)) }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { Result.failure(e) }
                    motion to result
                },deliver={ (motion,result) ->
                    selecting=false
                    // Read live motion here too: a native result can race the snapshotFlow collector.
                    if(motion==currentMotion()) {
                        val selection=result.getOrNull()
                        if(selection!=null) currentUi.onSelect(selection) else currentUi.onNoText(!source.textSelectionAvailable)
                    }
                })
        }
        DisposableEffect(queue) { onDispose { queue.close() } }
        LaunchedEffect(queue) {
            // Observe per-pixel motion outside composition; only a pending selection needs invalidation.
            snapshotFlow { currentMotion() }.collect { queue.invalidate();selecting=false }
        }
        val selected=ui.selection?.takeIf { it.anchor.page==page }
        val saved=ui.records.filter { it.anchor?.page==page }
        val active=saved.filter { it.id in ui.activeIds }
        val anchor=selected?.anchor ?: active.singleOrNull()?.anchor
        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates=it }
            .pointerInput(source,page,geometry,saved,ui.locked) {
                detectTapGestures(
                    onLongPress={ tap ->
                        val point=geometry.toPage(PdfPoint(tap.x,tap.y)) ?: return@detectTapGestures
                        if(!source.textSelectionAvailable) currentUi.onNoText(true)
                        else { currentUi.onExisting(emptyList()); selecting=true; queue.submit(Triple(point,point,currentMotion())) }
                    },
                    onTap={ tap ->
                        queue.invalidate();selecting=false
                        val point=geometry.toPage(PdfPoint(tap.x,tap.y))
                        val hits=if(point==null) emptyList() else saved.filter { record ->
                            record.anchor!!.rects.any { it.contains(PdfPoint(point.x/size.width,point.y/size.height)) }
                        }
                        if(hits.isNotEmpty()) { queue.invalidate(); selecting=false; currentUi.onSelect(null); currentUi.onExisting(hits.map { it.id }) }
                        else if(currentUi.locked) { queue.invalidate(); selecting=false; currentUi.onClear() }
                        else onPageTap(tap)
                    },
                    onDoubleTap={ tap -> queue.invalidate();selecting=false;if(!currentUi.locked) onPageDoubleTap(tap) },
                )
            }) {
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                fun mark(anchor: PdfTextAnchor,color: Color,outline: Boolean) {
                    anchor.rects.forEach { r ->
                        val a=geometry.toDisplay(PdfPoint(r.left*anchor.width,r.top*anchor.height))
                        val b=geometry.toDisplay(PdfPoint(r.right*anchor.width,r.bottom*anchor.height))
                        val origin=Offset(a.x,a.y); val bounds=Size(b.x-a.x,b.y-a.y)
                        drawRect(color.copy(alpha=.3f),origin,bounds)
                        if(outline) drawRect(color,origin,bounds,style=Stroke(1.5.dp.toPx()))
                    }
                }
                saved.forEach { record ->
                    val a=requireNotNull(record.anchor)
                    mark(a,record.color.toComposeColor(),record.id in ui.activeIds)
                    if(record.body!=null) {
                        val p=geometry.toDisplay(PdfPoint(a.rects.last().right*a.width,a.rects.last().top*a.height))
                        drawCircle(record.color.toComposeColor(),3.dp.toPx(),Offset(p.x,p.y))
                    }
                }
                selected?.let { mark(it.anchor,Color(0xFF6B96CA),true) }
            }
            if(selected!=null) {
                listOf(true,false).forEach { startHandle ->
                    val point=if(startHandle) selected.start else selected.end
                    val position=geometry.toDisplay(point)
                    val handleSize=with(LocalDensity.current) { 24.dp.toPx() }
                    val description=stringResource(if(startHandle) R.string.notes_start_handle else R.string.notes_end_handle)
                    var moving by remember { mutableStateOf(point) }
                    var fixed by remember { mutableStateOf(point) }
                    Box(Modifier.offset { IntOffset((position.x-handleSize/2).roundToInt(),position.y.roundToInt()) }
                        .size(24.dp).background(MaterialTheme.colorScheme.primary,CircleShape)
                        .semantics { contentDescription=description }
                        .pointerInput(page,geometry,startHandle) {
                            detectDragGestures(onDragStart={
                                val latest=currentUi.selection ?: return@detectDragGestures
                                moving=if(startHandle) latest.start else latest.end
                                fixed=if(startHandle) latest.end else latest.start
                                dragging=true
                            },onDragEnd={dragging=false},onDragCancel={dragging=false}) { change,delta ->
                                change.consume()
                                moving=PdfPoint((moving.x+delta.x*crop.width*size.width/geometry.width).coerceIn(0f,size.width),
                                    (moving.y+delta.y*crop.height*size.height/geometry.height).coerceIn(0f,size.height))
                                selecting=true
                                queue.submit(if(startHandle) Triple(moving,fixed,currentMotion()) else Triple(fixed,moving,currentMotion()))
                            }
                        })
                }
            }
        }
        if(!ui.editorVisible && !dragging && !selecting && (anchor!=null || active.size>1)) {
            val density=LocalDensity.current
            val direction=LocalLayoutDirection.current
            val safeTop=WindowInsets.safeDrawing.getTop(density)
            val safeBottom=WindowInsets.safeDrawing.getBottom(density)
            val safeLeft=WindowInsets.safeDrawing.getLeft(density,direction)
            val safeRight=WindowInsets.safeDrawing.getRight(density,direction)
            val positionProvider=remember(coordinates,anchor,geometry,density,safeTop,safeBottom,safeLeft,safeRight) {
                object: PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
                        val rects=anchor?.rects.orEmpty()
                        val top=if(anchor!=null && rects.isNotEmpty()) geometry.toDisplay(PdfPoint(
                            (rects.minOf {it.left}+rects.maxOf {it.right})*anchor.width/2,rects.minOf {it.top}*anchor.height))
                            else PdfPoint(geometry.width/2,0f)
                        val bottom=if(anchor!=null && rects.isNotEmpty()) geometry.toDisplay(PdfPoint(rects.maxOf {it.right}*anchor.width,rects.maxOf {it.bottom}*anchor.height)) else top
                        val attached=coordinates?.takeIf {it.isAttached}
                        val a=attached?.localToWindow(Offset(top.x,top.y)) ?: Offset(anchorBounds.left.toFloat(),anchorBounds.top.toFloat())
                        val b=attached?.localToWindow(Offset(bottom.x,bottom.y)) ?: a
                        val handleScale=attached?.let { kotlin.math.abs(it.localToWindow(Offset(0f,1f)).y-it.localToWindow(Offset.Zero).y) } ?: 1f
                        val y=pdfSelectionPopupY(a.y,b.y,popupContentSize.height,safeTop,windowSize.height-safeBottom,density.density,handleScale)
                        return IntOffset((a.x-popupContentSize.width/2).roundToInt().coerceIn(safeLeft,
                            (windowSize.width-safeRight-popupContentSize.width).coerceAtLeast(safeLeft)),y)
                    }
                }
            }
            Popup(popupPositionProvider=positionProvider,onDismissRequest=ui.onClear) {
                Surface(shape=MaterialTheme.shapes.small,shadowElevation=6.dp,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                    var colors by remember(anchor,ui.activeIds) { mutableStateOf(false) }
                    Column(Modifier.padding(horizontal=6.dp,vertical=4.dp)) {
                        if(active.size>1) active.forEach { record ->
                            Text(record.body ?: record.quote.take(30),modifier=Modifier.clickable {ui.onExisting(listOf(record.id))}.padding(4.dp))
                        } else {
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.notes_color),fontSize=18.sp,modifier=Modifier.clickable(enabled=!ui.saving) {colors=!colors})
                                Text(stringResource(R.string.notes_note),fontSize=18.sp,modifier=Modifier.clickable(enabled=!ui.saving,onClick=ui.onNote))
                            }
                            if(colors) Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(top=6.dp)) {
                                AnnotationColor.entries.forEach { color ->
                                    val label=stringResource(color.labelRes())
                                    Box(Modifier.size(24.dp).background(color.toComposeColor(),CircleShape)
                                        .clickable(enabled=!ui.saving) {ui.onColor(color)}.semantics {contentDescription=label; this.selected=color==(active.singleOrNull()?.color ?: AnnotationColor.GOLD)})
                                }
                            }
                            active.singleOrNull()?.let { record ->
                                Text(stringResource(R.string.notes_delete),modifier=Modifier.clickable(enabled=!ui.saving) {ui.onDelete(record.id)}.padding(top=4.dp))
                            }
                            if(ui.failed) Text(stringResource(R.string.notes_failed),color=MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
