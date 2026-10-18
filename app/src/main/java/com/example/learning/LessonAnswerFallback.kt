package com.example.learning

private val lessonStopWords = setOf(
    "các", "cho", "của", "được", "trong", "trên", "dưới", "với", "một", "những", "này", "đó",
    "the", "and", "for", "from", "that", "this", "what", "when", "where", "which", "with", "are"
)

internal fun offlineLessonAnswer(question: String, lessonContent: String): String {
    val terms = Regex("[\\p{L}\\p{N}]{3,}").findAll(question.lowercase())
        .map { it.value }.filterNot { it in lessonStopWords }.toSet()
    val sentences = lessonContent.split(Regex("(?<=[.!?。！？])\\s+|\\n+"))
        .map(String::trim).filter(String::isNotBlank)
    val relevant = sentences.mapIndexedNotNull { index, sentence ->
        val words = Regex("[\\p{L}\\p{N}]{3,}").findAll(sentence.lowercase()).map { it.value }.toSet()
        val score = terms.intersect(words).size
        if (score == 0) null else Triple(index, score, sentence)
    }.sortedWith(compareByDescending<Triple<Int, Int, String>> { it.second }.thenBy { it.first })
        .take(3).sortedBy { it.first }.map { it.third.take(500) }

    return if (relevant.isEmpty()) {
        "Mình chưa tìm thấy thông tin này trong nội dung bài học hiện có. Hãy thử hỏi về một khái niệm được nêu trong bài."
    } else {
        "Theo nội dung bài học:\n${relevant.joinToString(" ")}"
    }
}
