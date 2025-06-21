package com.example.currency

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.currency.network.RetrofitClient
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME              = "settings"
        private const val KEY_LANG                = "pref_lang"
        private const val KEY_THEME               = "pref_theme"
        private const val CHANNEL_ID              = "currency_channel"
        private const val CHANNEL_NAME            = "Currency Notifications"
        private const val NOTIFICATION_ID         = 2001
        private const val REQUEST_NOTIFICATION_RC = 101
    }

    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo:   Spinner
    private lateinit var etAmount:    EditText
    private lateinit var btnConvert:  Button
    private lateinit var tvResult:    TextView
    private lateinit var progressBar: ProgressBar

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lang  = prefs.getString(KEY_LANG, "ru")!!
        val ctx   = LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getString(KEY_THEME, "light") == "dark")
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarMain)
        setSupportActionBar(toolbar)

        spinnerFrom  = findViewById(R.id.spinnerFrom)
        spinnerTo    = findViewById(R.id.spinnerTo)
        etAmount     = findViewById(R.id.etAmount)
        btnConvert   = findViewById(R.id.btnConvert)
        tvResult     = findViewById(R.id.tvResult)
        progressBar  = findViewById(R.id.progressBar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_RC
            )
        }

        createNotificationChannel()
        btnConvert.setOnClickListener { convertCurrency() }
        loadCurrencyList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
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
            .setItems(items) { _, which ->
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
                prefs.edit()
                    .putString(KEY_THEME, if (idx == 1) "dark" else "light")
                    .apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (idx == 1) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                dlg.dismiss()
                recreate()
            }
            .show()
    }

    private fun loadCurrencyList() {
        progressBar.visibility = View.VISIBLE
        btnConvert.isEnabled    = false

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet_local), Toast.LENGTH_LONG)
                .show()
            setSpinnerWithFallback()
            return
        }

        lifecycleScope.launch {
            try {
                val resp  = withContext(Dispatchers.IO) { RetrofitClient.api.getSymbols() }
                val codes = resp.symbols
                    ?.keys
                    ?.sorted()
                    ?: resources
                        .getStringArray(R.array.default_currency_codes)
                        .toList()
                        .sorted()

                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    codes
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerFrom.adapter = adapter
                spinnerTo.adapter   = adapter

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_loading, e.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
                setSpinnerWithFallback()
            } finally {
                progressBar.visibility = View.GONE
                btnConvert.isEnabled    = true
            }
        }
    }

    private fun setSpinnerWithFallback() {
        val codes = resources.getStringArray(R.array.default_currency_codes).sorted()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            codes
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerFrom.adapter = adapter
        spinnerTo.adapter   = adapter
        progressBar.visibility = View.GONE
        btnConvert.isEnabled    = true
    }

    private fun isNetworkAvailable(): Boolean {
        val cm   = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net  = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun convertCurrency() {
        val from   = spinnerFrom.selectedItem as String
        val to     = spinnerTo.selectedItem as String
        val amount = etAmount.text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) {
            etAmount.error = getString(R.string.error_invalid_amount)
            return
        }

        progressBar.visibility = View.VISIBLE
        btnConvert.isEnabled   = false
        tvResult.text          = ""

        lifecycleScope.launch {
            try {
                val resp   = withContext(Dispatchers.IO) {
                    RetrofitClient.api.convert(from, to, amount)
                }
                val output = String.format(
                    "%.2f %s = %.2f %s",
                    amount, from,
                    resp.result, to
                )
                tvResult.text = output
                showNotification(getString(R.string.button_convert), output)

            } catch (e: UnknownHostException) {
                tvResult.text = getString(R.string.no_internet_local)
            } catch (e: Exception) {
                tvResult.text = getString(R.string.error_loading, e.localizedMessage)
            } finally {
                progressBar.visibility = View.GONE
                btnConvert.isEnabled   = true
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            mgr.deleteNotificationChannel(CHANNEL_ID)
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
                description = getString(R.string.settings)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, notif)
    }
}
