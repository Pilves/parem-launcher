package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import com.parem.launcher.R
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.helper.copyToClipboard
import com.parem.launcher.helper.showToast

/**
 * Offers the report saved by [CrashReporter] after a crash. Every way out
 * (an option or a swipe-away) deletes it, so the sheet shows once per crash;
 * BottomSheetMenu dismisses before an option runs, and [report] is already
 * in memory, so Send and Copy still have it.
 */
fun showCrashReport(context: Context, report: String) {
    val preview = report.lineSequence().take(8).joinToString("\n")
    BottomSheetMenu(context)
        .title(context.getString(R.string.crash_report_title))
        .message(context.getString(R.string.crash_report_body) + "\n\n" + preview)
        .option(context.getString(R.string.crash_report_send)) {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
                .putExtra(Intent.EXTRA_TEXT, report)
            context.startActivity(Intent.createChooser(send, null))
        }
        .option(context.getString(R.string.crash_report_copy)) {
            context.copyToClipboard(report)
            context.showToast(R.string.copied)
        }
        .option(context.getString(R.string.crash_report_discard), dimmed = true) {}
        .onDismiss { CrashReporter.clear(context) }
        .show()
}
