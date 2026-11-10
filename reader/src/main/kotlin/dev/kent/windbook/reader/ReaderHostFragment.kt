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
import org.readium.r2.navigator.input.InputListener
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
        fun onCenterTap()
    }

    private var boundSession: ReadiumReaderSession? = null
    private var pendingBind: (() -> Unit)? = null
    private var callbacks: Callbacks? = null
    private var locatorJob: Job? = null
    private var restoringAnchor = false
    private var appliedTypography: Pair<dev.kent.windbook.core.model.ReaderTypography,Boolean>? = null

    private val navigator: Navigator?
        get() = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val surface = NavigationSurface.valueOf(requireArguments().getString(ARG_SURFACE, NavigationSurface.PUBLICATION.name))
        require(surface == NavigationSurface.PUBLICATION) { "ReaderHostFragment requires a publication surface" }
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
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
        (navigator as? EpubNavigatorFragment)?.submitPreferences(typographyPreferences(value,darkTheme))
        appliedTypography=next
    }

    /** A deliberate jump completes only when its resource and requested anchor are visible. */
    suspend fun navigate(locatorJson: String): NavigationResult {
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
        return if (forward) overflowable.goForward() else overflowable.goBackward()
    }

    /** The theme the EPUB navigator is rendering with; null for PDF or before bind. */
    internal val epubTheme: Theme?
        get() = (navigator as? EpubNavigatorFragment)?.settings?.value?.theme

    private fun attachListeners(restore: String?) {
        val current = navigator ?: return
        (current as? OverflowableNavigator)?.apply {
            // Edge taps and arrow keys turn pages.
            addInputListener(DirectionalNavigationAdapter(this))
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
                if(restore!=null && restoringAnchor) launch {
                    kotlinx.coroutines.withTimeoutOrNull(8000) {
                        current.currentLocator.first {it.href==restore.toLocatorOrNull()?.href}
                        kotlinx.coroutines.delay(250)
                        while(boundSession===expected && navigate(restore)==NavigationResult.FAILED) kotlinx.coroutines.delay(100)
                    }
                    if(boundSession===expected && current===navigator) {
                        restoringAnchor=false
                        reportLocation(current,current.currentLocator.value)
                    }
                }
                current.currentLocator.collectLatest { locator -> reportLocation(current,locator) }
            }
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
