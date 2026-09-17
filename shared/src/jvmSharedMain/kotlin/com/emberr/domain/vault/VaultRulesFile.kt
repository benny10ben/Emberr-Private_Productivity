// Writes the CLAUDE.md rulebook into the vault so an AI tool knows the format before it edits anything.

package com.emberr.domain.vault

import java.io.File
import java.io.IOException

// Written only when missing, so edits made to it survive.
object VaultRulesFile {

    fun writeIfMissing(vaultRootDirectory: File): Boolean {
        try {
            if (!vaultRootDirectory.isDirectory && !vaultRootDirectory.mkdirs()) return false

            val rulesFile = File(vaultRootDirectory, VaultPaths.VAULT_RULES_FILE_NAME)
            if (rulesFile.exists()) return false

            rulesFile.writeText(RULES_TEXT)
            VaultLog.d("wrote ${VaultPaths.VAULT_RULES_FILE_NAME}")
            return true
        } catch (cause: IOException) {
            VaultLog.e("Could not write ${VaultPaths.VAULT_RULES_FILE_NAME}: ${cause.message}")
            return false
        } catch (cause: SecurityException) {
            VaultLog.e("Could not write ${VaultPaths.VAULT_RULES_FILE_NAME}: ${cause.message}")
            return false
        }
    }

    private val RULES_TEXT = """
        # Emberr notes

        This folder mirrors the notes in the Emberr app. Editing a file here changes the real
        note, usually within a second. Emberr has to be running for that to happen.

        Every top-level directory ending in `(Space)` is one space in the app, for example
        `Personal(Space)`. Notes only ever live inside one of them. A new `X(Space)` directory
        creates a space called X, and moving a note's file into a different space does nothing -
        Emberr writes it back to the space that owns it.

        Each space directory holds a hidden `.emberr-space` file naming that space. Leave it alone.
        It is what lets Emberr recognise a space after you rename its directory, and what lets a
        vault copied to another device rejoin the same space instead of becoming a second one. If
        you delete it, Emberr falls back to matching on the directory name and writes it again.

    """.trimIndent() + "\n\n" + VaultMarkdownFormatGuide.TEXT + "\n\n" + """
        ## How to do things

        | Goal | Do this |
        |---|---|
        | edit a block | change the text, leave the tag alone |
        | delete a block | delete the whole block including its tag |
        | add a block | write it with no tag |
        | reorder blocks | move the lines, the tags travel with them |
        | rename a note | change `title:` at the top, not the file name |
        | favourite a note | set `favorite: true` at the top |
        | create a note | make a new `.md` file in any directory - a new directory becomes a folder |
        | delete a note | delete the file, which moves the note to Trash rather than destroying it |

        Renaming the file itself does nothing. Emberr builds the file name from `title:` and will
        rename it back.

        `Daily/` and `Subnotes/` inside a space directory belong to Emberr. Do not create folders
        with those names, and do not put new notes in them.

        `Inbox.md` is where the app drops anything captured quickly, so Emberr always keeps it.
        Deleting that file empties the note instead of trashing it, and the empty file comes back.

        ## Conflict files

        If you and someone using the app change the same block at the same time, your version is
        kept and theirs is saved beside it as `<name>.conflict.md`. Nothing is lost. Emberr
        ignores those files, so read one and delete it when you are done with it.
    """.trimIndent() + "\n"
}
