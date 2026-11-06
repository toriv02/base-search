package search.indexlogic

interface SearchableIndex {
    fun postingList(term: String): IntArray
    fun universe(): IntArray
    val documentCount: Int
}