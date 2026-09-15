package com.dfc.mobile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dfc.mobile.backup.BackupWorker
import com.dfc.mobile.databinding.ActivitySetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First-run screen: point the app at a ddrive (Discord-Free-Cloud) server. */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = Prefs.get(this)
        binding.serverUrl.setText(prefs.serverUrl)
        binding.token.setText(prefs.token)
        binding.wifiOnly.isChecked = prefs.wifiOnly

        binding.save.setOnClickListener {
            val url = binding.serverUrl.text.toString().trim()
            val token = binding.token.text.toString().trim()
            if (!url.startsWith("http")) {
                binding.hint.text = "Server URL must start with http:// or https://"
                return@setOnClickListener
            }
            if (token.isEmpty()) {
                binding.hint.text = "Paste the write API token from the ddrive dashboard"
                return@setOnClickListener
            }
            binding.progress.visibility = View.VISIBLE
            binding.hint.text = ""
            binding.save.isEnabled = false

            lifecycleScope.launch {
                // Verify against a THROWAWAY prefs object first: only persist
                // the credentials once they actually work, so a typo can never
                // leave the app "configured" and silently failing.
                val probe = DfcApi(Prefs.probe(url, token))
                val (ok, msg) = withContext(Dispatchers.IO) { probe.verify() }
                binding.progress.visibility = View.GONE
                binding.save.isEnabled = true
                if (!ok) {
                    binding.hint.text = msg
                    return@launch
                }
                prefs.serverUrl = url
                prefs.token = token
                prefs.wifiOnly = binding.wifiOnly.isChecked
                // Reserve the app's folder tree on the server: Mobile Backup/
                try {
                    withContext(Dispatchers.IO) { probe.ensureFolderPath("Mobile Backup") }
                } catch (e: Exception) {
                    Toast.makeText(this@SetupActivity, "Folder create failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                BackupWorker.schedule(this@SetupActivity, prefs.wifiOnly)
                Toast.makeText(this@SetupActivity, "Connected. Auto backup every 15 min", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
