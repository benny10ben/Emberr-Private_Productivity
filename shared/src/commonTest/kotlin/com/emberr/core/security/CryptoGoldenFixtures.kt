package com.emberr.core.security

object CryptoGoldenFixtures {

    const val KEY = "emberr-golden-test-key"

    const val PAYLOAD_PLAIN_TEXT =
        "{\"noteId\":\"golden-1\",\"text\":\"Emberr cross platform payload\"}"

    const val PAYLOAD_BASE64 =
        "AQIDBAUGBwgJCgsMfnSFkIL1emJljN0anOGUpiNDcDfSi2Ixk22ed2RqJSj7nq/eBSd4IzMnk2Zn" +
            "6IEjSf0HN8wjyvfnk5nEBsPFTKfx2uTkiFsU3WNx9w=="

    const val STREAM_PLAIN_TEXT = "Emberr golden stream payload"

    const val STREAM_BASE64 =
        "AQIDBAUGBwgJCgsMAAAALEA7iZqE4hNhKNqbGJ2tg7c/CyB43tl3LYd23SnO0OxLE0L7ES4HVk8f" +
            "wz9C"

    const val SIGNING_SECRET = "emberr-pairing-secret"

    const val SIGNED_MESSAGE = "emberr golden signed message"

    const val EXPECTED_SIGNATURE =
        "bacbc7993788c324b133bdcda44541ca1f5f879240cccd14f03fc8f1b6650333"
}
