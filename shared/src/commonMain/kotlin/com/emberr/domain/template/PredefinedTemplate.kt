package com.emberr.domain.template

import com.emberr.domain.model.NoteContent

/**
 * Definition of a template the app ships with out of the box. Each implementation lives in its
 * own file under this package (see [ProjectsTemplate], [ResearchTemplate]) so adding another
 * default later is a one-file addition plus a single line in [PREDEFINED_TEMPLATES] - nothing
 * else in the seeding/DB layer has to change.
 */
interface PredefinedTemplate {
    // Fixed, hardcoded id (never a random UUID) so DefaultTemplateSeeder can tell "already
    // seeded" apart from "missing" across every app launch without a separate flag/table.
    val templateId: String
    val title: String
    val icon: String?
    fun buildContent(): NoteContent
}

fun PredefinedTemplate.noteIdInSpace(spaceId: String): String = "${templateId}_$spaceId"
