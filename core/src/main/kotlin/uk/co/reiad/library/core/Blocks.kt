package uk.co.reiad.library.core

/* ============================================================
   A lesson or article body, turned into blocks a native screen
   can draw.

   The site stores prose as HTML in a database, sanitised on write
   by an allowlist of about two dozen tags and twenty-one classes.
   A closed grammar is what makes a native renderer finite work
   rather than a WebView.

   **The allowlist is a floor, not a promise, and that is
   measured.** `/money/basics-1/share` carries a `<b>` in its
   stored body, which the server's tag list does not have: the
   browser's own sanitiser renames `B` to `STRONG` on the way in,
   and some prose predates that or arrived another way. It renders
   correctly on the web because a browser is forgiving. Nothing is
   forgiving here, so this file is:

     - a synonym table, the same one the editor applies, so `b`
       reads as strong and `h4` as a small heading;
     - and a rule that anything still unknown keeps its TEXT and
       loses only its tag, recorded in `unknown` so a fixture
       refresh turns silence into a failing test.

   Dropping the text instead would have eaten a word's emphasis on
   day one and said nothing, which is the failure the site's own
   rules keep returning to.
   ============================================================ */

/* ---------- what a body is made of ---------- */

sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Strong(val children: List<Inline>) : Inline
    data class Emphasis(val children: List<Inline>) : Inline
    data class Code(val children: List<Inline>) : Inline
    data class Sup(val children: List<Inline>) : Inline
    data class Sub(val children: List<Inline>) : Inline
    data object Break : Inline

    /** `isTerm` is the glossary link: `<a class="term">`, which
        the money school opens in place rather than navigating to.
        `href` is kept exactly as written, because a term link
        inside a lesson is RELATIVE (`dividend.html`) and only the
        stage's base can resolve it. */
    data class Link(
        val href: String,
        val children: List<Inline>,
        val isTerm: Boolean = false,
    ) : Inline
}

/** The kind of a bordered aside. Each is a class in the article
    allowlist and a rule in the site's stylesheet. */
enum class CalloutKind { AT_A_GLANCE, SIDE_NOTE, NOTE, EXAMPLE }

sealed interface Block {
    data class Heading(val level: Int, val inlines: List<Inline>) : Block
    data class Paragraph(val inlines: List<Inline>) : Block
    data class Bullets(val items: List<List<Inline>>) : Block
    data class Numbers(val items: List<List<Inline>>) : Block

    /** A numbered walk-through: `.step-list`. Visually a counter
        in a disc, not a plain ordered list. */
    data class Steps(val items: List<List<Inline>>) : Block

    /** `.checklist`. Inside a school lesson these become the
        tickable checkpoints, filed under `<school>-checks`, and
        `index` is the id's second half. */
    data class Checklist(val items: List<List<Inline>>) : Block

    data class Quote(val inlines: List<Inline>) : Block
    data object Rule : Block

    data class Callout(
        val kind: CalloutKind,
        val label: List<Inline>,
        val body: List<Block>,
    ) : Block

    /** `.figures`, a row of key numbers. Each figure is a value
        and its caption. */
    data class KeyFigures(val figures: List<Figure>) : Block

    data class Photo(
        val src: String,
        val alt: String,
        val caption: List<Inline>,
        val classes: Set<String>,
    ) : Block

    data class Table(
        val head: List<List<Inline>>,
        val rows: List<List<List<Inline>>>,
        val scrolls: Boolean,
    ) : Block

    /** A shape the parser did not know. Its text survives; only
        the tag is lost. */
    data class Unknown(val tag: String, val inlines: List<Inline>) : Block
}

data class Figure(val value: List<Inline>, val caption: List<Inline>)

/** The parsed body, plus what it did not recognise. */
data class Body(val blocks: List<Block>, val unknown: List<String> = emptyList())

/* ---------- the parse ---------- */

object BodyParser {

    /** Tags the editor rewrites on the way in. Applied here too,
        because stored prose predates it. */
    private val SYNONYMS = mapOf(
        "b" to "strong",
        "i" to "em",
        "u" to "em",
        "mark" to "em",
        "h1" to "h2",
        "h4" to "h3",
        "h5" to "h3",
        "h6" to "h3",
        "section" to "div",
        "article" to "div",
        "span" to "inline-plain",
        "font" to "inline-plain",
    )

    private val VOID = setOf("br", "hr", "img")

    fun parse(html: String): Body {
        val unknown = mutableListOf<String>()
        val nodes = Tokeniser(html, unknown).parse()
        val blocks = nodes.flatMap { blocksOf(it, unknown) }
        return Body(blocks, unknown.distinct())
    }

