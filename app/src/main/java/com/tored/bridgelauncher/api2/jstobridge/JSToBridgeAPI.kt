package com.tored.bridgelauncher.api2.jstobridge

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import com.tored.bridgelauncher.BridgeLauncherApplication
import com.tored.bridgelauncher.api2.server.BridgeServer
import com.tored.bridgelauncher.api2.server.endpoints.AppIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.IconPackContentEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.IconPacksEndpoint
import com.tored.bridgelauncher.api2.server.getBridgeApiEndpointURL
import com.tored.bridgelauncher.api2.shared.BridgeButtonVisibilityStringOptions
import com.tored.bridgelauncher.api2.shared.BridgeThemeStringOptions
import com.tored.bridgelauncher.api2.shared.OverscrollEffectsStringOptions
import com.tored.bridgelauncher.api2.shared.SystemBarAppearanceStringOptions
import com.tored.bridgelauncher.api2.shared.SystemNightModeStringOptions
import com.tored.bridgelauncher.services.battery.BatteryInfo
import com.tored.bridgelauncher.services.displayshape.DisplayShapeHolder
import com.tored.bridgelauncher.services.googlesearch.GoogleSearch
import com.tored.bridgelauncher.services.location.LocationInfo
import com.tored.bridgelauncher.services.mediaplayback.MediaPlayback
import com.tored.bridgelauncher.services.mobilesignal.MobileSignal
import com.tored.bridgelauncher.services.notificationbadges.NotificationBadgesService
import com.tored.bridgelauncher.services.settings2.BridgeSetting
import com.tored.bridgelauncher.services.settings2.BridgeSettings
import com.tored.bridgelauncher.services.settings2.getIsBridgeAbleToLockTheScreen
import com.tored.bridgelauncher.services.settings2.setBridgeSetting
import com.tored.bridgelauncher.services.settings2.settingsDataStore
import com.tored.bridgelauncher.services.settings2.useBridgeSettingStateFlow
import com.tored.bridgelauncher.services.system.BridgeLauncherAccessibilityService
import com.tored.bridgelauncher.services.wallpaper.WallpaperInfo
import com.tored.bridgelauncher.services.wifisignal.WifiSignal
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsHolder
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsOptions
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsSnapshot
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import com.tored.bridgelauncher.utils.getIsSystemInNightMode
import com.tored.bridgelauncher.utils.launchApp
import com.tored.bridgelauncher.utils.messageOrDefault
import com.tored.bridgelauncher.utils.openAppInfo
import com.tored.bridgelauncher.utils.q
import com.tored.bridgelauncher.utils.requestAppUninstall
import com.tored.bridgelauncher.utils.showErrorToast
import com.tored.bridgelauncher.utils.startAndroidSettingsActivity
import com.tored.bridgelauncher.utils.startBridgeAppDrawerActivity
import com.tored.bridgelauncher.utils.startBridgeSettingsActivity
import com.tored.bridgelauncher.utils.startDevConsoleActivity
import com.tored.bridgelauncher.utils.startWallpaperPickerActivity
import com.tored.bridgelauncher.utils.toPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

private const val TAG = "JSToBridge"

