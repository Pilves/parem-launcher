package com.parem.launcher.helper

/**
 * Decides what a lock gesture does on Android 9+, where locking needs
 * MyAccessibilityService. Android can turn the service off behind the user's
 * back (an app update, a crash, a security setting), and a service still
 * listed as enabled but not bound swallows the lock click silently — so a
 * service that has worked here before gets one explanation, not the
 * first-time consent sheet and not silence. An active device admin is the
 * fallback (M4-WP18): it locks via lockNow(), which costs biometric unlock, so
 * a running service always wins over it.
 */
object LockServiceCheck {

    enum class Outcome {
        /** Service is on and running: lock. */
        LOCK,
        /** Service can't lock but the device admin is active: DevicePolicyManager.lockNow(). */
        LOCK_ADMIN,
        /** Service never ran on this device: first-time consent sheet. */
        CONSENT,
        /** Service ran before and is now off: explain once. */
        EXPLAIN_OFF,
        /** Already explained since the service last ran: a short toast only. */
        OFF_EXPLAINED,
    }

    /**
     * @param enabled the service is listed in the system's enabled services
     * @param bound the system has the service bound and running
     * @param connectedBefore the service has connected on this device before
     * @param offExplained the explanation was shown since the service last connected
     * @param adminActive Parem's device admin is active
     */
    fun decide(
        enabled: Boolean,
        bound: Boolean,
        connectedBefore: Boolean,
        offExplained: Boolean,
        adminActive: Boolean,
    ): Outcome = when {
        enabled && bound -> Outcome.LOCK
        adminActive -> Outcome.LOCK_ADMIN
        !connectedBefore -> Outcome.CONSENT
        offExplained -> Outcome.OFF_EXPLAINED
        else -> Outcome.EXPLAIN_OFF
    }
}
