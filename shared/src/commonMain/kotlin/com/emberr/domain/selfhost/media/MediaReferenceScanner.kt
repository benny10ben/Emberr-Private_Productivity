package com.emberr.domain.selfhost.media

import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.VoiceBlock

object MediaReferenceScanner {

    fun extractMediaFileNames(blocks: List<NoteBlock>): Set<String> {
        val fileNames = mutableSetOf<String>()
        blocks.forEach { block ->
            if (block.isDeleted) return@forEach
            when (block) {
                is ImageBlock -> block.localFilePath?.substringAfterLast("/")?.let { fileNames.add(it) }
                is DocumentBlock -> block.localFilePath?.substringAfterLast("/")?.let { fileNames.add(it) }
                is VoiceBlock -> block.localFilePath?.substringAfterLast("/")?.let { fileNames.add(it) }
                else -> Unit
            }
        }
        return fileNames
    }
}