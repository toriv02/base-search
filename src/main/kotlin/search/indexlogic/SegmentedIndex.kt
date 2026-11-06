package search.indexlogic

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class SegmentedIndex(
    private val dir: File,
    private val segmentSize: Int = 250_000
) : SearchableIndex {

    //буфер для сборки индекса
    //ключ - терм, значение - постинг-лист
    private var buf: HashMap<String, IntArrayList>? = null
    private var bufDocCount = 0
    private var segId = 0
    private var nextDocId = 0

    private val segments = ArrayList<Segment>()

    override val documentCount: Int
        get() = if (segments.isNotEmpty()) segments.sumOf { it.docCount } else nextDocId

    fun openForBuild() {
        dir.mkdirs()
        File(dir, ".building").writeText("in progress")
        buf = HashMap(1 shl 20)
    }

    fun addDocument(text: String): Int {
        val id = nextDocId++
        val terms = Tokenizer.tokenize(text).toHashSet()
        val b = buf!!
        for (t in terms) b.getOrPut(t) { IntArrayList(4) }.add(id)
        bufDocCount++
        if (bufDocCount >= segmentSize) flushSegment()
        return id
    }

    fun finalizeBuild() {
        if (bufDocCount > 0) flushSegment()
        buf = null
        File(dir, ".building").delete()
    }
    //сбрасываем текущий буфер на диск как новый сегмент
    private fun flushSegment() {
        val b = buf ?: return
        val segDir = File(dir, "seg_$segId")
        if (segDir.exists()) segDir.deleteRecursively()
        segDir.mkdirs()

        //для использования бинарного поиска в словаре вычисляем хэши для всех термов и сортируя их
        data class Entry(val term: String, val h1: Long, val h2: Long)
        val entries = b.map { (term, _) -> Entry(term, Hash128.h1(term), Hash128.h2(term)) }
            .sortedBy { it.h1 }

        //записываем постинг-листы в postings.bin, предварительно выделив на id документа 4-байта
        val postingsFile = File(segDir, "postings.bin")
        val offsets = IntArray(entries.size)
        val counts = IntArray(entries.size)
        FileOutputStream(postingsFile).use { fos ->
            val ch = fos.channel
            val buf4 = ByteBuffer.allocateDirect(4 * 1024 * 1024).order(ByteOrder.BIG_ENDIAN)
            var byteOffset = 0L
            for ((i, e) in entries.withIndex()) {
                val list = b[e.term]!!
                val sorted = list.toSortedArray() //сортируем id по возрастанию
                offsets[i] = byteOffset.toInt() //запоминаем начало постинг-листа
                counts[i] = sorted.size //запоминаем длину постинг-листа
                for (id in sorted) {
                    if (!buf4.hasRemaining()) { buf4.flip(); ch.write(buf4); buf4.clear() }
                    buf4.putInt(id) //пишем id в буфер
                }
                byteOffset += sorted.size * 4L
            }
            buf4.flip()
            while (buf4.hasRemaining()) ch.write(buf4)
        }

        //записываем словарь с хэшами термов и указателями на их постинг-листы в postings.bin
        DataOutputStream(BufferedOutputStream(FileOutputStream(File(segDir, "dict.bin")))).use { out ->
            out.writeInt(entries.size)
            for ((i, e) in entries.withIndex()) {
                out.writeLong(e.h1)
                out.writeLong(e.h2)
                out.writeInt(offsets[i]) //смещение в файле postings.bin
                out.writeInt(counts[i]) //количество документов
            }
        }

        File(segDir, "meta.txt").writeText("$bufDocCount\n")

        println("  segment $segId: $bufDocCount docs, ${entries.size} terms")

        b.clear()
        bufDocCount = 0
        segId++
    }

    fun load() {
        segments.clear()
        var id = 0
        while (true) {
            val segDir = File(dir, "seg_$id")
            if (!segDir.isDirectory) break
            val docCount = File(segDir, "meta.txt").readLines()[0].trim().toInt()

            val dictFile = File(segDir, "dict.bin")
            val dictSize = dictFile.length() / 24L
            val n = (dictFile.length() / 24L).toInt()
            val h1 = LongArray(n)
            val h2 = LongArray(n)
            val offsets = IntArray(n)
            val counts = IntArray(n)
            DataInputStream(BufferedInputStream(FileInputStream(dictFile))).use { inp ->
                val declared = inp.readInt()
                if (declared != n) error("Bad dict size $declared vs $n")
                for (i in 0 until n) {
                    h1[i] = inp.readLong()
                    h2[i] = inp.readLong()
                    offsets[i] = inp.readInt()
                    counts[i] = inp.readInt()
                }
            }

            val channel = FileChannel.open(
                File(segDir, "postings.bin").toPath(),
                StandardOpenOption.READ
            )
            segments.add(Segment(id, docCount, h1, h2, offsets, counts, channel))
            println("  loaded segment $id: $docCount docs, $n terms")
            id++
        }
        nextDocId = segments.sumOf { it.docCount }
        println("Loaded ${segments.size} segments, $documentCount docs total")
    }

    //получаем постинг-лист по терму во время поиска
    override fun postingList(term: String): IntArray {
        val th1 = Hash128.h1(term)
        val th2 = Hash128.h2(term)
        val out = IntArrayList()

        //идем по всем сегментам и ищем терм в их словарях
        for (seg in segments) {
            val ref = seg.lookup(th1, th2) ?: continue
            //если нашли, читаем массив айдишек из postings.bin
            out.addAll(seg.readPostings(ref.offset, ref.count))
        }
        //возвращаем общий отсортированный список
        return out.toSortedArray()
    }

    override fun universe(): IntArray = IntArray(documentCount) { it }

    private class Segment(
        val id: Int,
        val docCount: Int,
        val h1: LongArray,
        val h2: LongArray,
        val offsets: IntArray,
        val counts: IntArray,
        private val channel: FileChannel
    ) {
        data class Ref(val offset: Int, val count: Int)

        fun lookup(termH1: Long, termH2: Long): Ref? {
            var lo = 0
            var hi = h1.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    h1[mid] < termH1 -> lo = mid + 1
                    h1[mid] > termH1 -> hi = mid - 1
                    h2[mid] == termH2 -> return Ref(offsets[mid], counts[mid])
                    h2[mid] < termH2 -> lo = mid + 1
                    else -> hi = mid - 1
                }
            }
            return null
        }

        fun readPostings(offset: Int, count: Int): IntArray {
            val buf = ByteBuffer.allocate(count * 4).order(ByteOrder.BIG_ENDIAN)
            var pos = offset.toLong()
            while (buf.hasRemaining()) {
                val n = channel.read(buf, pos)
                if (n < 0) error("EOF in segment $id postings")
                pos += n
            }
            buf.flip()
            return IntArray(count) { buf.int }
        }
    }
}