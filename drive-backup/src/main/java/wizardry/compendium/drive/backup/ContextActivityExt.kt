package wizardry.compendium.drive.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the ContextWrapper chain looking for an Activity. LocalContext.current
 * in Compose is frequently a wrapper rather than a bare Activity, so a raw
 * cast `context as? Activity` returns null in production. This helper handles
 * the wrapping. Returns null only when there genuinely isn't an Activity
 * (e.g., the call site really is in a Service / Application context).
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
