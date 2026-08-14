package com.example.timelineviewer.data.model

/**
 * The only mutable Journey fields in the first editor release. Route points, stops, transport
 * segments, offline map packs, and computed Journey Brief data remain untouched.
 */
data class JourneyMetadata(
    val title: String,
    val description: String
)

data class JourneyMetadataValidation(
    val metadata: JourneyMetadata? = null,
    val titleError: String? = null,
    val descriptionError: String? = null
) {
    val isValid: Boolean get() = metadata != null
}

object JourneyMetadataEditor {
    const val MAX_TITLE_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 600

    fun validate(title: String, description: String): JourneyMetadataValidation {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val titleError = when {
            cleanTitle.isEmpty() -> "A journey title is required."
            cleanTitle.length > MAX_TITLE_LENGTH -> "Keep the title within $MAX_TITLE_LENGTH characters."
            else -> null
        }
        val descriptionError = when {
            cleanDescription.length > MAX_DESCRIPTION_LENGTH -> "Keep the description within $MAX_DESCRIPTION_LENGTH characters."
            else -> null
        }
        return if (titleError == null && descriptionError == null) {
            JourneyMetadataValidation(metadata = JourneyMetadata(cleanTitle, cleanDescription))
        } else {
            JourneyMetadataValidation(titleError = titleError, descriptionError = descriptionError)
        }
    }
}
