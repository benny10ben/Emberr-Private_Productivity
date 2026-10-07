package com.emberr.domain.selfhost.sync

import com.emberr.domain.selfhost.translation.BlockTombstone
import com.emberr.domain.selfhost.translation.NoteBlockPayload
import com.emberr.domain.selfhost.translation.NoteJsonParser
import com.emberr.domain.selfhost.translation.NotePayload
import com.emberr.domain.selfhost.translation.NotePayloadSyncException
import com.emberr.domain.selfhost.webdav.WebDavDecryptionException
import com.emberr.domain.selfhost.webdav.WebDavException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReconcileOrSkipBrokenNoteTest {

    @Test
    fun aNoteWithBrokenContentIsSkipped() = runTest {
        val outcome = reconcileOrSkipBrokenNote("recipe") {
            throw NotePayloadSyncException("Failed to decode note payload JSON", IllegalStateException("bad json"))
        }

        assertEquals(ReconcileOutcome.BROKEN_NOTE, outcome)
    }

    @Test
    fun aNoteThatCannotBeDecryptedIsSkipped() = runTest {
        val outcome = reconcileOrSkipBrokenNote("recipe") {
            throw WebDavDecryptionException("Could not decrypt notes/recipe.json", IllegalStateException("tag mismatch"))
        }

        assertEquals(ReconcileOutcome.BROKEN_NOTE, outcome)
    }

    @Test
    fun aNoteWithABlockThatIsBothLiveAndDeletedIsSkipped() = runTest {
        val payloadJson = Json.encodeToString(
            NotePayload.serializer(),
            NotePayload(
                noteId = "recipe",
                title = "Recipe",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                filePath = "",
                blocks = listOf(
                    NoteBlockPayload(
                        blockId = "block-1",
                        displayOrder = 0,
                        updatedAt = 100L,
                        content = Json.parseToJsonElement("{\"type\":\"text\",\"id\":\"block-1\"}")
                    )
                ),
                tombstones = listOf(BlockTombstone("block-1", deletedAt = 200L))
            )
        )

        val outcome = reconcileOrSkipBrokenNote("recipe") {
            NoteJsonParser.parseJsonToDatabaseOperations(payloadJson)
            ReconcileOutcome.SYNCED
        }

        assertEquals(ReconcileOutcome.BROKEN_NOTE, outcome)
    }

    @Test
    fun theNotesAfterABrokenNoteStillSync() = runTest {
        val notesInSyncOrder = listOf("shopping", "recipe", "travel", "birthday")
        val brokenNote = "recipe"

        val outcomes = notesInSyncOrder.map { noteId ->
            reconcileOrSkipBrokenNote(noteId) {
                if (noteId == brokenNote) {
                    throw WebDavDecryptionException("Could not decrypt notes/$noteId.json", IllegalStateException())
                }
                ReconcileOutcome.SYNCED
            }
        }

        assertEquals(
            listOf(
                ReconcileOutcome.SYNCED,
                ReconcileOutcome.BROKEN_NOTE,
                ReconcileOutcome.SYNCED,
                ReconcileOutcome.SYNCED
            ),
            outcomes
        )
    }

    @Test
    fun aServerErrorStillStopsTheSync() = runTest {
        assertFailsWith<WebDavException> {
            reconcileOrSkipBrokenNote("travel") {
                throw WebDavException("GET failed for notes/travel.json with status 503", statusCode = 503)
            }
        }
    }

    @Test
    fun aCancelledSyncIsNotTreatedAsABrokenNote() = runTest {
        assertFailsWith<CancellationException> {
            reconcileOrSkipBrokenNote("travel") {
                throw CancellationException("sync was cancelled")
            }
        }
    }

    @Test
    fun noBrokenNotesShowsNoMessage() {
        assertNull(SelfHostSyncResult.Success(notesSynced = 4, conflicts = 0, brokenNotes = 0).brokenNotesMessage)
    }

    @Test
    fun oneBrokenNoteSaysOneNote() {
        assertEquals(
            "1 note couldn't be synced",
            SelfHostSyncResult.Success(notesSynced = 3, conflicts = 0, brokenNotes = 1).brokenNotesMessage
        )
    }

    @Test
    fun severalBrokenNotesSaysHowMany() {
        assertEquals(
            "3 notes couldn't be synced",
            SelfHostSyncResult.Success(notesSynced = 1, conflicts = 0, brokenNotes = 3).brokenNotesMessage
        )
    }
}
