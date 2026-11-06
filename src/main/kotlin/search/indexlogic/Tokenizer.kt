package search.indexlogic

import java.util.Locale

object Tokenizer {
    private val WORD_REGEX = Regex("[\\p{L}\\p{N}]+")

    fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return WORD_REGEX.findAll(text.lowercase(Locale.ROOT))
            .map { it.value }
            .toList()
    }
}