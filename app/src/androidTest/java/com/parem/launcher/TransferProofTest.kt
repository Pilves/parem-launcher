package com.parem.launcher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Prefs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Invoked individually, without Android Test Orchestrator, by a disposable-emulator script. */
@RunWith(AndroidJUnit4::class)
class TransferProofTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun requireTransferFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("transfer_fixture") == "true")
    }

    @Test fun seedSource() {
        assertTrue(preferences.edit().clear().putString("TRANSFER_PROOF_SENTINEL", "source")
            .putBoolean("GRAYSCALE_SUPPRESSED", true).commit())
    }

    @Test fun seedDestination() {
        assertTrue(preferences.edit().clear().putBoolean("FIRST_OPEN", false)
            .putString("TRANSFER_PROOF_SENTINEL", "destination")
            .putFloat("TEXT_SIZE_SCALE", 1.3f).commit())
        File(context.noBackupFilesDir, "transfer-proof-canary").writeText("destination-survived")
    }
}
