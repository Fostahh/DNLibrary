package id.dn.fostah.dnlibrary

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform