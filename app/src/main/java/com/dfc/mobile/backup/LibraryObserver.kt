package com.dfc.mobile.backup

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Watches this phone's own photo and video library while the app is on screen,
 * so a picture taken now is indexed now and queued now instead of at the next
 * 15-minute tick. MediaStore notifies on commit; nothing here polls.
 *
 * Registered for as long as the app is in the foreground and unregistered when
 * it is not, which is why the background case still belongs to the periodic job.
 */
object LibraryObserver {

    /** Start watching both collections; the returned handles unregister them. */
    fun register(context: Context, onChanged: () -> Unit): List<ContentObserver> {
        val resolver = context.contentResolver
        val handler = Handler(Looper.getMainLooper())
        return listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).map { uri ->
            // The Uri overload is what a provider dispatches to; the boolean-only
            // one is deprecated and never used by MediaStore.
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, changed: Uri?) = onChanged()
            }
            resolver.registerContentObserver(uri, true, observer)
            observer
        }
    }

    fun unregister(context: Context, observers: List<ContentObserver>) {
        observers.forEach { context.contentResolver.unregisterContentObserver(it) }
    }
}
