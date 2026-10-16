package com.parem.launcher

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import com.parem.launcher.data.Prefs

/**
 * Auto backup still does the work (fullBackupOnly, rules in res/xml); this
 * agent exists for one hook. A cloud restore or device transfer brings the
 * main prefs file over whole, and its device-moment keys describe the old
 * phone: Prefs.deviceOnlyKeys are cleared once the restore has landed.
 */
class ParemBackupAgent : BackupAgent() {

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        Prefs(this).clearDeviceOnlyState()
    }
}