    /* ---------- node to block ---------- */

    private fun blocksOf(node: Node, unknown: MutableList<String>): List<Block> = when (node) {
        is Node.Text ->
            if (node.text.isBlank()) emptyList()
            else listOf(Block.Paragraph(listOf(Inline.Text(node.text.trim()))))

        is Node.Element -> elementBlocks(node, unknown)
    }

    private fun elementBlocks(el: Node.Element, unknown: MutableList<String>): List<Block> {
        val classes = el.classes
        return when (el.tag) {
            "p" -> listOf(Block.Paragraph(inlinesOf(el.children, unknown)))
            "h2" -> listOf(Block.Heading(2, inlinesOf(el.children, unknown)))
            "h3" -> listOf(Block.Heading(3, inlinesOf(el.children, unknown)))
            "hr" -> listOf(Block.Rule)
            "blockquote" -> listOf(Block.Quote(inlinesOf(el.children, unknown)))

            "ul" -> when {
                "checklist" in classes -> listOf(Block.Checklist(itemsOf(el, unknown)))
                "step-list" in classes -> listOf(Block.Steps(itemsOf(el, unknown)))
                "figures" in classes -> listOf(Block.KeyFigures(figuresOf(el, unknown)))
                else -> listOf(Block.Bullets(itemsOf(el, unknown)))
            }

            "ol" -> if ("step-list" in classes) listOf(Block.Steps(itemsOf(el, unknown)))
            else listOf(Block.Numbers(itemsOf(el, unknown)))

            "figure" -> listOf(photoOf(el, unknown))
            "table" -> listOf(tableOf(el, unknown, scrolls = false))

            "div" -> divBlocks(el, classes, unknown)

            /* A tag that only ever holds inline content, met where
               a block was expected. Its text is the point. */
            "strong", "em", "code", "a", "sup", "sub", "inline-plain" ->
                listOf(Block.Paragraph(inlinesOf(listOf(el), unknown)))

            else -> {
                unknown += el.tag
                listOf(Block.Unknown(el.tag, inlinesOf(el.children, unknown)))
            }
        }
    }

    private fun divBlocks(
        el: Node.Element,
        classes: Set<String>,
        unknown: MutableList<String>,
    ): List<Block> {
        val kind = when {
            "at-a-glance" in classes -> CalloutKind.AT_A_GLANCE
            "side-note" in classes -> CalloutKind.SIDE_NOTE
            "note" in classes -> CalloutKind.NOTE
            "ex" in classes -> CalloutKind.EXAMPLE
            else -> null
        }
        if (kind != null) {
            val labelClass = if (kind == CalloutKind.AT_A_GLANCE) "at-a-glance-label" else "side-note-label"
            val label = el.children
                .filterIsInstance<Node.Element>()
                .firstOrNull { labelClass in it.classes }
            val rest = el.children.filter { it !== label }
            return listOf(
                Block.Callout(
                    kind = kind,
                    label = label?.let { inlinesOf(it.children, unknown) } ?: emptyList(),
                    body = rest.flatMap { blocksOf(it, unknown) },
                )
            )
        }
        if ("table-scroll" in classes) {
            val table = el.children.filterIsInstance<Node.Element>().firstOrNull { it.tag == "table" }
            if (table != null) return listOf(tableOf(table, unknown, scrolls = true))
        }
        /* A div carrying no class this renderer knows is a
           wrapper, so its children are the blocks. */
        return el.children.flatMap { blocksOf(it, unknown) }
    }

    private fun itemsOf(el: Node.Element, unknown: MutableList<String>): List<List<Inline>> =
        el.children.filterIsInstance<Node.Element>()
            .filter { it.tag == "li" }
            .map { inlinesOf(it.children, unknown) }

    private fun figuresOf(el: Node.Element, unknown: MutableList<String>): List<Figure> =
        el.children.filterIsInstance<Node.Element>()
            .filter { it.tag == "li" }
            .map { li ->
                val strong = li.children.filterIsInstance<Node.Element>()
                    .firstOrNull { it.tag == "strong" }
                val rest = li.children.filter { it !== strong }
                Figure(
                    value = strong?.let { inlinesOf(it.children, unknown) } ?: emptyList(),
                    caption = inlinesOf(rest, unknown),
                )
            }

    private fun photoOf(el: Node.Element, unknown: MutableList<String>): Block.Photo {
        val img = el.children.filterIsInstance<Node.Element>().firstOrNull { it.tag == "img" }
        val caption = el.children.filterIsInstance<Node.Element>()
            .firstOrNull { it.tag == "figcaption" }
        return Block.Photo(
            src = img?.attrs?.get("src").orEmpty(),
            alt = img?.attrs?.get("alt").orEmpty(),
            caption = caption?.let { inlinesOf(it.children, unknown) } ?: emptyList(),
            classes = el.classes,
        )
    }

