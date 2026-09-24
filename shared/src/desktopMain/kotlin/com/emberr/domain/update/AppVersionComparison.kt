package com.emberr.domain.update

fun isNewerVersion(candidateVersion: String, installedVersion: String): Boolean {
    val candidateParts = versionPartsOf(candidateVersion) ?: return false
    val installedParts = versionPartsOf(installedVersion) ?: return false

    for (index in 0 until maxOf(candidateParts.size, installedParts.size)) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val installedPart = installedParts.getOrElse(index) { 0 }
        if (candidatePart != installedPart) return candidatePart > installedPart
    }
    return false
}

private fun versionPartsOf(version: String): List<Int>? =
    version.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
