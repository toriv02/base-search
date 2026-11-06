package search.query

sealed class Query {
    data class Term(val term: String) : Query()
    data class And(val left: Query, val right: Query) : Query()
    data class Or(val left: Query, val right: Query) : Query()
    data class Not(val expr: Query) : Query()
}