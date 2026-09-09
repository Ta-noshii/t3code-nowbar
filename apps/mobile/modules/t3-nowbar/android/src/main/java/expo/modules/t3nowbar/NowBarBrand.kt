package expo.modules.t3nowbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat

object NowBarBrand {
  fun resource(provider: String, model: String): Int {
    val name = model.lowercase()
    return when {
      name.contains("claude") -> R.drawable.nowbar_claude
      Regex("(^|[/_-])(gpt|codex|o[134])").containsMatchIn(name) -> R.drawable.nowbar_openai
      name.contains("grok") -> R.drawable.nowbar_grok
      provider.lowercase().contains("claude") -> R.drawable.nowbar_claude
      provider.lowercase().contains("codex") -> R.drawable.nowbar_openai
      provider.lowercase().contains("cursor") -> R.drawable.nowbar_cursor
      provider.lowercase().contains("grok") -> R.drawable.nowbar_grok
      provider.lowercase().contains("opencode") -> R.drawable.nowbar_opencode
      else -> R.drawable.nowbar_pulse
    }
  }

  fun bitmap(context: Context, provider: String, model: String): Bitmap {
    val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
    ContextCompat.getDrawable(context, resource(provider, model))!!.apply {
      setBounds(6, 6, 74, 74); draw(Canvas(bitmap))
    }
    return bitmap
  }
}
