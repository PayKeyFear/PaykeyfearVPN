package com.paykeyfear.vpn.ui.screens.import_config

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Portrait-locked subclass of ZXing's embedded [CaptureActivity].
 *
 * `com.journeyapps:zxing-android-embedded` declares its CaptureActivity
 * with `android:screenOrientation="sensorLandscape"` in the library
 * manifest. That setting wins over any runtime `setOrientationLocked`
 * flag, which is why users saw the scanner flip to landscape even
 * though we asked for locked orientation.
 *
 * The recommended override is to subclass CaptureActivity and declare
 * the subclass in the app manifest with the desired
 * `screenOrientation="portrait"` — the manifest merger picks the
 * subclass attributes and ZXing uses them for the preview surface.
 */
class PortraitCaptureActivity : CaptureActivity()
