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
import dev.kent.windbook.core.model.BookFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    private val navigator: Navigator?
        get() = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val format = BookFormat.valueOf(requireArguments().getString(ARG_FORMAT, BookFormat.EPUB.name))
        require(format == BookFormat.EPUB) { "ReaderHostFragment hosts EPUB only; PDFs use PdfReaderView" }
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

    fun bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks, darkTheme: Boolean) {
        this.callbacks = callbacks
        val readium = session as ReadiumReaderSession
        if (readium === boundSession) return
        if (view == null) {
            pendingBind = { bind(session, initialLocatorJson, callbacks, darkTheme) }
            return
        }
        boundSession = readium
        val initialLocator = initialLocatorJson?.toLocatorOrNull()

        childFragmentManager.fragmentFactory = EpubNavigatorFactory(readium.publication)
            .createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = initialEpubPreferences(darkTheme),
            )
        childFragmentManager.commitNow {
            childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { remove(it) }
            add(R.id.windbook_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }
        attachListeners()
    }

    /** Used for volume keys / page keys. */
    fun turnPage(forward: Boolean): Boolean {
        val overflowable = navigator as? OverflowableNavigator ?: return false
        return if (forward) overflowable.goForward() else overflowable.goBackward()
    }

    /** The theme the EPUB navigator is rendering with; null for PDF or before bind. */
    internal val epubTheme: Theme?
        get() = (navigator as? EpubNavigatorFragment)?.settings?.value?.theme

    private fun attachListeners() {
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
                current.currentLocator.collect { locator ->
                    callbacks?.onLocatorChanged(
                        locator.toJsonString(),
                        readingProgression(
                            position = locator.locations.position,
                            positionCount = boundSession?.positionCount ?: 0,
                            totalProgression = locator.locations.totalProgression,
                        ),
                        locator.title,
                    )
                }
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
        private const val ARG_FORMAT = "format"
        private const val NAVIGATOR_TAG = "readium-navigator"

        fun arguments(format: BookFormat): Bundle = bundleOf(ARG_FORMAT to format.name)
    }
}
