package com.pointlessapps.filman.lite

/** Address of the Filman backend; the emulator/simulator loopback differs per platform. */
expect val defaultBaseUrl: String

/** Directory (created on demand) where downloaded videos and the index are stored. */
expect fun downloadsDirectory(): String
