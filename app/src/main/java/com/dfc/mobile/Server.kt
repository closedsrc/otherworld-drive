package com.dfc.mobile

/**
 * The drive this app talks to. The default is this product's hosted drive;
 * a self-hosted instance can be entered on the setup screen, and the choice
 * is stored in prefs so the app can be pointed at any DFC server without a
 * rebuild.
 */
object Server {
    const val DEFAULT_BASE_URL = "https://drive-api.otherworld.bond"

    fun baseUrl(prefs: Prefs): String = prefs.serverUrl.ifEmpty { DEFAULT_BASE_URL }
}
