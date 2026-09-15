package com.dfc.mobile

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dfc.mobile.data.AppDb
import com.dfc.mobile.databinding.ActivityViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen viewer for one backed-up item: /api/preview for images,
 * /api/download/file with range support through VideoView for videos.
 * Swipe left/right walks the uploaded list.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private var items: List<com.dfc.mobile.data.MediaItem> = emptyList()
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        position = intent.getIntExtra("index", 0)
        lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { AppDb.get(this@ViewerActivity).mediaDao().uploaded(500) }
            if (items.isEmpty()) { finish(); return@launch }
            position = position.coerceIn(0, items.size - 1)
            show(position)
        }

        binding.root.setOnClickListener { finish() }
        binding.prev.setOnClickListener { if (position > 0) show(--position) }
        binding.next.setOnClickListener { if (position < items.size - 1) show(++position) }
    }

    private fun show(pos: Int) {
        val item = items[pos]
        val api = DfcApi.get(this)
        binding.caption.text = item.displayName
        binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
        binding.imageView.visibility = View.VISIBLE

        if (item.isVideo) {
            binding.imageView.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE
            val headers = api.authHeaders()
            binding.videoView.setVideoURI(
                Uri.parse(api.streamUrl(item.remoteId ?: "")),
                headers
            )
            binding.videoView.setMediaController(MediaController(this))
            binding.videoView.setOnErrorListener { _, _, _ ->
                binding.caption.text = "Could not play video"
                true
            }
            binding.videoView.start()
        } else {
            lifecycleScope.launch {
                binding.progress.visibility = View.VISIBLE
                val bmp = withContext(Dispatchers.IO) {
                    ThumbLoader.load(this@ViewerActivity, item.remoteId ?: "", preview = true)
                }
                binding.progress.visibility = View.GONE
                if (bmp != null) binding.imageView.setImageBitmap(bmp)
                else binding.caption.text = "Could not load preview"
            }
        }
    }

    override fun onDestroy() {
        binding.videoView.stopPlayback()
        super.onDestroy()
    }
}
