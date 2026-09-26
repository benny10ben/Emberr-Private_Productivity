// The vault markdown format spec, shared by the CLAUDE.md rulebook and the AI vault tool instructions.

package com.emberr.domain.vault

object VaultMarkdownFormatGuide {

    val TEXT = """
        ## Block tags

        Every block ends with a small tag like `^em-c3d5`. It is that block's permanent id,
        and it is how an edit reaches the right block instead of rewriting the whole note.

        1. Never remove or change a `^em-xxxx` tag on a block you are not replacing.
        2. New content does not need a tag - one is added automatically.
        3. Never invent a tag yourself.
        4. A block can be several lines long. The tag sits at the end of the whole block, not
           at the end of every line.

        ## Formatting

        Use `**bold**`, `*italic*`, `~~strikethrough~~` and `<u>underline</u>`.

        Do not use `_` for emphasis. It is left as a literal underscore on purpose, so file names
        and snake_case survive untouched.

        ## Lists, quotes, code

        ```markdown
        - [ ] a task ^em-c3d5
        - [x] a finished task ^em-d4e6
        - a bullet ^em-e5f7
        1. a numbered item ^em-f6a8
        - ▸ a collapsible toggle ^em-a7b9
        > a quote ^em-b8c1
        ```

        Indent a list item by two spaces per level. A code block puts its tag on its own line
        after the closing fence.

        ## Task due dates and repeats

        A checkbox can carry a small group at the end of the line, before the tag:

        ```markdown
        - [ ] Email finance {due: 2026-09-12 14:00} ^em-c3d5
        - [ ] Standup {due: 2026-09-14 09:30; for: 15m; category: Work; repeat: weekly on mon,wed} ^em-d4e6
        ```

        | Key | Meaning |
        |---|---|
        | `due` | `YYYY-MM-DD HH:MM` in local time. The date on its own means midnight |
        | `for` | how long it takes, like `45m`. Left out when it is the default 30 minutes |
        | `category` | a calendar category by name, which has to already exist |
        | `repeat` | see below |
        | `link` | a web address to open from the task |
        | `details` | a longer note about the task |

        Inside a value, write `\;` for a semicolon, `\{` and `\}` for braces, and `\n` for a line
        break, so the whole group stays on one line.

        Repeat reads the way you would say it:

        ```
        daily
        every 3 days
        weekly
        weekly on mon,wed,fri
        every 2 weeks on mon
        monthly
        yearly until 2030-01-01
        ```

        Drop the whole group to clear all of it. Drop one key to clear just that one. Any key we do
        not recognise makes the braces ordinary text, so `{some note}` at the end of a line stays
        exactly as typed.

        ## Tables

        A plain table is just a markdown table with its tag on the line below.

        ## Things that cannot be represented this way

        - drawings, which appear only as a stroke count
        - voice recordings
        - cell colours, column widths

        None of this is at risk. Emberr keeps it all. It simply is not written into this
        markdown, so leave those fences and settings alone.
    """.trimIndent()
}
