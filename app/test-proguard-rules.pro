# AndroidX Test's optional screenshot capture code references a newer futures
# adapter than the app supplies. ReleaseSmokeTest does not call that API.
-dontwarn androidx.concurrent.futures.SuspendToFutureAdapter
