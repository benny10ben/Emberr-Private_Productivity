// The data shape for one AI-proposed vault change, from proposal through to its final outcome.

package com.emberr.domain.ai.tools

import kotlinx.serialization.Serializable

enum class VaultPendingWriteKind { CREATE, UPDATE, APPEND, DELETE }

enum class VaultPendingWriteStatus { PENDING, APPLIED, REJECTED, FAILED }

@Serializable
data class VaultPendingWrite(
    val kind: VaultPendingWriteKind,
    val relativePath: String,
    val spaceId: String = "",
    val previousContent: String? = null,
    val proposedContent: String? = null,
    val status: VaultPendingWriteStatus = VaultPendingWriteStatus.PENDING,
    val failureReason: String? = null
)
