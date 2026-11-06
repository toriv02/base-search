package search.ui

import search.indexlogic.DiskDocumentStore
import search.indexlogic.SearchableIndex
import search.query.QueryParser
import search.query.QueryEvaluator
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder

class SearchUI(
    private val index: SearchableIndex,
    private val store: DiskDocumentStore
) {
    private val queryField = JTextField("")
    private val searchButton = JButton("Найти")


    private val btnPrev = JButton("<< Назад")
    private val btnNext = JButton("Вперед >>")
    private val pageLabel = JLabel("Стр. 0 из 0")


    private val resultsPanel = JPanel()


    private var allIds: IntArray = IntArray(0)
    private var currentPage = 0
    private val pageSize = 20

    fun show() {
        val frame = JFrame("Поиск, всего ${index.documentCount} документов")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout(8, 8)

        val top = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
        queryField.preferredSize = Dimension(560, 28)
        top.add(JLabel("Запрос:"))
        top.add(queryField)
        top.add(searchButton)
        frame.add(top, BorderLayout.NORTH)

        resultsPanel.layout = BoxLayout(resultsPanel, BoxLayout.Y_AXIS)
        resultsPanel.background = Color.WHITE

        val scrollPane = JScrollPane(resultsPanel)
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        frame.add(scrollPane, BorderLayout.CENTER)


        val bottom = JPanel(BorderLayout())

        val paginationPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5))
        btnPrev.isEnabled = false
        btnNext.isEnabled = false
        paginationPanel.add(btnPrev)
        paginationPanel.add(pageLabel)
        paginationPanel.add(btnNext)

        bottom.add(paginationPanel, BorderLayout.CENTER)
        frame.add(bottom, BorderLayout.SOUTH)


        searchButton.addActionListener { runQuery() }
        queryField.addActionListener { runQuery() }

        btnPrev.addActionListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }

        btnNext.addActionListener {
            val totalPages = getTotalPages()
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage()
            }
        }

        frame.setSize(1000, 700)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun runQuery() {
        val raw = queryField.text.trim()
        if (raw.isEmpty()) {
            showMessage("нет термов")
            allIds = IntArray(0)
            updatePaginationUI()
            return
        }
        try {
            val ast = QueryParser(raw).parse()
            allIds = QueryEvaluator(index).eval(ast)
            currentPage = 0
            renderPage()
        } catch (e: Exception) {
            allIds = IntArray(0)
            updatePaginationUI()
            showMessage("Ошибка разбора запроса: ${e.message}\n")
        }
    }

    private fun getTotalPages(): Int {
        return if (allIds.isEmpty()) 1 else (allIds.size + pageSize - 1) / pageSize
    }

    private fun renderPage() {
        resultsPanel.removeAll()

        val totalPages = getTotalPages()
        val start = currentPage * pageSize
        val end = minOf(start + pageSize, allIds.size)

        if (allIds.isEmpty()) {
            val lbl = JLabel("Ничего не найдено.")
            lbl.border = EmptyBorder(20, 20, 20, 20)
            resultsPanel.add(lbl)
        } else {
            for (i in start until end) {
                val id = allIds[i]
                val d = store.get(id)
                if (d != null) {
                    resultsPanel.add(createResultCard(id, d))
                    resultsPanel.add(Box.createVerticalStrut(5))
                    resultsPanel.add(JSeparator())
                    resultsPanel.add(Box.createVerticalStrut(5))
                } else {
                    val errLbl = JLabel("#$id (ошибка чтения документа)")
                    errLbl.border = EmptyBorder(10, 10, 10, 10)
                    resultsPanel.add(errLbl)
                }
            }
        }

        resultsPanel.revalidate()
        resultsPanel.repaint()
        updatePaginationUI()
    }

    private fun updatePaginationUI() {
        val totalPages = getTotalPages()
        pageLabel.text = "Страница ${currentPage + 1} из $totalPages (Всего найдено: ${allIds.size})"

        btnPrev.isEnabled = currentPage > 0
        btnNext.isEnabled = currentPage < totalPages - 1


        SwingUtilities.invokeLater {
            val parent = resultsPanel.parent
            if (parent is JViewport) {
                parent.viewPosition = Point(0, 0)
            }
        }
    }

    private fun createResultCard(id: Int, d: DiskDocumentStore.Doc): JPanel {
        val card = JPanel()
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
        card.background = Color.WHITE
        card.border = EmptyBorder(10, 10, 10, 10)


        val safeTitle = "[${d.lang}] ${d.title.ifBlank { "(без названия)" }} (#$id)"
        val titleArea = JTextArea(safeTitle)
        titleArea.isEditable = false
        titleArea.lineWrap = true
        titleArea.wrapStyleWord = true
        titleArea.background = Color.WHITE
        titleArea.font = Font("SansSerif", Font.BOLD, 14)
        titleArea.border = null
        card.add(titleArea)


        if (d.externalId.isNotBlank()) {
            val url = "https://${d.lang}.wikipedia.org/?curid=${d.externalId}"
            val linkLabel = JLabel("<html><a href=''>$url</a></html>")
            linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            linkLabel.border = EmptyBorder(2, 0, 5, 0)
            linkLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (ex: Exception) {
                        JOptionPane.showMessageDialog(card, "Не удалось открыть ссылку: ${ex.message}")
                    }
                }
            })
            card.add(linkLabel)
        }


        val snippetArea = JTextArea(d.snippet)
        snippetArea.isEditable = false
        snippetArea.lineWrap = true
        snippetArea.wrapStyleWord = true
        snippetArea.background = Color.WHITE
        snippetArea.border = EmptyBorder(5, 0, 10, 0)
        snippetArea.font = Font("SansSerif", Font.PLAIN, 14)
        card.add(snippetArea)


        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        buttonPanel.background = Color.WHITE

        val readButton = JButton("Читать полностью")
        readButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        readButton.addActionListener {
            showFullTextWindow(d)
        }

        buttonPanel.add(readButton)
        card.add(buttonPanel)


        card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height + 15)

        return card
    }

    private fun showFullTextWindow(doc: DiskDocumentStore.Doc) {
        val dialog = JDialog()
        dialog.title = "[${doc.lang}] ${doc.title}"
        dialog.setSize(900, 700)
        dialog.setLocationRelativeTo(null)
        dialog.layout = BorderLayout()

        val textArea = JTextArea(doc.fullText)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.caretPosition = 0
        textArea.font = Font("SansSerif", Font.PLAIN, 15)
        textArea.margin = Insets(15, 15, 15, 15)

        val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        if (doc.externalId.isNotBlank()) {
            val url = "https://${doc.lang}.wikipedia.org/?curid=${doc.externalId}"
            val linkBtn = JButton("Открыть в браузере")
            linkBtn.addActionListener {
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (ex: Exception) {
                    JOptionPane.showMessageDialog(dialog, "Не удалось открыть: ${ex.message}")
                }
            }
            bottomPanel.add(linkBtn)
        }

        dialog.add(JScrollPane(textArea), BorderLayout.CENTER)
        dialog.add(bottomPanel, BorderLayout.SOUTH)
        dialog.isVisible = true
    }

    private fun showMessage(msg: String) {
        resultsPanel.removeAll()
        val lbl = JLabel("<html><body style='width: 600px;'>$msg</body></html>")
        lbl.border = EmptyBorder(20, 20, 20, 20)
        resultsPanel.add(lbl)
        resultsPanel.revalidate()
        resultsPanel.repaint()
    }
}