package dev.kent.windbook.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Hosts a Readium navigator fragment. Readium navigators can't be restored without their
 * publication, so a dummy factory is installed in onCreate and [bind] always replaces the
 * navigator with a fresh one positioned at the given locator (rotation, process death).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderHostFragment : Fragment() {

    interface Callbacks {
        fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?)
        fun onTextSelected(anchor: dev.kent.windbook.core.model.EpubTextAnchor, ai: Boolean) {}
        fun onDecorationActivated(id: String) {}
        fun onCenterTap()
    }

    var noteActionLabel: String = ""
    var aiActionLabel: String = ""
    private var boundSession: ReadiumReaderSession? = null
    private var annotationRecords=emptyList<dev.kent.windbook.core.model.PdfTextAnnotation>()
    private var pendingBind: (() -> Unit)? = null
    private var callbacks: Callbacks? = null
    private var locatorJob: Job? = null
    private var restoringAnchor = false
    private var restoreJob: Job? = null
    private var appliedTypography: Pair<dev.kent.windbook.core.model.ReaderTypography,Boolean>? = null

    private val navigator: Navigator?
        get() = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val surface = NavigationSurface.valueOf(requireArguments().getString(ARG_SURFACE, NavigationSurface.PUBLICATION.name))
        require(surface == NavigationSurface.PUBLICATION) { "ReaderHostFragment requires a publication surface" }
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        childFragmentManager.registerFragmentLifecycleCallbacks(object: androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(manager: androidx.fragment.app.FragmentManager,fragment: Fragment,view: View,state: Bundle?) {
                blockPublicationNetwork(view)
            }
        },true)
        super.onCreate(savedInstanceState)
        // A restored (dummy) navigator throws if it reaches onResume, so drop it now;
        // bind() adds a fresh one at the right locator.
        childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { restored ->
            childFragmentManager.commitNow { remove(restored) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentContainerView(requireContext()).apply { id = R.id.windbook_navigator_container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingBind?.invoke()
        pendingBind = null
    }

    fun bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks, darkTheme: Boolean, typography: dev.kent.windbook.core.model.ReaderTypography = dev.kent.windbook.core.model.ReaderTypography()) {
        require(session.surface == NavigationSurface.PUBLICATION) { "ReaderHostFragment requires a publication session" }
        this.callbacks = callbacks
        val readium = session as ReadiumReaderSession
        if (readium === boundSession) {
            applyTypography(typography,darkTheme)
            return
        }
        if (view == null) {
            pendingBind = { bind(session, initialLocatorJson, callbacks, darkTheme, typography) }
            return
        }
        boundSession = readium
        val initialLocator = initialLocatorJson?.toLocatorOrNull()

        childFragmentManager.fragmentFactory = EpubNavigatorFactory(readium.publication)
            .createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = typographyPreferences(typography,darkTheme),
                configuration = EpubNavigatorFragment.Configuration(servedAssets = listOf("fonts/.*")).apply {
                    registerJavascriptInterface("WindBookNotes") {object {
                        @android.webkit.JavascriptInterface fun activate(id:String) {
                            view?.post {if(boundSession===readium && annotationRecords.any {"wb"+it.id.replace(Regex("[^a-zA-Z0-9]"),"")==id}) callbacks?.onDecorationActivated(annotationRecords.first {"wb"+it.id.replace(Regex("[^a-zA-Z0-9]"),"")==id}.id)}
                        }
                    }}
                    if(session.format==dev.kent.windbook.core.model.BookFormat.EPUB && ReaderNavigationController(session).reflowable) selectionActionModeCallback=selectionActions(readium)
                    addFontFamilyDeclaration(org.readium.r2.navigator.preferences.FontFamily("Literata")) {
                        addFontFace { addSource("/fonts/literata.ttf") }
                    }
                },
            )
        childFragmentManager.commitNow {
            childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { remove(it) }
            add(R.id.windbook_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }
        appliedTypography = typography to darkTheme
        val restore=initialLocator?.takeIf {it.locations.otherLocations["windbookTextOffset"] is Number}
        restoringAnchor=restore!=null
        attachListeners(restore?.toJsonString())
    }

    fun applyTypography(value: dev.kent.windbook.core.model.ReaderTypography, darkTheme: Boolean) {
        val next=value to darkTheme
        if(appliedTypography==next) return
        clearSelection()
        (navigator as? EpubNavigatorFragment)?.submitPreferences(typographyPreferences(value,darkTheme))
        appliedTypography=next
    }

    /** A deliberate jump completes only when its resource and requested anchor are visible. */
    suspend fun navigate(locatorJson: String): NavigationResult {
        readerMoved()
        return navigateTo(locatorJson)
    }

    private suspend fun navigateTo(locatorJson: String): NavigationResult {
        val current=navigator as? EpubNavigatorFragment ?: return NavigationResult.FAILED
        val expected=boundSession ?: return NavigationResult.FAILED
        val target=locatorJson.toLocatorOrNull() ?: return NavigationResult.FAILED
        if(expected.publication.linkWithHref(target.href)==null) return NavigationResult.FAILED
        return try {
            val script=navigationVisibilityScript(target)
            suspend fun arrived(): Boolean {
                val coarse=current.currentLocator.value
                if(coarse.href!=target.href) return false
                val visible=script?.let {current.evaluateJavascript(it)=="true"}
                return navigationTargetMatches(target,coarse,visible)
            }
            if(arrived()) return NavigationResult.UNCHANGED
            if(!current.go(target,false)) return NavigationResult.FAILED
            val scrollScript=navigationScrollScript(target)
            kotlinx.coroutines.delay(100)
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                while(!arrived()) {
                    if(boundSession!==expected) return@withTimeoutOrNull NavigationResult.FAILED
                    if(current.currentLocator.value.href==target.href && scrollScript!=null) current.evaluateJavascript(scrollScript)
                    kotlinx.coroutines.delay(80)
                }
                reportLocation(current,current.currentLocator.value)
                NavigationResult.COMPLETED
            } ?: NavigationResult.FAILED
        } catch(e: CancellationException) {throw e}
        catch(_: Exception) {NavigationResult.FAILED}
    }

    private suspend fun reportLocation(current: Navigator,locator: org.readium.r2.shared.publication.Locator) {
        if(restoringAnchor) return
        val visible=try {kotlinx.coroutines.withTimeoutOrNull(700) {(current as? EpubNavigatorFragment)?.evaluateJavascript(visibleReadingLocatorScript)?.let { json ->
            json.toLocatorOrNull()?.copy(href=locator.href)
        }}}
        catch(e: CancellationException) {throw e}
        catch(_: Exception) {null}
        if(current!==navigator || current.currentLocator.value!=locator) return
        callbacks?.onLocatorChanged(semanticReadingLocator(locator,visible).toJsonString(),
            readingProgression(locator.locations.position,boundSession?.positionCount ?: 0,locator.locations.totalProgression),locator.title)
    }

    /** Used for volume keys / page keys. */
    fun turnPage(forward: Boolean): Boolean {
        val overflowable = navigator as? OverflowableNavigator ?: return false
        readerMoved()
        return if (forward) overflowable.goForward() else overflowable.goBackward()
    }

    /** The reader chose a new place, so a pending restore stops and locations are saved again. */
    private fun readerMoved() {
        if(!restoringAnchor) return
        restoringAnchor=false
        restoreJob?.cancel()
        restoreJob=null
    }

    /** The theme the EPUB navigator is rendering with; null for PDF or before bind. */
    internal val epubTheme: Theme?
        get() = (navigator as? EpubNavigatorFragment)?.settings?.value?.theme

    private fun attachListeners(restore: String?) {
        val current = navigator ?: return
        (current as? org.readium.r2.navigator.DecorableNavigator)?.addDecorationListener("windbook-notes",object: org.readium.r2.navigator.DecorableNavigator.Listener {
            override fun onDecorationActivated(event: org.readium.r2.navigator.DecorableNavigator.OnActivatedEvent): Boolean {
                if(current!==navigator) return false
                callbacks?.onDecorationActivated(event.decoration.id);return true
            }
        })
        (current as? OverflowableNavigator)?.apply {
            // Edge taps and arrow keys turn pages and end a pending restore. A touch while the book is still
            // opening is not a choice of place (most drags turn nothing), so a swipe ends only a restore that gave up.
            val turns=DirectionalNavigationAdapter(this)
            addInputListener(object : InputListener {
                override fun onTap(event: TapEvent)=turns.onTap(event).also {if(it) readerMoved()}
                override fun onKey(event: KeyEvent)=turns.onKey(event).also {if(it) readerMoved()}
                override fun onDrag(event: DragEvent): Boolean {
                    if(event.type==DragEvent.Type.Start && restoreJob?.isActive!=true) readerMoved()
                    return turns.onDrag(event)
                }
            })
            addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    callbacks?.onCenterTap()
                    return true
                }
            })
        }
        locatorJob?.cancel()
        locatorJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val expected=boundSession
                if(restore!=null && restoringAnchor) restoreJob=launch {
                    val epub=current as? EpubNavigatorFragment ?: return@launch
                    val target=restore.toLocatorOrNull() ?: return@launch
                    val visibility=navigationVisibilityScript(target)
                    // The page can still re-flow after the anchor first shows (Readium adds its paginated
                    // padding late), so keep it on screen until one layout has held for the settle window.
                    val settled=kotlinx.coroutines.withTimeoutOrNull(RESTORE_DEADLINE_MS) {
                        epub.currentLocator.first {it.href==target.href}
                        val settling=RestoreSettling(RESTORE_SETTLE_MS)
                        while(boundSession===expected) {
                            val visible=visibility!=null && epub.evaluateJavascript(visibility)=="true"
                            val layout=parsePageLayout(epub.evaluateJavascript(pageLayoutScript))
                            if(settling.observe(android.os.SystemClock.uptimeMillis(),layout,visible)) return@withTimeoutOrNull true
                            if(!visible) navigateTo(restore)
                            kotlinx.coroutines.delay(RESTORE_POLL_MS)
                        }
                        false
                    }==true
                    // Giving up keeps the saved position: nothing is reported until the reader moves.
                    if(settled && boundSession===expected && current===navigator && restoringAnchor) {
                        restoringAnchor=false
                        reportLocation(current,current.currentLocator.value)
                    }
                }
                var href: String?=null
                current.currentLocator.collectLatest { locator ->
                    if(href!=null && href!=locator.href.toString()) clearSelection()
                    href=locator.href.toString();applyAnnotations(annotationRecords);reportLocation(current,locator)
                }
            }
        }
    }

    private fun blockPublicationNetwork(view: View) {
        if(view is android.webkit.WebView) {
            view.settings.blockNetworkLoads=true
            view.settings.allowFileAccess=false
            view.settings.allowContentAccess=false
        }
        if(view is ViewGroup) for(i in 0 until view.childCount) blockPublicationNetwork(view.getChildAt(i))
    }

    fun clearSelection() { (navigator as? org.readium.r2.navigator.SelectableNavigator)?.clearSelection() }

    suspend fun captureSelection(): dev.kent.windbook.core.model.EpubTextAnchor? {
        val expected=boundSession ?: return null
        if(expected.format!=dev.kent.windbook.core.model.BookFormat.EPUB || !ReaderNavigationController(expected).reflowable) return null
        val current=navigator as? EpubNavigatorFragment ?: return null
        val selection=current.currentSelection() ?: return null
        val captured=current.evaluateJavascript(epubSelectionScript)
        if(expected!==boundSession || current!==navigator || current.currentLocator.value.href!=selection.locator.href) return null
        return epubSelectionAnchor(selection.locator,captured)
    }
    private fun selectionActions(expected: ReadiumReaderSession)=object: android.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: android.view.ActionMode,menu: android.view.Menu): Boolean {
            menu.clear()
            menu.add(0,1,0,noteActionLabel).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(0,2,1,aiActionLabel).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }
        override fun onPrepareActionMode(mode: android.view.ActionMode,menu: android.view.Menu)=false
        override fun onActionItemClicked(mode: android.view.ActionMode,item: android.view.MenuItem): Boolean {
            if(item.itemId !in 1..2) return false
            viewLifecycleOwner.lifecycleScope.launch {
                val anchor=captureSelection()
                if(anchor!=null && boundSession===expected) callbacks?.onTextSelected(anchor,item.itemId==2)
                mode.finish()
            }
            return true
        }
        override fun onDestroyActionMode(mode: android.view.ActionMode) {}
    }
    suspend fun applyAnnotations(records: List<dev.kent.windbook.core.model.PdfTextAnnotation>) {
        annotationRecords=records
        val current=navigator as? EpubNavigatorFragment ?: return
        if(current.evaluateJavascript(setupExactHighlightsScript)!="true") return
        val colors=listOf("#E7B63D","#78B77B","#70A8D9","#E891B1","#B28BD4")
        val active=records.mapNotNull {a -> a.epubAnchor?.locatorJson?.toLocatorOrNull()?.takeIf {it.href==current.currentLocator.value.href}?.let {a to it}}
        val styles=active.joinToString("\n") {(a,_) -> "::highlight(wb${a.id.replace(Regex("[^a-zA-Z0-9]"),"")}){background-color:${colors[a.color.ordinal]}80;}"}
        current.evaluateJavascript("(function(){let style=document.getElementById('wb-note-colors');if(!style){style=document.createElement('style');style.id='wb-note-colors';document.head.append(style);}style.textContent=${org.json.JSONObject.quote(styles)};return true;})()")
        active.forEach {(a,locator) ->
            exactHighlightScript(locator,"wb"+a.id.replace(Regex("[^a-zA-Z0-9]"),""),colors[a.color.ordinal])?.let {current.evaluateJavascript(it)}
        }
    }

    override fun onDestroyView() {
        locatorJob?.cancel()
        locatorJob = null
        boundSession = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SURFACE = "surface"
        private const val NAVIGATOR_TAG = "readium-navigator"

        fun arguments(surface: NavigationSurface): Bundle = bundleOf(ARG_SURFACE to surface.name)
    }
}