class JSToBridgeAPI(
    private val _app: BridgeLauncherApplication,
    private val _windowInsetsHolder: WindowInsetsHolder,
    private val _displayShapeHolder: DisplayShapeHolder,
    private val _batteryInfo: BatteryInfo,
    private val _mobileSignal: MobileSignal,
    private val _wifiSignal: WifiSignal,
    private val _wallpaperInfo: WallpaperInfo,
    private val _mediaPlayback: MediaPlayback,
    private val _locationInfo: LocationInfo,
    private val _googleSearch: GoogleSearch
)
{
    private val _scope = CoroutineScope(Dispatchers.Main)

    private val _pm = _app.packageManager
    private val _wallman = _app.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    private val _modeman = _app.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    private val _dpman = _app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    var webView: WebView? = null
    var homeScreenContext: Context? = null


    // SETTING STATES

    private fun <TPreference, TResult> s(setting: BridgeSetting<TPreference, TResult>) = useBridgeSettingStateFlow(_app.settingsDataStore, _scope, setting)
    private val _isDeviceAdminEnabled = s(BridgeSettings.isDeviceAdminEnabled)
    private val _isAccessibilityServiceEnabled = s(BridgeSettings.isAccessibilityServiceEnabled)
    private val _theme = s(BridgeSettings.theme)
    private val _allowProjectsToTurnScreenOff = s(BridgeSettings.allowProjectsToTurnScreenOff)
    private val _statusBarAppearance = s(BridgeSettings.statusBarAppearance)
    private val _navigationBarAppearance = s(BridgeSettings.navigationBarAppearance)
    private val _showBridgeButton = s(BridgeSettings.showBridgeButton)
    private val _drawSystemWallpaperBehindWebView = s(BridgeSettings.drawSystemWallpaperBehindWebView)
    private val _drawWebViewOverscrollEffects = s(BridgeSettings.drawWebViewOverscrollEffects)

    private var cachedLatitude: Double = 0.0
    private var cachedLongitude: Double = 0.0

    init {
        // Eagerly set if available
        _locationInfo.latitude.value?.let { cachedLatitude = it }
        _locationInfo.longitude.value?.let { cachedLongitude = it }

        // Launch observer that waits for future updates
        _scope.launch {
            _locationInfo.latitude.collect { lat ->
                lat?.let {
                    Log.d("JSToBridge", "Updating cachedLatitude = $it")
                    cachedLatitude = it
                }
            }
        }
        _scope.launch {
            _locationInfo.longitude.collect { lon ->
                lon?.let {
                    Log.d("JSToBridge", "Updating cachedLongitude = $it")
                    cachedLongitude = it
                }
            }
        }
    }

    private var _lastException: Exception? = null
        set(value)
        {
            field = value.also { Log.e(TAG, "Caught exception", value) }
        }


    // region system

    @JavascriptInterface
    fun getAndroidAPILevel() = Build.VERSION.SDK_INT


    @JavascriptInterface
    fun getBridgeVersionCode() = _pm
        .getPackageInfo(_app.packageName, 0)
        .run {
            if (CurrentAndroidVersion.supportsPackageInfoLongVersionCode())
                longVersionCode
            else
                @Suppress("DEPRECATION")
                versionCode.toLong()
        }

    @JavascriptInterface
    fun getBridgeVersionName(): String = _pm.getPackageInfo(_app.packageName, 0).versionName ?: ""


    @JavascriptInterface
    fun getLastErrorMessage() = _lastException?.messageOrDefault()

    // endregion


    // region fetch

    @JavascriptInterface
    fun getProjectURL() = BridgeServer.PROJECT_URL

    @JavascriptInterface
    fun getAppsURL() = getBridgeApiEndpointURL(BridgeServer.ENDPOINT_APPS)

    // endregion


    // region apps

    @JvmOverloads
    @JavascriptInterface
    fun requestAppUninstall(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { requestAppUninstall(packageName) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenAppInfo(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { openAppInfo(packageName) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestLaunchApp(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { launchApp(packageName) }
    }

    // endregion


    // region icon packs

    @JavascriptInterface
    fun getIconPacksURL(includeItems: Boolean = false): String =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    @JavascriptInterface
    fun getIconPackInfoURL(iconPackPackageName: String, includeItems: Boolean = false) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    @JavascriptInterface
    fun getIconPackAppFilterXMLURL(iconPackPackageName: String, includeItems: Boolean = false) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    // endregion


    // region icons

    @JavascriptInterface
    fun getDefaultAppIconURL(packageName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to packageName,
        )

    @JvmOverloads
    @JavascriptInterface
    fun getAppIconURL(appPackageName: String, iconPackPackageName: String? = null) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to appPackageName,
            AppIconsEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            AppIconsEndpoint.QUERY_NOT_FOUND_BEHAVIOR to AppIconsEndpoint.IconNotFoundBehaviors.Default,
        )

    @JavascriptInterface
    fun getIconPackAppIconURL(iconPackPackageName: String, appPackageName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to appPackageName,
            AppIconsEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            AppIconsEndpoint.QUERY_NOT_FOUND_BEHAVIOR to AppIconsEndpoint.IconNotFoundBehaviors.Error,
        )

    @JavascriptInterface
    fun getIconPackAppItemURL(iconPackPackageName: String, itemName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACK_CONTENT,
            IconPackContentEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPackContentEndpoint.QUERY_ITEM_NAME to itemName,
        )


    @JavascriptInterface
    fun getIconPackDrawableURL(iconPackPackageName: String, drawableName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACK_CONTENT,
            IconPackContentEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPackContentEndpoint.QUERY_DRAWABLE_NAME to drawableName,
        )

    // endregion


    // region wallpaper

    @JavascriptInterface
    fun setWallpaperOffsetSteps(xStep: Float, yStep: Float)
    {
        _wallman.setWallpaperOffsetSteps(xStep, yStep)
    }

    @JavascriptInterface
    fun setWallpaperOffsets(x: Float, y: Float)
    {
        val token = webView?.applicationWindowToken

        if (token != null)
        {
            _wallman.setWallpaperOffsets(token, x, y)
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun sendWallpaperTap(x: Float, y: Float, z: Float = 0f)
    {
        val token = webView?.applicationWindowToken
        if (token != null)
        {
            val metrics = _app.resources.displayMetrics
            _wallman.sendWallpaperCommand(
                token,
                WallpaperManager.COMMAND_TAP,
                metrics.toPx(x).toInt(),
                metrics.toPx(y).toInt(),
                metrics.toPx(z).toInt(),
                Bundle.EMPTY
            )
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestChangeSystemWallpaper(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startWallpaperPickerActivity() }
    }

    // endregion


    // region bridge button

    @JavascriptInterface
    fun getBridgeButtonVisibility(): String
    {
        return BridgeButtonVisibilityStringOptions.fromShowBridgeButton(_showBridgeButton.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetBridgeButtonVisibility(visibility: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.showBridgeButton,
                BridgeButtonVisibilityStringOptions.showBridgeButtonFromStringOrThrow(visibility),
            )
        }
    }

    // endregion


    // region draw system wallpaper behind webview

    @JavascriptInterface
    fun getDrawSystemWallpaperBehindWebViewEnabled(): Boolean
    {
        return _drawSystemWallpaperBehindWebView.value
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetDrawSystemWallpaperBehindWebViewEnabled(enable: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(BridgeSettings.drawSystemWallpaperBehindWebView, enable)
        }
    }

    // endregion


    // region overscroll effects

    @JavascriptInterface
    fun getOverscrollEffects(): String
    {
        return OverscrollEffectsStringOptions.fromDrawWebViewOverscrollEffects(_drawWebViewOverscrollEffects.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetOverscrollEffects(effects: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.drawWebViewOverscrollEffects,
                OverscrollEffectsStringOptions.drawWebViewOverscrollEffectsOrThrow(effects),
            )
        }
    }

    // endregion


    // region system night mode

    @JavascriptInterface
    fun getSystemNightMode(): String
    {
        return SystemNightModeStringOptions.fromUiModeManagerNightMode(_modeman.nightMode).rawValue
    }

    @JavascriptInterface
    fun resolveIsSystemInDarkTheme(): Boolean
    {
        return _app.getIsSystemInNightMode()
    }

    @JavascriptInterface
    fun getCanSetSystemNightMode(): Boolean
    {
        return ActivityCompat.checkSelfPermission(_app, "android.permission.MODIFY_DAY_NIGHT_MODE") == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(_app, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("WrongConstant")
    @JvmOverloads
    @JavascriptInterface
    fun requestSetSystemNightMode(mode: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            Log.d(TAG, "requestSetSystemNightMode: $mode")

            val modeInt = when (mode)
            {
                "no" -> UiModeManager.MODE_NIGHT_NO
                "yes" -> UiModeManager.MODE_NIGHT_YES
                "auto" -> UiModeManager.MODE_NIGHT_AUTO

                "custom" -> if (CurrentAndroidVersion.supportsNightModeCustom())
                    UiModeManager.MODE_NIGHT_CUSTOM
                else
                    throw Exception("\"custom\" requires API level 30 (Android 11).")

                else -> throw Exception("Mode must be one of ${q("no")}, ${q("yes")}, ${q("auto")} or, from API level 30 (Android 11), ${q("custom")} (got ${q(mode)}).")
            }

            val hasModifyPerm = ActivityCompat.checkSelfPermission(_app, "android.permission.MODIFY_DAY_NIGHT_MODE") == PackageManager.PERMISSION_GRANTED

            if (hasModifyPerm)
            {
                _modeman.nightMode = modeInt
            }
            else
            {
                val hasWriteSecureSettingsPerm = ActivityCompat.checkSelfPermission(_app, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

                if (hasWriteSecureSettingsPerm)
                {
                    // shoutouts to joaomgcd (Tasker dev) for this workaround!
                    Settings.Secure.putInt(_app.contentResolver, "ui_night_mode", modeInt)
                    _modeman.enableCarMode(UiModeManager.ENABLE_CAR_MODE_ALLOW_SLEEP)
                    _modeman.disableCarMode(0)
                }
                else
                {
                    Toast
                        .makeText(
                            _app,
                            "To set system night mode, Bridge needs the WRITE_SECURE_SETTINGS permission, which can be granted via ADB. "
                                    + "Check the documentation for more information.",
                            Toast.LENGTH_LONG
                        )
                        .show()
                }
            }
        }
    }

    // endregion

    // region
    @JavascriptInterface
    fun getNotificationCounts(): String {
        val counts = NotificationBadgesService.instance?.notificationCounts?.value.orEmpty()
        return Json.encodeToString(counts)
    }
    // endregion


    // region Bridge theme

    @JavascriptInterface
    fun getBridgeTheme(): String
    {
        return BridgeThemeStringOptions.fromBridgeTheme(_theme.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetBridgeTheme(theme: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.theme,
                BridgeThemeStringOptions.bridgeThemeFromStringOrThrow(theme),
            )
        }
    }

    // endregion


    // region system bars

    @JavascriptInterface
    fun getStatusBarAppearance(): String
    {
        return SystemBarAppearanceStringOptions.fromSystemBarAppearance(_statusBarAppearance.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetStatusBarAppearance(appearance: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.statusBarAppearance,
                SystemBarAppearanceStringOptions.systemBarAppearanceFromStringOrThrow(appearance)
            )
        }
    }


    @JavascriptInterface
    fun getNavigationBarAppearance(): String
    {
        return SystemBarAppearanceStringOptions.fromSystemBarAppearance(_navigationBarAppearance.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetNavigationBarAppearance(appearance: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.navigationBarAppearance,
                SystemBarAppearanceStringOptions.systemBarAppearanceFromStringOrThrow(appearance)
            )
        }
    }

    // endregion


    // region screen locking

    @JavascriptInterface
    fun getCanLockScreen(): Boolean
    {
        return getIsBridgeAbleToLockTheScreen(
            isAccessibilityServiceEnabled = _isAccessibilityServiceEnabled.value,
            isDeviceAdminEnabled = _isDeviceAdminEnabled.value,
            allowProjectsToTurnScreenOff = _allowProjectsToTurnScreenOff.value,
        )
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestLockScreen(showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            if (!CurrentAndroidVersion.supportsAccessiblityServiceScreenLock() && !_isDeviceAdminEnabled.value)
            {
                throw Exception("Bridge is not a device admin. Visit Bridge Settings to resolve the issue.")
            }
            else if (CurrentAndroidVersion.supportsAccessiblityServiceScreenLock() && !_isAccessibilityServiceEnabled.value)
            {
                throw Exception("Bridge Accessibility Service is not enabled. Visit Bridge Settings to resolve the issue.")
            }

            if (!_allowProjectsToTurnScreenOff.value)
            {
                throw Exception("Projects are not allowed to lock the screen. Visit Bridge Settings to resolve the issue.")
            }

            if (CurrentAndroidVersion.supportsAccessiblityServiceScreenLock())
            {
                if (BridgeLauncherAccessibilityService.instance == null)
                {
                    throw Exception("Cannot access the Bridge Accessibility Service instance. This is a bug.")
                }
                else
                {
                    BridgeLauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            else
            {
                _dpman.lockNow()
            }
        }
    }

    // endregion


    // region misc actions

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenBridgeSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startBridgeSettingsActivity() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenBridgeAppDrawer(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startBridgeAppDrawerActivity() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenDeveloperConsole(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startDevConsoleActivity() }
    }

    // https://stackoverflow.com/a/15582509/6796433
    @JvmOverloads
    @SuppressLint("WrongConstant")
    @JavascriptInterface
    fun requestExpandNotificationShade(showToastIfFailed: Boolean = true): Boolean
    {
        try
        {
            val sbservice: Any = _app.getSystemService("statusbar")
            val statusbarManager = Class.forName("android.app.StatusBarManager")
            val showsb = statusbarManager.getMethod("expandNotificationsPanel")
            showsb.invoke(sbservice)

            return true
        }
        catch (ex: Exception)
        {
            _lastException = ex

            if (showToastIfFailed)
                _app.showErrorToast(ex)

            return false
        }
    }


    @JvmOverloads
    @JavascriptInterface
    fun requestOpenAndroidSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startAndroidSettingsActivity() }
    }

    // endregion


    // region toast

    @JvmOverloads
    @JavascriptInterface
    fun showToast(message: String, long: Boolean = false)
    {
        Toast.makeText(_app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // endregion


    // region window insets & cutouts

    private fun getWindowInsetsJson(option: WindowInsetsOptions) = Json.encodeToString(WindowInsetsSnapshot.serializer(), _windowInsetsHolder.stateFlowMap[option]!!.value)

    @JavascriptInterface
    fun getStatusBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.StatusBars)

    @JavascriptInterface
    fun getStatusBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.StatusBarsIgnoringVisibility)


    @JavascriptInterface
    fun getNavigationBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.NavigationBars)

    @JavascriptInterface
    fun getNavigationBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.NavigationBarsIgnoringVisibility)


    @JavascriptInterface
    fun getCaptionBarWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.CaptionBar)

    @JavascriptInterface
    fun getCaptionBarIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.CaptionBarIgnoringVisibility)


    @JavascriptInterface
    fun getSystemBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemBars)

    @JavascriptInterface
    fun getSystemBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemBarsIgnoringVisibility)


    @JavascriptInterface
    fun getImeWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.Ime)

    @JavascriptInterface
    fun getImeAnimationSourceWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.ImeAnimationSource)

    @JavascriptInterface
    fun getImeAnimationTargetWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.ImeAnimationTarget)


    @JavascriptInterface
    fun getTappableElementWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.TappableElement)

    @JavascriptInterface
    fun getTappableElementIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.TappableElementIgnoringVisibility)


    @JavascriptInterface
    fun getSystemGesturesWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemGestures)

    @JavascriptInterface
    fun getMandatorySystemGesturesWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.MandatorySystemGestures)


    @JavascriptInterface
    fun getDisplayCutoutWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.DisplayCutout)

    @JavascriptInterface
    fun getWaterfallWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.Waterfall)


    @JavascriptInterface
    fun getDisplayCutoutPath() = _displayShapeHolder.displayCutoutPath

    @JavascriptInterface
    fun getDisplayShapePath() = _displayShapeHolder.displayShapePath

    // endregion

    // region statusbar

    @JavascriptInterface
    fun getBatteryLevel() = _batteryInfo.batteryLevel.value

    @JavascriptInterface
    fun getBatteryIsCharging() = _batteryInfo.isCharging.value

    @JavascriptInterface
    fun getWifiSignalStrength() = _wifiSignal.wifiSignalStrength.value

    @JavascriptInterface
    fun getWifiSignalLevel() = _wifiSignal.wifiSignalLevel.value

    @JavascriptInterface
    fun getWifiSSID() = _wifiSignal.ssid.value

    @JavascriptInterface
    fun getMobileSignalStrength() = _mobileSignal.mobileSignalStrength.value

    @JavascriptInterface
    fun getNetworkType() = _mobileSignal.networkType.value

    @JavascriptInterface
    fun getMobileSignalLevel() = _mobileSignal.mobileSignalLevel.value
    // endregion

    // region wallpaper

    @JvmOverloads
    @JavascriptInterface
    fun requestWallpaperRefresh(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _wallpaperInfo.refresh() }
    }

    @JavascriptInterface
    fun getSystemWallpaperURL() = getBridgeApiEndpointURL(BridgeServer.ENDPOINT_WALLPAPER)

    @JavascriptInterface
    fun getSystemWallpaperBase64() = _wallpaperInfo.wallpaperBase64.value

    @JavascriptInterface
    fun getMonetPaletteJson() = _wallpaperInfo.monetPaletteJson.value

    // endregion

    // region media

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaPlay(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _mediaPlayback.play() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaPause(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _mediaPlayback.pause() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSeekTo(ms: Long, showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _mediaPlayback.seekTo(ms) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaSkipNext(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _mediaPlayback.skipNext() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaSkipPrevious(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) { _mediaPlayback.skipPrevious() }
    }

    @JavascriptInterface
    fun getCurrentMediaMetadataArtist() = _mediaPlayback.metadata.value?.getString(MediaMetadata.METADATA_KEY_ARTIST)

    @JavascriptInterface
    fun getCurrentMediaMetadataTitle() = _mediaPlayback.metadata.value?.getString(MediaMetadata.METADATA_KEY_TITLE)

    @JavascriptInterface
    fun getCurrentMediaMetadataAlbum() = _mediaPlayback.metadata.value?.getString(MediaMetadata.METADATA_KEY_ALBUM)

    @JavascriptInterface
    fun getCurrentMediaMetadataDuration() = _mediaPlayback.metadata.value?.getLong(MediaMetadata.METADATA_KEY_DURATION)

    @JavascriptInterface
    fun getCurrentMediaMetadataArtworkBase64(): String? {
        val bitmap = _mediaPlayback.metadata.value?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: _mediaPlayback.metadata.value?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        return bitmap?.let {
            val outputStream = ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    @JavascriptInterface
    fun getIsPlaying() = _mediaPlayback.isPlaying.value

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaAppLaunch(showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) {
            _mediaPlayback.packageName.value?.let { launchApp(it) }
        }
    }

    // endregion

    // region location

    @JavascriptInterface
    fun getLocationLatitude(): Double = cachedLatitude

    @JavascriptInterface
    fun getLocationLongitude(): Double = cachedLongitude

    // endregion

    // region googlesearch

    @JvmOverloads
    @JavascriptInterface
    fun requestGoogleSearch(query: String, showToastIfFailed: Boolean = true): Boolean {
        return tryRunInHomescreenContext(showToastIfFailed) {
            _googleSearch.search(query)
        }
    }

    @JavascriptInterface
    fun getGoogleSearchSuggestions(query: String): String {
        return try {
            val suggestions = _googleSearch.getSuggestions(query)
            Json.encodeToString(suggestions)
        } catch (e: Exception) {
            _lastException = e
            "[]"
        }
    }

    //endregion


    // region helpers

    private fun Context.tryRun(showToastIfFailed: Boolean, f: Context.() -> Unit): Boolean
    {
        return try
        {
            f()
            true
        }
        catch (ex: Exception)
        {
            if (showToastIfFailed)
                showErrorToast(ex)

            _lastException = ex

            false
        }
    }

    private fun tryRunInHomescreenContext(showToastIfFailed: Boolean, f: Context.() -> Unit): Boolean
    {
        return when(val context = homeScreenContext)
        {
            null -> false.also { if (showToastIfFailed) _app.showErrorToast("homeScreenContext is null") }
            else -> context.tryRun(showToastIfFailed, f)
        }
    }

    private fun Context.tryEditPrefs(showToastIfFailed: Boolean, f: (MutablePreferences) -> Unit): Boolean
    {
        return tryRun(showToastIfFailed)
        {
            runBlocking {
                settingsDataStore.edit(f)
            }
        }
    }

    // endregion
}