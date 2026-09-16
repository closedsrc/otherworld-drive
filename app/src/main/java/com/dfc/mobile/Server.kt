package com.dfc.mobile

/**
 * The one drive this build talks to. It is compiled in deliberately: this is a
 * single-server companion app, so there is no address to type at setup and no
 * way to point a phone at the wrong host. Moving the backend is a rebuild —
 * as long as this hostname keeps resolving, the app keeps working.
 */
object Server {
    const val BASE_URL = "https://drive-api.otherworld.bond"
}
