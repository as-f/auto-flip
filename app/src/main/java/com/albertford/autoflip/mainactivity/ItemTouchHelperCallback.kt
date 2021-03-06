package com.albertford.autoflip.mainactivity

import android.graphics.Canvas
import android.support.design.widget.Snackbar
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import com.albertford.autoflip.R
import com.albertford.autoflip.database
import com.albertford.autoflip.room.Sheet
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers

/**
 * Callback for touching items in the main activity's recyclerview.
 * This class allows swiping items to the right to delete them.
 * When the user swipes items away, a snackbar allows them to undo the delete for a short while.
 * If the user does not choose to undo, the item is actually deleted from the database.
 */

class ItemTouchHelperCallback(private val sheetAdapter: SheetAdapter) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder): Int =
            makeMovementFlags(0, ItemTouchHelper.END)

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = sheetAdapter.sheets.isNotEmpty()

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position >= sheetAdapter.sheets.size) {
            return
        }
        val deletedSheet = sheetAdapter.sheets.removeAt(position)
        sheetAdapter.notifyItemRemoved(position)
        Snackbar.make(viewHolder.itemView, R.string.deleted_msg, Snackbar.LENGTH_SHORT)
                .setAction(R.string.undo) { undoDelete(position, deletedSheet) }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                            Completable.fromAction {
                                database?.sheetDao()?.deleteSheets(deletedSheet)
                            }.subscribeOn(Schedulers.io()).subscribe()
                        }
                    }
                })
                .show()
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int,
            isCurrentlyActive: Boolean) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

        viewHolder.itemView.elevation = Math.min(8f, 1f + dX / 48)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder): Boolean = false

    private fun undoDelete(position: Int, deletedSheet: Sheet) {
        sheetAdapter.sheets.add(position, deletedSheet)
        if (sheetAdapter.sheets.size == 1) {
            sheetAdapter.notifyItemChanged(position)
        } else {
            sheetAdapter.notifyItemInserted(position)
        }
    }
}
