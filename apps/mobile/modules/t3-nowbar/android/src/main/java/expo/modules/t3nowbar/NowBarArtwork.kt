package expo.modules.t3nowbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

/** Small, cached native artwork; no frame loop or fabricated progress. */
internal object NowBarArtwork {
  private var cachedKey = ""
  private var cached: Bitmap? = null

  fun emblem(phase: String, color: Int, progress: Int?): Bitmap {
    val key = "$phase:$color:$progress"
    if (key == cachedKey) cached?.let { return it }
    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(20f, 10f, 180f, 190f,
      Color.rgb(42, 32, 69), Color.rgb(10, 14, 27), Shader.TileMode.CLAMP)
    canvas.drawCircle(96f, 96f, 94f, paint)
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 7f
    paint.color = Color.argb(65, Color.red(color), Color.green(color), Color.blue(color))
    canvas.drawCircle(96f, 96f, 79f, paint)
    paint.color = color
    paint.strokeCap = Paint.Cap.ROUND
    if (progress != null) {
      canvas.drawArc(17f, 17f, 175f, 175f, -90f, progress * 3.6f, false, paint)
    } else {
      // Static signal arcs indicate activity, not an estimated percentage.
      for (start in listOf(-78f, 42f, 162f))
        canvas.drawArc(17f, 17f, 175f, 175f, start, 65f, false, paint)
    }
    paint.style = Paint.Style.FILL
    paint.typeface = Typeface.create("monospace", Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 57f
    val glyph = when (phase) {
      "input" -> "?"
      "approval" -> "!"
      "plan" -> "="
      "stopped" -> "■"
      "completed" -> "✓"
      "error" -> "!"
      "attention" -> "!"
      "offline" -> "||"
      "monitoring" -> "~"
      else -> ">_"
    }
    canvas.drawText(glyph, 96f, 96f - (paint.ascent() + paint.descent()) / 2f, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(156f, 42f, 6f, paint)
    cachedKey = key
    cached = bitmap
    return bitmap
  }
}
