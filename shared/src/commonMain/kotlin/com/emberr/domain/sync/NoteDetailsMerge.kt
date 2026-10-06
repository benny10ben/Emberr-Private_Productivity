package com.emberr.domain.sync

import com.emberr.data.local.room.entity.NoteMetadataEntity

fun NoteMetadataEntity.withNewerDetailsFrom(otherCopy: NoteMetadataEntity?): NoteMetadataEntity {
    if (otherCopy == null) return this
    val titleSource = if (otherCopy.titleUpdatedAt > titleUpdatedAt) otherCopy else this
    val folderSource = if (otherCopy.folderUpdatedAt > folderUpdatedAt) otherCopy else this
    val iconSource = if (otherCopy.iconUpdatedAt > iconUpdatedAt) otherCopy else this
    val favoriteSource = if (otherCopy.favoriteUpdatedAt > favoriteUpdatedAt) otherCopy else this
    val coverImageSource = if (otherCopy.coverImageUpdatedAt > coverImageUpdatedAt) otherCopy else this
    val wordCountSource = if (otherCopy.wordCountUpdatedAt > wordCountUpdatedAt) otherCopy else this
    return copy(
        title = titleSource.title,
        titleUpdatedAt = titleSource.titleUpdatedAt,
        folderId = folderSource.folderId,
        folderUpdatedAt = folderSource.folderUpdatedAt,
        icon = iconSource.icon,
        iconUpdatedAt = iconSource.iconUpdatedAt,
        isFavorite = favoriteSource.isFavorite,
        favoriteUpdatedAt = favoriteSource.favoriteUpdatedAt,
        coverImagePath = coverImageSource.coverImagePath,
        coverImageUpdatedAt = coverImageSource.coverImageUpdatedAt,
        showWordCount = wordCountSource.showWordCount,
        wordCountUpdatedAt = wordCountSource.wordCountUpdatedAt
    )
}
