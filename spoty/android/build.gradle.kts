// Versions are pinned to the set already resolved on this machine, because
// dl.google.com is not reachable here without a VPN session and an unpinned
// upgrade would silently need one.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.chaquo.python") version "16.1.0" apply false
}
