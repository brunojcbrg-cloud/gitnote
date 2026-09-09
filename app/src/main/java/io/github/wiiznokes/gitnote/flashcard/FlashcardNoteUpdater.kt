package io.github.wiiznokes.gitnote.flashcard

object FlashcardNoteUpdater {
    fun update(
        content: String,
        card: ParsedFlashcard,
        schedule: FlashcardSchedule,
    ): String {
        require(card.sourceRange.first >= 0 && card.sourceRange.last < content.length)
        val comment = schedule.asComment()
        val scheduleRange = card.scheduleLineRange
        if (scheduleRange != null) {
            require(scheduleRange.first >= 0 && scheduleRange.last < content.length)
            return content.replaceRange(scheduleRange.first, scheduleRange.last + 1, comment)
        }

        require(card.insertionOffset in 0..content.length)
        val insertion = if (card.insertionOffset > 0 &&
            (content[card.insertionOffset - 1] == '\r' || content[card.insertionOffset - 1] == '\n')
        ) {
            comment + card.insertionLineEnding
        } else {
            card.insertionLineEnding + comment
        }
        return content.substring(0, card.insertionOffset) + insertion +
            content.substring(card.insertionOffset)
    }

    fun checkUnchanged(
        expectedContent: String,
        diskContent: String,
    ): Result<Unit> {
        if (diskContent != expectedContent) {
            return Result.failure(ConcurrentNoteChangeException())
        }
        return Result.success(Unit)
    }
}

class ConcurrentNoteChangeException : IllegalStateException()
