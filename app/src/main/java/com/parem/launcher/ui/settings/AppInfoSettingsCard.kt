package com.parem.launcher.ui.settings

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.parem.launcher.BuildConfig
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentSettingsBinding
import com.parem.launcher.helper.openAppInfo
import com.parem.launcher.helper.openUrl
import com.parem.launcher.helper.showToast
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Card 1 ("Parem Launcher"): app info, default-launcher prompt, about/github/
 * privacy links, and settings export/import.
 *
 * Extracted from SettingsFragment; mirrors HomeWidgetController's shape
 * (fragment/binding/prefs in the constructor). [importSettingsLauncher] must
 * still be registered by the fragment in onCreate (ActivityResultLauncher
 * registration requires that), so it's handed in rather than owned here; the
 * fragment's launcher callback calls back into [importSettings].
 */
class AppInfoSettingsCard(
    private val fragment: SettingsFragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
    private val importSettingsLauncher: ActivityResultLauncher<Intent>,
) : View.OnClickListener {

    private val context get() = binding.root.context

    fun bind() {
        // Rows that link nowhere are hidden until their URL constants are filled in
        binding.aboutParem.isVisible = Constants.URL_ABOUT_PAREM.isNotEmpty()
        binding.privacy.isVisible = Constants.URL_PAREM_PRIVACY.isNotEmpty()
        binding.github.isVisible = Constants.URL_PAREM_GITHUB.isNotEmpty()

        initClickListeners()
        initObservers()
    }

    private fun initClickListeners() {
        binding.paremHiddenApps.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.exportSettings?.setOnClickListener(this)
        binding.importSettings?.setOnClickListener(this)
        binding.aboutParem.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)
    }

    private fun initObservers() {
        viewModel.isParemDefault.observe(fragment.viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = context.getString(R.string.change_default_launcher)
            }
        }
    }

    override fun onClick(view: View) {
        fragment.resetOpenPickers(view.id)
        when (view.id) {
            R.id.paremHiddenApps -> showHiddenApps()
            R.id.appInfo -> openAppInfo(context, Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.exportSettings -> exportSettings()
            R.id.importSettings -> confirmImportSettings()
            R.id.aboutParem -> context.openUrl(Constants.URL_ABOUT_PAREM)
            R.id.github -> context.openUrl(Constants.URL_PAREM_GITHUB)
            R.id.privacy -> context.openUrl(Constants.URL_PAREM_PRIVACY)
        }
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            context.showToast(context.getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        fragment.findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun exportSettings() {
        val ctx = context
        val resolver = ctx.contentResolver
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    val json = prefs.exportToJson()
                    json.toString(2)
                }

                withContext(Dispatchers.IO) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "parem_settings.json")
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw IOException("Failed to create export file")
                    resolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
                }
                ctx.showToast(ctx.getString(R.string.settings_exported))
            } catch (e: Exception) {
                ctx.showToast(ctx.getString(R.string.settings_export_failed, e.message))
            }
        }
    }

    private fun confirmImportSettings() {
        BottomSheetMenu(context)
            .message(context.getString(R.string.import_settings_confirm))
            .option(context.getString(R.string.confirm)) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importSettingsLauncher.launch(intent)
            }
            .option(context.getString(R.string.cancel), dimmed = true) {}
            .show()
    }

    /** Called from the fragment's registerForActivityResult callback. */
    fun importSettings(uri: Uri) {
        val ctx = context
        val activity = fragment.requireActivity()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }
                if (jsonString != null) {
                    val json = JSONObject(jsonString)
                    withContext(Dispatchers.IO) {
                        prefs.importFromJson(json)
                    }
                    ctx.showToast(ctx.getString(R.string.settings_imported))
                    activity.recreate()
                }
            } catch (e: Exception) {
                ctx.showToast(ctx.getString(R.string.settings_import_failed, e.message))
            }
        }
    }
}
