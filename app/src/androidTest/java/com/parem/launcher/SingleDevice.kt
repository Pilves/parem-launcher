package com.parem.launcher

/** State-only cases run on the measured fastest CI profile; all other tests run everywhere. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SingleDevice
