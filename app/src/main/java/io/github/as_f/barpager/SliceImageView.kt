package io.github.as_f.barpager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import io.github.as_f.barpager.models.*

const val HANDLE_PADDING = 50
const val DASH_LENGTH = 10
const val MINIMUM_PROJECTION = 10f

val black = makePaint(128, 0, 0, 0)
val white = makePaint(255, 255, 255, 255)

class SliceImageView(context: Context?, attrs: AttributeSet?) : ImageView(context, attrs) {

  val accent = getAccentPaint()

  var sheet: Sheet = Sheet()
  var selection: Selection = StaffSelection(0f, 0f)

  var renderer: PdfRenderer? = null

  var activePointerId = MotionEvent.INVALID_POINTER_ID
  var lastTouchX = 0f
  var lastTouchY = 0f

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)
    if (canvas != null) {
      selection.mask(canvas, sheet.pages.last())
      drawSheet(canvas)
      selection.project(canvas, sheet)
      selection.drawHandles(canvas, sheet.pages.last(), accent)
    }
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    return when (event?.action) {
      MotionEvent.ACTION_DOWN -> onActionDown(event)
      MotionEvent.ACTION_MOVE -> onActionMove(event)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onActionCancel()
      MotionEvent.ACTION_POINTER_UP -> onActionPointerUp(event)
      else -> super.onTouchEvent(event)
    }
  }

  fun onActionDown(event: MotionEvent): Boolean {
    return if (selection.handleTouched(event)) {
      lastTouchX = event.x
      lastTouchY = event.y
      activePointerId = event.getPointerId(event.actionIndex)
      true
    } else {
      activePointerId = MotionEvent.INVALID_POINTER_ID
      false
    }
  }

  fun onActionMove(event: MotionEvent): Boolean {
    if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
      return false
    }

    val pointerIndex = event.findPointerIndex(activePointerId)
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val dx = x - lastTouchX
    val dy = y - lastTouchY
    lastTouchX = x
    lastTouchY = y

    selection.move(sheet.pages.last(), dx, dy, width.toFloat(), height.toFloat())
    invalidate()
    return true
  }

  fun onActionCancel(): Boolean {
    activePointerId = MotionEvent.INVALID_POINTER_ID
    return true
  }

  fun onActionPointerUp(event: MotionEvent): Boolean {
    val pointerId = event.getPointerId(event.actionIndex)
    if (pointerId == activePointerId) {
      val otherPointerIndex = if (event.actionIndex == 0) 1 else 0
      lastTouchX = event.getX(otherPointerIndex)
      lastTouchY = event.getY(otherPointerIndex)
      activePointerId = event.getPointerId(otherPointerIndex)
    }
    return true
  }

  fun drawSheet(canvas: Canvas) {
    val page = sheet.pages[sheet.pages.size - 1]
    for (staff in page.staves) {
      val startY = staff.startY
      val endY = staff.endY
      drawHorizontal(canvas, startY, 0f, width.toFloat(), white)
      drawHorizontal(canvas, endY, 0f, width.toFloat(), white)
      for (barLine in staff.barLines) {
        drawVertical(canvas, barLine.x, startY, endY, white)
      }
    }
  }

  fun saveSelection() {
    selection = selection.save(sheet)
    invalidate()
  }

  fun nextStaff() {
    selection = suggestStaff(sheet)
    invalidate()
  }

  /**
   * @return Whether the current page (after this function executes) is the last one
   */
  fun nextPage(): Boolean {
    renderPage(sheet.pages.size)
    return sheet.pages.size + 1 == renderer?.pageCount
  }

  fun renderPage(i: Int) {
    val renderer = renderer
    if (renderer != null) {
      val pageRenderer = renderer.openPage(i)
      val imageWidth = pointsToPixels(pageRenderer.width)
      val imageHeight = pointsToPixels(pageRenderer.height)
      val fitToWidthScale = maxWidth.toFloat() / imageWidth
      val scaledWidth = Math.round(imageWidth * fitToWidthScale)
      val scaledHeight = Math.round(imageHeight * fitToWidthScale)
      val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
      pageRenderer.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      pageRenderer.close()
      setImageBitmap(bitmap)

      if (sheet.pages.size == i) {
        sheet.pages.add(Page(scaledWidth, scaledHeight))
        selection = suggestStaff(sheet)
      }
    }
  }

  private fun pointsToPixels(pixels: Int): Int {
    return resources.displayMetrics.densityDpi * pixels / 72
  }

  private fun getAccentPaint(): Paint {
    val paint = Paint()
    paint.color = ContextCompat.getColor(context, R.color.colorAccent)
    return paint
  }

}

/**
 * Whether a coordinate is within less than HANDLE_PADDING pixels of a 1-pixel thick line
 */
fun nearLine(coord: Float, target: Float): Boolean {
  return Math.abs(coord - target) <= HANDLE_PADDING
}

fun clamp(num: Float, min: Float, max: Float): Float {
  return if (num < min) {
    min
  } else if (num > max) {
    max
  } else {
    num
  }
}

fun drawHorizontal(canvas: Canvas, y: Float, startX: Float, endX: Float, paint: Paint) {
  canvas.drawRect(startX, y - 0.5f, endX, y + 0.5f, paint)
}

fun drawVertical(canvas: Canvas, x: Float, startY: Float, endY: Float, paint: Paint) {
  canvas.drawRect(x - 0.5f, startY, x + 0.5f, endY, paint)
}

fun drawHorizontalDashed(canvas: Canvas, y: Float, startX: Float, endX: Float, paint: Paint) {
  var x = startX
  while (x < endX) {
    canvas.drawRect(x, y - 0.5f, x + DASH_LENGTH, y + 0.5f, paint)
    x += 2 * DASH_LENGTH
  }
}

fun projectHorizontal(canvas: Canvas, initY: Float, period: Float, paint: Paint) {
  var y = initY + period
  while (y > 0 && y < canvas.height) {
    drawHorizontalDashed(canvas, y, 0f, canvas.width.toFloat(), paint)
    y += period
  }
}

fun projectVertical(canvas: Canvas, initX: Float, period: Float, startY: Float, endY: Float, paint: Paint) {
  var x = initX + period
  while (x > 0 && x < canvas.width) {
    drawVerticalDashed(canvas, x, startY, endY, paint)
    x += period
  }
}

fun drawVerticalDashed(canvas: Canvas, x: Float, startY: Float, endY: Float, paint: Paint) {
  var y = startY
  while (y < endY) {
    canvas.drawRect(x - 0.5f, y, x + 0.5f, y + DASH_LENGTH, paint)
    y += 2 * DASH_LENGTH
  }
}

fun makePaint(a: Int, r: Int, g: Int, b: Int): Paint {
  val paint = Paint()
  paint.setARGB(a, r, g, b)
  paint.style = Paint.Style.FILL
  return paint
}

fun fadePaint(period: Float): Paint {
  return if (period > 2 * MINIMUM_PROJECTION) {
    white
  } else {
    val alpha = 255 * (period - MINIMUM_PROJECTION) / (MINIMUM_PROJECTION)
    makePaint(alpha.toInt(), 255, 255, 255)
  }
}
