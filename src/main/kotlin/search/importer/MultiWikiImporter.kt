package search.importer

import search.indexlogic.DiskDocumentStore
import search.indexlogic.SegmentedIndex
import java.io.File
import java.sql.DriverManager

object MultiWikiImporter {

    fun import(rootDir: File, index: SegmentedIndex, store: DiskDocumentStore) {
        val wikiDirs = if (rootDir.name.endsWith("wiki")) {
            listOf(rootDir)
        } else {
            listOf("ruwiki", "enwiki", "dewiki")
                .map { File(rootDir, it) }
                .filter { it.isDirectory }
        }

        require(wikiDirs.isNotEmpty()) {
            "Не найдено ни одной из директорий ruwiki/enwiki/dewiki в ${rootDir.absolutePath}"
        }

        Class.forName("org.duckdb.DuckDBDriver")

        for (wikiDir in wikiDirs) {
            val lang = wikiDir.name.removeSuffix("wiki")
            println("=== Импорт ${wikiDir.name} (lang=$lang) ===")
            importOne(wikiDir, lang, index, store)
        }
    }

    private fun importOne(
        dir: File,
        lang: String,
        index: SegmentedIndex,
        store: DiskDocumentStore
    ) {
        val pattern = File(dir, "*.parquet").absolutePath.replace("\\", "/")

        DriverManager.getConnection("jdbc:duckdb:").use { conn ->
            val sql = "SELECT id, title, text FROM read_parquet('$pattern')"
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    var n = 0
                    while (rs.next()) {
                        val extId = rs.getString(1) ?: ""
                        val title = rs.getString(2) ?: ""
                        val body  = rs.getString(3) ?: ""

                        val docId = index.addDocument("$title\n$body")
                        store.append(
                            docId,
                            DiskDocumentStore.Doc(
                                externalId = extId,
                                title      = title,
                                snippet    = body.take(300).replace('\n', ' ').replace('\r', ' '),
                                fullText   = body,
                                lang       = lang
                            )
                        )
                        n++
                        if (n % 10_000 == 0) {
                            println("  $n документов в ${dir.name} (глобальный docId=$docId)")
                        }
                    }
                    println("  Готово: $n документов в ${dir.name}")
                }
            }
        }
    }
}