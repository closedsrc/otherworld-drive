package com.dfc.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.backup.MediaScanner
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google-Photos-style home: a date-grouped grid of everything backed up so
 * far, a backup status header, and a manual "Back up now" action.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter by lazy { GalleryAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.grid.layoutManager = GridLayoutManager(this, 3)
        binding.grid.adapter = adapter
        adapter.onClick = { item, pos ->
            startActivity(
                Intent(this, ViewerActivity::class.java).putExtra("index", pos)
            )
        }

        binding.settings.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.backupNow.setOnClickListener { onBackupNow() }

        requestMediaPermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        adapter.cancelLoads()
        super.onDestroy()
    }

    private fun requestMediaPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) refresh()
            else binding.statusText.text = "Photo permission needed for backup"
        }
    }

    private fun onBackupNow() {
        val prefs = Prefs.get(this)
        if (!prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        BackupWorker.runNow(this)
        Toast.makeText(this, "Backup started", Toast.LENGTH_SHORT).show()
        binding.backupNow.isEnabled = false
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            withContext(Dispatchers.Main) { binding.backupNow.isEnabled = true; refresh() }
        }
    }

    private fun refresh() {
        val prefs = Prefs.get(this)

        if (!prefs.isConfigured) {
            binding.statusText.text = "Not connected yet. Tap the gear to link your drive."
            binding.statusDot.setImageResource(R.drawable.dot_off)
            adapter.submit(emptyList())
            binding.empty.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            val db = AppDb.get(this@MainActivity)
            // Every app open is also a backup opportunity: reconcile, then let
            // the worker drain whatever is pending (Wi-Fi gate still applies).
            withContext(Dispatchers.IO) {
                try { MediaScanner.scan(this@MainActivity) } catch (_: Exception) {}
            }
            val pending = withContext(Dispatchers.IO) { db.mediaDao().pendingCount() }
            val done = withContext(Dispatchers.IO) { db.mediaDao().uploadedCount() }
            val last = prefs.lastBackupAt
            val lastStr = if (last > 0)
                android.text.format.DateUtils.getRelativeTimeSpanString(last).toString()
            else "never"

            binding.statusDot.setImageResource(if (pending > 0) R.drawable.dot_busy else R.drawable.dot_idle)
            binding.statusText.text = when {
                pending > 0 -> "$done safe on the drive, $pending waiting, last run $lastStr"
                done > 0 -> "All $done backed up, last run $lastStr"
                else -> "Connected. Ready to back up your first photos."
            }

            // Only piggyback a manual run when the periodic job is stale —
            // firing on every open races the 15-min worker and double-uploads.
            val stale = prefs.lastBackupAt < System.currentTimeMillis() - 10 * 60_000
            if (pending > 0 && stale) BackupWorker.runNow(this@MainActivity)

            val items = withContext(Dispatchers.IO) { db.mediaDao().uploaded(500) }
            adapter.submit(items)
            binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
