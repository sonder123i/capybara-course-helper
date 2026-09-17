package com.k2767.course.manager

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2767.course.R
import java.util.Collections
import java.util.WeakHashMap

/** Native windows and the separate WebView process share the same persisted preference. */
object AppThemeCoordinator : Application.ActivityLifecycleCallbacks {
    const val PREFS = "appearance_settings"
    const val KEY = "theme_mode"
    private var application: Application? = null
    private var appliedMode: AppThemeMode? = null
    var systemNight by mutableStateOf(false)
        private set
    private val activities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.appearance/theme")

    fun readStored(context: Context): AppThemeMode = AppThemePreferences(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).read()

    fun systemDark(): Boolean = Resources.getSystem().configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    fun preserveWebContentColors(settings: android.webkit.WebSettings) {
        if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
        if (Build.VERSION.SDK_INT >= 29) settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
    }

    fun wrapContext(base: Context): Context {
        val mode = if (application != null) AppearanceSettingsManager.themeMode else readStored(base)
        if (mode == AppThemeMode.System) return base
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (mode == AppThemeMode.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
    }

    fun initialize(app: Application) {
        if (application != null) return
        application = app
        app.registerActivityLifecycleCallbacks(this)
        app.contentResolver.registerContentObserver(uri(app), false, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refreshFromProvider() }
        })
        refreshFromProvider()
    }

    private fun refreshFromProvider() {
        val app = application ?: return
        systemNight = systemDark()
        val mode = runCatching {
            app.contentResolver.query(uri(app), null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) AppThemeMode.decode(cursor.getString(0)) else readStored(app)
            }
        }.getOrNull() ?: readStored(app)
        AppearanceSettingsManager.receiveThemeMode(mode)
        applyMode(mode)
    }

    fun preferenceChanged(mode: AppThemeMode) {
        systemNight = systemDark()
        applyMode(mode)
        application?.let { it.contentResolver.notifyChange(uri(it), null) }
    }

    private fun applyMode(mode: AppThemeMode) {
        val app = application ?: return
        if (appliedMode != mode) {
            appliedMode = mode
            if (Build.VERSION.SDK_INT >= 31) {
                app.getSystemService(UiModeManager::class.java).setApplicationNightMode(when (mode) {
                    AppThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                    AppThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                    AppThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                })
            }
            AppCompatDelegate.setDefaultNightMode(when (mode) {
                AppThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                AppThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            })
        }
        activities.toList().forEach { applyActivity(it, mode) }
    }

    @Suppress("DEPRECATION")
    private fun applyActivity(activity: Activity, mode: AppThemeMode) {
        val desired = if (resolveDarkTheme(mode, systemDark())) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val current = activity.resources.configuration
        if (current.uiMode and Configuration.UI_MODE_NIGHT_MASK != desired) {
            val configuration = Configuration(current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or desired
            }
            // ComponentActivity/FragmentActivity do not have an AppCompat delegate.
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            activity.theme.applyStyle(R.style.Theme_CourseSelector, true)
        }
    }

    fun configurationChanged() { systemNight = systemDark(); appliedMode?.let(::applyMode) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        activities.add(activity)
        applyActivity(activity, AppearanceSettingsManager.themeMode)
    }
    override fun onActivityResumed(activity: Activity) { refreshFromProvider() }
    override fun onActivityDestroyed(activity: Activity) { activities.remove(activity) }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}

/** Read-only IPC; SharedPreferences caches are not reliable across processes. */
class AppearanceThemeProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(uri.path == "/theme")
        return MatrixCursor(arrayOf(AppThemeCoordinator.KEY)).apply {
            addRow(arrayOf(AppThemeCoordinator.readStored(requireNotNull(context)).storageValue))
            setNotificationUri(requireNotNull(context).contentResolver, uri)
        }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.course.appearance"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
