package search.query

class QueryParser(private val input: String) {

    private var pos = 0
    //запускаем рекурсивный спуск, начиная с самого низкого приоритета (OR)
    fun parse(): Query {
        skipWs()
        if (pos >= input.length) throw IllegalArgumentException("Empty query")
        val q = parseOr()
        skipWs()
        if (pos < input.length) {
            throw IllegalArgumentException(
                "Unexpected token at position $pos: '${input.substring(pos)}'"
            )
        }
        return q
    }
    //парсим выражения с оператором OR
    private fun parseOr(): Query {
        var left = parseAnd()
        while (true) {
            skipWs()
            val save = pos
            val tok = readWord()
            if (tok != null && tok.equals("OR", ignoreCase = true)) {
                left = Query.Or(left, parseAnd())
            } else {
                pos = save
                return left
            }
        }
    }
    //парсим выражения с оператором AND
    private fun parseAnd(): Query {
        var left = parseNot()
        while (true) {
            skipWs()
            val save = pos
            val tok = readWord()
            if (tok != null && tok.equals("AND", ignoreCase = true)) {
                left = Query.And(left, parseNot())
            } else {
                pos = save
                return left
            }
        }
    }
    //парсим выражения с оператором NOT
    private fun parseNot(): Query {
        skipWs()
        val save = pos
        val tok = readWord()
        if (tok != null && tok.equals("NOT", ignoreCase = true)) {
            return Query.Not(parseNot())
        }
        pos = save
        return parseAtom()
    }
    //парсим атомарный терм
    private fun parseAtom(): Query {
        skipWs()
        if (pos >= input.length) {
            throw IllegalArgumentException("Expected term or '(' at end of input")
        }
        if (input[pos] == '(') {
            pos++
            val q = parseOr()
            skipWs()
            if (pos >= input.length || input[pos] != ')') {
                throw IllegalArgumentException("Expected ')' at position $pos")
            }
            pos++
            return q
        }
        val word = readWord()
            ?: throw IllegalArgumentException("Expected term at position $pos")
        if (word.equals("AND", true) || word.equals("OR", true) || word.equals("NOT", true)) {
            throw IllegalArgumentException("Unexpected keyword '$word' at position $pos")
        }
        return Query.Term(word.lowercase())
    }

    private fun skipWs() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun readWord(): String? {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
        return if (pos > start) input.substring(start, pos) else null
    }
}