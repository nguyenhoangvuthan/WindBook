# Updraft launcher icon

Owner-selected variant B (2026-11-17): a 3D open book whose pages fan up in the wind, with two
curled gusts, cream and gold on a deep bronze radial background. The artwork is original; it
replaces the earlier Lucide Book Open outline, so no third-party icon notice is bundled any more.

`make_icon.py` is the single source. One shape list produces both outputs:

    python3 artwork/launcher/make_icon.py --preview <dir>                       # SVG previews, variants A/B/C
    python3 artwork/launcher/make_icon.py --android app/src/main/res --variant B  # shipped vector layers

Generated files (do not edit by hand): `drawable/ic_launcher_background.xml`,
`ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`. The monochrome layer is a flat
silhouette (cover rim, page outlines, gusts) for Android 13+ themed icons.

All artwork stays inside the adaptive-icon safe circle (centre 54,54; radius 33 dp of the 108 dp
layer). Vector gradients need API 24+; the app's minSdk is 31.
