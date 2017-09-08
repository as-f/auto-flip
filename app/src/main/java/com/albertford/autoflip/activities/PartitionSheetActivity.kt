package com.albertford.autoflip.activities

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.albertford.autoflip.PartitionImageView
import com.albertford.autoflip.PdfSheetRenderer
import com.albertford.autoflip.R
import com.albertford.autoflip.SheetRenderer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_partition_sheet.*

class PartitionSheetActivity : AppCompatActivity() {

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    private var changeTitleButton: MenuItem? = null

    private var uri: String? = null
    private var sheetRenderer: SheetRenderer? = null
    private var pageIndex = 0

    private var title: String? = null

    private var sheetSubscription: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partition_sheet)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetBehavior?.setBottomSheetCallback(bottomSheetCallback)

        readUri()
        initPageCount()

        overlay_view.setOnTouchListener(overlayViewListener)
        sheet_image.setOnLongClickListener(sheet_image.onLongClickListener)
        bottom_sheet.setOnClickListener(bottomSheetListener)
        start_finish_button.setOnClickListener(startButtonListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        val sheetSub = sheetSubscription
        if (sheetSub != null && !sheetSub.isDisposed) {
            sheetSub.dispose()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_partition, menu)
        changeTitleButton = menu?.findItem(R.id.action_title)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.action_title) {
            toggleTitle()
        }
        return super.onOptionsItemSelected(item)
    }

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        /** Darken the sheet image when the bottom sheet is expanded */
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            overlay_view.opacity = slideOffset
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {

        }
    }

    /** Collapse bottom sheet when you click outside it */
    private val overlayViewListener = View.OnTouchListener { _, _ ->
        if (overlay_view.opacity == 1f) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
        }
    }

    private val bottomSheetListener = View.OnClickListener {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private val startButtonListener = View.OnClickListener {
        start_finish_button.text = resources.getString(R.string.finish)
        start_finish_button.setOnClickListener(finishButtonListener)
        val renderer = sheetRenderer
        if (renderer is PdfSheetRenderer) {
            if (renderer.getPageCount() != 1) {
                start_finish_button.visibility = View.GONE
                next_page_button.visibility = View.VISIBLE
            }
        }
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private val finishButtonListener = View.OnClickListener {

    }

    private fun readUri() {
        val pdfUriString = intent.getStringExtra("PDF")
        val imageUriString = intent.getStringExtra("IMAGE")
        if (pdfUriString != null) {
            uri = pdfUriString
            val pdfSingle = Single.fromCallable {
                PdfSheetRenderer(this, pdfUriString)
            }
            sheetSubscription = pdfSingle
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ renderer ->
                        sheetRenderer = renderer
                        sheet_image.post {
                            renderCurrentPage()
                            initPageCount()
                        }
                    }, { error ->
                        Log.e("AutoFlip", error.toString())
                    })
        } else if (imageUriString != null) {
            uri = imageUriString
        } else {
            finish()
        }
    }

    private fun initPageCount() {
        val renderer = sheetRenderer
        page_number_text.text = (pageIndex + 1).toString()
        if (renderer is PdfSheetRenderer) {
            of_text.visibility = View.VISIBLE
            page_count_text.text = renderer.getPageCount().toString()
            page_count_text.visibility = View.VISIBLE
        }
    }

    private fun toggleTitle() {
        if (title == null) {
            title = title_field.text.toString()
            if (title == "") {
                title = resources.getString(R.string.untitled)
            }
            toolbar.title = title
            title_field.visibility = View.GONE
            changeTitleButton?.setIcon(R.drawable.ic_mode_edit_white_24dp)
            changeTitleButton?.title = resources.getString(R.string.action_edit)
        } else {
            title_field.visibility = View.VISIBLE
            title = null
            changeTitleButton?.setIcon(R.drawable.ic_done_white_24dp)
            changeTitleButton?.title = resources.getString(R.string.action_save)
        }
    }

    private fun renderCurrentPage() {
        val bitmap = sheetRenderer?.renderFullPage(pageIndex, sheet_image.width)
        sheet_image.setImageBitmap(bitmap)
    }
}