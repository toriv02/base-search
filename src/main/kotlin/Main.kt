package search

import search.importer.MultiWikiImporter
import search.index.DiskDocumentStore
import search.index.SegmentedIndex
import search.ui.SearchUI
import java.io.File
import javax.swing.SwingUtilities

fun main() {
    val dataRoot = File("""C:\Users\v\Desktop\Поиск\data\finewiki_data\data""")
    val indexDir = File("index")

    if (File(indexDir, ".building").exists()) {
        println("Обнаружена незавершённая сборка, очищаю $indexDir")
        indexDir.deleteRecursively()
    }

    val ready = File(indexDir, "seg_0").isDirectory &&
            File(indexDir, "documents.bin").exists() &&
            File(indexDir, "documents.bin.idx").exists()

    val index = SegmentedIndex(indexDir, segmentSize = 250_000)
    val store = DiskDocumentStore(File(indexDir, "documents.bin"))

    if (ready) {
        println("Загружаю индекс из ${indexDir.absolutePath} ...")
        index.load()
        store.openForRead()
        println("Готово: ${index.documentCount} документов")
    } else {
        require(dataRoot.isDirectory) {
            "Нет директории с данными: ${dataRoot.absolutePath}"
        }
        println("Строю индекс из ${dataRoot.absolutePath} ...")
        index.openForBuild()
        store.openForWrite()
        MultiWikiImporter.import(dataRoot, index, store)
        index.finalizeBuild()
        store.closeForWrite()
        store.openForRead()
        println("Сборка завершена: ${index.documentCount} документов")
    }

    SwingUtilities.invokeLater { SearchUI(index, store).show() }
}