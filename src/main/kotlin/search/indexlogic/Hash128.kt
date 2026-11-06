package search.indexlogic

import kotlin.text.iterator

object Hash128 {
    fun h1(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
        return h
    }

    fun h2(s: String): Long {
        var h = 5381L
        for (c in s) h = ((h shl 5) + h) + c.code.toLong()
        return h * 0x9E3779B97F4A7C15uL.toLong()
    }
}