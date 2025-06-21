package com.example.currency

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_LANG   = "pref_lang"
        private const val KEY_THEME  = "pref_theme"
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lang  = prefs.getString(KEY_LANG, "ru")!!
        val ctx   = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем тему
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getString(KEY_THEME, "light") == "dark")
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Настраиваем тулбар без заголовка
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarLogin)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""

        // Кнопка «Начать»
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            showSettingsDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            getString(R.string.settings_language),
            getString(R.string.settings_theme)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(items) { dlg, which ->
                when (which) {
                    0 -> showLanguageDialog()
                    1 -> showThemeDialog()
                }
            }
            .show()
    }

    private fun showLanguageDialog() {
        val langs   = arrayOf(
            getString(R.string.lang_russian),
            getString(R.string.lang_english)
        )
        val codes   = arrayOf("ru", "en")
        val prefs   = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val current = prefs.getString(KEY_LANG, "ru")
        val checked = codes.indexOf(current)
        AlertDialog.Builder(this)
            .setTitle(R.string.lang_title)
            .setSingleChoiceItems(langs, checked) { dlg, idx ->
                prefs.edit().putString(KEY_LANG, codes[idx]).apply()
                dlg.dismiss()
                recreate()
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes  = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val prefs   = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val current = prefs.getString(KEY_THEME, "light")
        val checked = if (current == "dark") 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(themes, checked) { dlg, idx ->
                val mode = if (idx == 1)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_NO

                prefs.edit()
                    .putString(KEY_THEME, if (idx == 1) "dark" else "light")
                    .apply()
                AppCompatDelegate.setDefaultNightMode(mode)
                dlg.dismiss()
                recreate()
            }
            .show()
    }
}
