package com.example.timelineviewer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMetadataEditorTest {

    @Test
    fun `accepts and trims a practical title and description`() {
        val result = JourneyMetadataEditor.validate("  Summer in Prague  ", "  A slow walk through the old town.  ")

        assertTrue(result.isValid)
        assertEquals("Summer in Prague", result.metadata?.title)
        assertEquals("A slow walk through the old town.", result.metadata?.description)
    }

    @Test
    fun `rejects a blank title without rejecting an empty description`() {
        val result = JourneyMetadataEditor.validate("   ", "")

        assertFalse(result.isValid)
        assertEquals("A journey title is required.", result.titleError)
        assertEquals(null, result.descriptionError)
    }

    @Test
    fun `enforces concise metadata limits`() {
        val tooLongTitle = "T".repeat(JourneyMetadataEditor.MAX_TITLE_LENGTH + 1)
        val tooLongDescription = "D".repeat(JourneyMetadataEditor.MAX_DESCRIPTION_LENGTH + 1)

        val result = JourneyMetadataEditor.validate(tooLongTitle, tooLongDescription)

        assertFalse(result.isValid)
        assertTrue(requireNotNull(result.titleError).contains(JourneyMetadataEditor.MAX_TITLE_LENGTH.toString()))
        assertTrue(requireNotNull(result.descriptionError).contains(JourneyMetadataEditor.MAX_DESCRIPTION_LENGTH.toString()))
    }
}
