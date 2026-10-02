package srooms.protocol

import kotlinx.serialization.json.Json

val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