    private fun tableOf(
        el: Node.Element,
        unknown: MutableList<String>,
        scrolls: Boolean,
    ): Block.Table {
        val rows = mutableListOf<Node.Element>()
        fun collect(node: Node.Element) {
            for (child in node.children.filterIsInstance<Node.Element>()) {
                when (child.tag) {
                    "tr" -> rows += child
                    "thead", "tbody", "tfoot" -> collect(child)
                    else -> Unit
                }
            }
        }
        collect(el)

        val cellsOf = { row: Node.Element ->
            row.children.filterIsInstance<Node.Element>()
                .filter { it.tag == "th" || it.tag == "td" }
                .map { inlinesOf(it.children, unknown) }
        }
        val headRow = rows.firstOrNull { row ->
            row.children.filterIsInstance<Node.Element>().any { it.tag == "th" }
        }
        val body = rows.filter { it !== headRow }
        return Block.Table(
            head = headRow?.let(cellsOf) ?: emptyList(),
            rows = body.map(cellsOf),
            scrolls = scrolls,
        )
    }

    /* ---------- node to inline ---------- */

    private fun inlinesOf(nodes: List<Node>, unknown: MutableList<String>): List<Inline> =
        nodes.flatMap { node ->
            when (node) {
                is Node.Text ->
                    if (node.text.isEmpty()) emptyList() else listOf(Inline.Text(node.text))

                is Node.Element -> when (node.tag) {
                    "strong" -> listOf(Inline.Strong(inlinesOf(node.children, unknown)))
                    "em" -> listOf(Inline.Emphasis(inlinesOf(node.children, unknown)))
                    "code" -> listOf(Inline.Code(inlinesOf(node.children, unknown)))
                    "sup" -> listOf(Inline.Sup(inlinesOf(node.children, unknown)))
                    "sub" -> listOf(Inline.Sub(inlinesOf(node.children, unknown)))
                    "br" -> listOf(Inline.Break)
                    "a" -> listOf(
                        Inline.Link(
                            href = node.attrs["href"].orEmpty(),
                            children = inlinesOf(node.children, unknown),
                            isTerm = "term" in node.classes,
                        )
                    )
                    /* A wrapper with nothing to say inline: keep
                       the words, drop the box. */
                    "inline-plain", "p", "div", "li" -> inlinesOf(node.children, unknown)
                    else -> {
                        unknown += node.tag
                        inlinesOf(node.children, unknown)
                    }
                }
            }
        }.let(::collapse)

    /** Adjacent text runs become one, so a `<span>` that
        dissolved does not leave two halves of a word apart. */
    private fun collapse(inlines: List<Inline>): List<Inline> {
        val out = mutableListOf<Inline>()
        for (item in inlines) {
            val last = out.lastOrNull()
            if (item is Inline.Text && last is Inline.Text) {
                out[out.size - 1] = Inline.Text(last.text + item.text)
            } else {
                out += item
            }
        }
        return out
    }

    /* ---------- the tokeniser ---------- */

    private sealed interface Node {
        data class Text(val text: String) : Node
        data class Element(
            val tag: String,
            val attrs: Map<String, String>,
            val children: MutableList<Node> = mutableListOf(),
        ) : Node {
            val classes: Set<String>
                get() = attrs["class"].orEmpty().split(' ').filter { it.isNotBlank() }.toSet()
        }
    }

    /** Small and deliberately tolerant. The input is already
        allowlist-sanitised by the Worker, so this does not have to
        defend against hostile markup; it has to survive prose that
        predates the sanitiser. */
    private class Tokeniser(private val html: String, private val unknown: MutableList<String>) {

        private var i = 0

        fun parse(): List<Node> {
            val root = Node.Element("root", emptyMap())
            val stack = ArrayDeque<Node.Element>()
            stack.addLast(root)

            while (i < html.length) {
                val lt = html.indexOf('<', i)
                if (lt < 0) {
                    addText(stack.last(), html.substring(i))
                    break
                }
                if (lt > i) addText(stack.last(), html.substring(i, lt))

                val gt = html.indexOf('>', lt)
                if (gt < 0) {
                    addText(stack.last(), html.substring(lt))
                    break
                }

                val raw = html.substring(lt + 1, gt).trim()
                i = gt + 1

                when {
                    raw.startsWith("!") -> Unit /* a comment or doctype: gone */

                    raw.startsWith("/") -> {
                        val tag = normalise(raw.drop(1).trim().lowercase())
                        /* Close the nearest matching open tag.
                           A stray close is dropped rather than
                           unwinding the whole document. */
                        val at = stack.indexOfLast { it.tag == tag }
                        if (at > 0) while (stack.size > at) stack.removeLast()
                    }

                    else -> {
                        val selfClosing = raw.endsWith("/")
                        val inner = raw.trimEnd('/').trim()
                        val name = normalise(inner.takeWhile { !it.isWhitespace() }.lowercase())
                        val element = Node.Element(name, attrsOf(inner))
                        stack.last().children += element
                        if (!selfClosing && name !in VOID) stack.addLast(element)
                    }
                }
            }
            return root.children
        }

