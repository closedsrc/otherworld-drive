package com.dfc.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.dfc.mobile.data.MediaItem
import com.dfc.mobile.databinding.ItemCellBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Photo grid cells: 3 columns, thumbnails from the server's /api/thumb. */
class GalleryAdapter(private val context: android.content.Context) :
    RecyclerView.Adapter<GalleryAdapter.Cell>() {

    private val items = ArrayList<MediaItem>()
    private val jobs = HashMap<ImageView, Job>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var onClick: ((MediaItem, Int) -> Unit)? = null

    /** Call from the owning Activity's onDestroy to stop in-flight loads. */
    fun cancelLoads() = scope.cancel()

    fun submit(list: List<MediaItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun get(position: Int): MediaItem = items[position]

    class Cell(val binding: ItemCellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell {
        val binding = ItemCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Cell(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Cell, position: Int) {
        val item = items[position]
        val iv = holder.binding.thumb
        iv.setImageBitmap(null)
        holder.binding.videoBadge.visibility =
            if (item.isVideo) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.root.setOnClickListener { onClick?.invoke(item, holder.bindingAdapterPosition) }

        // Cancel any previous load bound to this recycled cell.
        jobs.remove(iv)?.cancel()
        val remoteId = item.remoteId ?: return
        jobs[iv] = scope.launch {
            val bmp = ThumbLoader.cached(remoteId) ?: ThumbLoader.load(context, remoteId)
            if (holder.bindingAdapterPosition == position) {
                iv.setImageBitmap(bmp)
            }
        }
    }

    override fun onViewRecycled(holder: Cell) {
        jobs.remove(holder.binding.thumb)?.cancel()
        holder.binding.thumb.setImageDrawable(null)
        super.onViewRecycled(holder)
    }
}
