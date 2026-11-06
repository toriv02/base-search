package search.query

import search.index.SearchableIndex

class QueryEvaluator(private val index: SearchableIndex) {

    //возвращаем отсортированный массив id документов, подходящих под запрос
    fun eval(query: Query): IntArray = when (query) {
        //для терма запрашиваем постинг-лист из индекса
        is Query.Term -> index.postingList(query.term)
        //для AND пересекаем результаты левого и правого поддеревьев
        is Query.And  -> intersect(eval(query.left), eval(query.right))
        //для OR объединяем результаты
        is Query.Or   -> union(eval(query.left), eval(query.right))
        //для NOT вычитаем из множества всех документов результат поддерева
        is Query.Not  -> difference(index.universe(), eval(query.expr))
    }
    //Пересечение двух отсортированных массивов
    private fun intersect(a: IntArray, b: IntArray): IntArray {
        if (a.isEmpty() || b.isEmpty()) return EMPTY
        //если один массив сильно больше другого, используем бинарный поиск
        val (small, large) = if (a.size <= b.size) a to b else b to a
        if (large.size > small.size * 8) {
            val out = ArrayList<Int>(small.size)
            for (x in small) if (binarySearch(large, x) >= 0) out.add(x)
            return out.toIntArray()
        }
        //пересечение двух указателей для двух отсортированных массивов
        val out = ArrayList<Int>(small.size)
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out.add(a[i]); i++; j++ }
                a[i] <  b[j] -> i++
                else         -> j++
            }
        }
        return out.toIntArray()
    }

    private fun binarySearch(arr: IntArray, x: Int): Int {
        var lo = 0; var hi = arr.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                arr[mid] < x -> lo = mid + 1
                arr[mid] > x -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun union(a: IntArray, b: IntArray): IntArray {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = ArrayList<Int>(a.size + b.size)
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out.add(a[i]); i++; j++ }
                a[i] <  b[j] -> { out.add(a[i]); i++ }
                else         -> { out.add(b[j]); j++ }
            }
        }
        while (i < a.size) out.add(a[i++])
        while (j < b.size) out.add(b[j++])
        return out.toIntArray()
    }

    private fun difference(a: IntArray, b: IntArray): IntArray {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return EMPTY
        val out = ArrayList<Int>(a.size)
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { i++; j++ }
                a[i] <  b[j] -> { out.add(a[i]); i++ }
                else         -> j++
            }
        }
        while (i < a.size) out.add(a[i++])
        return out.toIntArray()
    }

    companion object { private val EMPTY = IntArray(0) }
}