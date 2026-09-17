# Database Architecture

How Emberr stores data on the device.

## Two databases

| File | Engine | Holds |
|---|---|---|
| `emberr_database.db` | Room | Notes, blocks, folders, tags, calendar, chats, spaces |
| `emberr_ai_index.db` | SQLDelight | Text chunks and embeddings for AI search |

Desktop keeps both in `~/.emberr/`. Android keeps them in its private app directory. Both are created in `AndroidModule.kt` and `DesktopModule.kt`.

The AI index is separate because it can be deleted and rebuilt from the main database, so re-indexing can never damage a note.

## Spaces

A space keeps content apart, like "Work" and "Personal". One user can have many.

There is still only one database. A space is just a `spaceId` column on each table:

```
notes_metadata
  noteId   title      spaceId
  abc      Meeting    work-uuid
  def      Grocery    personal-uuid
```

One file is simpler to open, back up and sync than one database per space.

**The `spaces` table:** `spaceId` (UUID primary key, the only safe way to refer to a space), `displayName` (renameable, so never an id), `sortOrder`, `updatedAt` (picks the winner when devices disagree), `isDeleted` (so a deletion reaches other devices).

The first space is always id `00000000-0000-0000-0000-000000000001`, named `Default`. Fixing the id means two devices set up offline agree on it instead of each making their own "Default".

**Active space:** stored in preferences as `active_space_id`, touched only by `ActiveSpaceStore`. Never synced, because switching to Work on your laptop should not switch your phone.

**How the wall holds:** DAO queries take a `spaceId` argument, so the filter cannot be forgotten. The code will not compile without it.

```kotlin
@Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 0 …")
fun getAllNotes(spaceId: String): Flow<List<NoteMetadataEntity>>
```

Repositories pass the active space in, so screens never handle space ids. Queries that read every space on purpose end in `AcrossSpaces`, used by sync and backup.

## Tables

### Notes

`notes_metadata` is one row per note: title, icon, folder, favourite, cover, timestamps. No content, so lists and search stay fast. Four flags say what a note is:

- `isDaily` + `dateString` — a journal entry for one day
- `isSubNote` — a note inside another note
- `isTemplate` — a blueprint, hidden from normal lists
- `trashedAt` — in the trash, still recoverable

The `filePath` column is left over from an older design and is unused. Every write sets it to `""`.

`note_blocks` holds the content, one row per block:

```kotlin
data class NoteBlockEntity(
    val blockId: String,
    val noteId: String,
    val displayOrder: Int,
    val blockDataJson: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)
```

- `blockDataJson` is the whole block as JSON, so a new block type needs no schema change. SQL cannot look inside it, so content search is a `LIKE` on the text.
- `isDeleted` keeps the row instead of removing it. That makes undo work and stops sync from adding the block back.
- `noteId` has `ON DELETE CASCADE`, so deleting a note deletes its blocks.
- There is no `spaceId`. A block belongs to its note's space. Queries starting from blocks must join back to `notes_metadata` to filter by space, or Personal search finds Work notes.

### Organising

`folders` (a tree via `parentFolderId`), `global_tags`, `calendar_categories` — all per space.

`database_templates` holds saved table layouts, columns and views only, never rows. Not per space, because a layout is a tool, not content.

### Calendar

`calendar_tasks` is a copy, not the original. Checkbox blocks with a date are flattened here so the calendar can search by date without opening every note. Rebuilt on every save.

`calendar_event_exceptions` stores changes to one occurrence of a repeating event, keyed `(blockId, occurrenceDate)`. A repeating event is a single block with a rule, expanded when read, so future occurrences have no rows. Ticking, moving or cancelling one day is saved here.

### Other tables

- `image_blocks`, `document_blocks`, `bookmark_blocks` — copies of media blocks so galleries and bookmark lists load fast. Same idea as `calendar_tasks`.
- `media_references` — maps `(noteId, fileName)` so the app knows which files are still used.
- `chat_sessions` — AI conversations, messages as JSON, per space.
- `self_host_deleted_notes` — remembers a permanent deletion. Without it another device cannot tell "deleted" from "never existed here".
- `self_host_deleted_api_configs` — the same for provider settings.

## Media files

Files sit in a media folder (`~/.emberr/media` on desktop) and the database stores only the path.

The folder is flat, not split per space, so an image used in two spaces is stored once. `media_references` is the only link from a file to a note.

Unused media is cleaned up at launch only, never on delete, because undo can bring a block back and it still needs its image.

## AI index

One table, `block_metadata`: `block_id`, `note_id`, `space_id`, `chunk_text`, `embedding`.

`space_id` is repeated here on purpose. AI search compares a question against every vector, so the index filters by space itself instead of loading everything and asking the main database. Reads go through `getBlocksInSpace`, so a question in Personal cannot be answered from a Work note.

## Encryption at rest

This differs by platform.

On Android both databases are encrypted with SQLCipher. The key is a random 32-byte passphrase in `emberr_db_key.bin`, itself encrypted by a master key held in the Android Keystore. It is generated once on first launch. If it cannot be decrypted the app refuses to start rather than making a new one, because a new key would leave every existing note unreadable.

On desktop neither database is encrypted. Room uses the bundled SQLite driver and the AI index uses a plain JDBC driver, both with no key. Anything with access to `~/.emberr/` can read your notes.

Separately, sync traffic and backup files are always encrypted with AES-GCM on both platforms.

## Migrations

Version 1 with `fallbackToDestructiveMigration(dropAllTables = true)`. Schema changes are made directly and the app reinstalled.

That works only because there are no users yet. Once there are, every change needs a version bump and an `AutoMigration`, kept forever. See `DatabaseMigrations.kt`. The schema is exported to `shared/schemas/`.

Adding a column changes Room's identity hash, so the old database is dropped even without a version bump. Expected in development.

## Rules to follow

- Use `@Upsert` on `notes_metadata`, never `OnConflictStrategy.REPLACE`. REPLACE deletes the row first, the cascade fires, and all the note's blocks are lost.
- Always set `NoteBlock.updatedAt`. It defaults to `0L`, which looks very old, so sync always picks the other device's copy.
- Look up daily notes by `(spaceId, dateString)`, never by id. Two devices can each create a row for the same day before syncing.
- Never write to `calendar_tasks`, `image_blocks`, `document_blocks` or `bookmark_blocks`. They are rebuilt from blocks, so edits are lost on the next save.
- Treat a query without `spaceId` as a leak. If it truly needs every space, name it `AcrossSpaces`.