        private fun normalise(tag: String): String = SYNONYMS[tag] ?: tag

        private fun addText(parent: Node.Element, text: String) {
            val decoded = unescape(text)
            if (decoded.isNotEmpty()) parent.children += Node.Text(decoded)
        }

        private fun attrsOf(inner: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            val rest = inner.dropWhile { !it.isWhitespace() }
            val pattern = Regex("""([a-zA-Z-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
            for (match in pattern.findAll(rest)) {
                val key = match.groupValues[1].lowercase()
                val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
                out[key] = unescape(value)
            }
            return out
        }
    }

    /** The five named entities the sanitiser can emit, plus
        numeric ones, which Bangla prose uses for nothing but which
        an import can leave behind. */
    internal fun unescape(text: String): String {
        if ('&' !in text) return text
        return Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(text) { match ->
            when (val body = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos", "#39" -> "'"
                "nbsp" -> " "
                else -> when {
                    body.startsWith("#x") || body.startsWith("#X") ->
                        body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                            ?: match.value
                    body.startsWith("#") ->
                        body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                            ?: match.value
                    else -> match.value
                }
            }
        }
    }
}

/* ---------- reading the words back out ---------- */

/** Every word in a run of inlines, for search, for read-aloud,
    and for the tests. */
fun List<Inline>.text(): String = joinToString("") { inline ->
    when (inline) {
        is Inline.Text -> inline.text
        is Inline.Strong -> inline.children.text()
        is Inline.Emphasis -> inline.children.text()
        is Inline.Code -> inline.children.text()
        is Inline.Sup -> inline.children.text()
        is Inline.Sub -> inline.children.text()
        is Inline.Link -> inline.children.text()
        Inline.Break -> "\n"
    }
}

/* ============================================================
   Where a checkpoint's number comes from.

   A checklist inside a school lesson becomes tickable
   checkpoints, filed `<lesson id>#<n>`. The site numbers them
   with one `querySelectorAll(".checklist > li")` over the whole
   article, so `n` runs across EVERY checklist item in the lesson
   in document order, not from zero inside each list.

   That distinction is a storage identity rather than a detail. A
   lesson with two checklists of three items files them 0 to 5;
   numbering each list from zero would file them 0,1,2,0,1,2 and
   every existing tick in every real account would point at the
   wrong line. This was written the wrong way first.

   Position rather than text, and that is the site's reason too:
   prose gets edited, and a checkpoint that forgot itself over a
   fixed typo is worse than one that stays put when a line is
   reworded.

   Returned as a map from a block's PATH to the number its first
   item takes, because a checklist can be nested inside a callout
   and a renderer needs to find its own base without recounting.
   ============================================================ */

/** The number each checklist's first item takes, by path.

    A path is the block's position, dotted for nesting: `"3"` is
    the fourth top-level block, `"3.1"` the second block inside
    it. */
fun checkpointBases(blocks: List<Block>): Map<String, Int> {
    val bases = mutableMapOf<String, Int>()
    var next = 0

    fun walk(list: List<Block>, prefix: String) {
        for ((index, block) in list.withIndex()) {
            val path = if (prefix.isEmpty()) "$index" else "$prefix.$index"
            when (block) {
                is Block.Checklist -> {
                    bases[path] = next
                    next += block.items.size
                }
                /* A callout holds blocks, and a checklist inside
                   one is a descendant of the article like any
                   other: the site's selector finds it and counts
                   it in place. */
                is Block.Callout -> walk(block.body, path)
                else -> Unit
            }
        }
    }

    walk(blocks, "")
    return bases
}

/** How many checkpoints a lesson has at all, which is what a
    "3 of 5 done" line counts against. */
fun checkpointCount(blocks: List<Block>): Int {
    var total = 0
    fun walk(list: List<Block>) {
        for (block in list) when (block) {
            is Block.Checklist -> total += block.items.size
            is Block.Callout -> walk(block.body)
            else -> Unit
        }
    }
    walk(blocks)
    return total
}
