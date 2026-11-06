package search.indexlogic

class IntArrayList(initialCapacity: Int = 16) {
    private var data = IntArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) grow(size + 1)
        data[size++] = value
    }

    fun addAll(other: IntArray) {
        val need = size + other.size
        if (need > data.size) grow(need)
        System.arraycopy(other, 0, data, size, other.size)
        size += other.size
    }

    fun toSortedArray(): IntArray {
        val out = data.copyOf(size)
        out.sort()
        return out
    }

    private fun grow(minCapacity: Int) {
        val newCap = maxOf(minCapacity, data.size + (data.size shr 1) + 1)
        data = data.copyOf(newCap)
    }
}