package search.indexlogic

import java.io.*

class DiskDocumentStore(private val file: File) {

    data class Doc(
        val externalId: String,
        val title: String,
        val snippet: String,
        val fullText: String,
        val lang: String
    )

    private var writeOut: DataOutputStream? = null
    private var bytesWritten = 0L
    private var offsets: LongArray = LongArray(1024)
    private var count = 0

    private var raf: RandomAccessFile? = null

    fun openForWrite() {
        file.parentFile.mkdirs()
        writeOut = DataOutputStream(BufferedOutputStream(FileOutputStream(file)))
        bytesWritten = 0L
        count = 0
    }

    fun append(internalId: Int, doc: Doc) {
        require(internalId == count) { "Expected docId=$count, got $internalId" }
        if (count >= offsets.size) offsets = offsets.copyOf(offsets.size * 2)
        offsets[count] = bytesWritten
        val o = writeOut!!
        bytesWritten += writeStr(o, doc.externalId)
        bytesWritten += writeStr(o, doc.title)
        bytesWritten += writeStr(o, doc.snippet)
        bytesWritten += writeStr(o, doc.fullText)
        bytesWritten += writeStr(o, doc.lang)
        count++
    }

    fun closeForWrite() {
        writeOut!!.flush()
        writeOut!!.close()
        writeOut = null
        DataOutputStream(BufferedOutputStream(FileOutputStream(
            File(file.parentFile, file.name + ".idx")
        ))).use { idx ->
            idx.writeInt(count)
            for (i in 0 until count) idx.writeLong(offsets[i])
        }
        offsets = LongArray(0)
    }

    private fun writeStr(out: DataOutputStream, s: String): Int {
        val b = s.toByteArray(Charsets.UTF_8)
        out.writeInt(b.size)
        out.write(b)
        return 4 + b.size
    }

    fun openForRead() {
        val idxFile = File(file.parentFile, file.name + ".idx")
        DataInputStream(BufferedInputStream(FileInputStream(idxFile))).use { inp ->
            count = inp.readInt()
            offsets = LongArray(count)
            for (i in 0 until count) offsets[i] = inp.readLong()
        }
        raf = RandomAccessFile(file, "r")
    }

    fun size(): Int = count

    fun get(id: Int): Doc? {
        if (id < 0 || id >= count) return null
        val r = raf ?: return null
        synchronized(r) {
            r.seek(offsets[id])
            val ext = readStr(r)
            val title = readStr(r)
            val snip = readStr(r)
            val full = readStr(r)
            val lang = readStr(r)
            return Doc(ext, title, snip, full, lang)
        }
    }

    private fun readStr(r: RandomAccessFile): String {
        val len = r.readInt()
        val b = ByteArray(len)
        r.readFully(b)
        return String(b, Charsets.UTF_8)
    }
}
