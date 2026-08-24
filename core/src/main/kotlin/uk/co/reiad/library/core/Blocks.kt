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

/** The kind of a bordered aside. The first four are classes in
    the article allowlist and rules in the site's stylesheet. The
    last two are the schools' remember rail (`.merke`, `.mone`),
    drawn against an accent rail, with `.warn` swapping the rail
    to the danger colour. */
enum class CalloutKind { AT_A_GLANCE, SIDE_NOTE, NOTE, EXAMPLE, REMEMBER, CAUTION }

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

    /** The pattern box: German's `.muster`, English's `.shape`.
        Who is talking, the pattern itself at display size, and
        the sentences under it. Its own block rather than a callout,
        because the SHAPE line is the lesson's whole point and a
        renderer has to be able to set it larger than the prose
        around it. */
    data class Pattern(
        val label: List<Inline>,
        val shape: List<Inline>,
        val body: List<Block>,
    ) : Block

    /** A pair list: `.satz-list`, `.shobdo-list`, `.line-list`,
        `.word-grid` and their kin. The lead is
        the sentence in the language being learnt and the gloss is
        its Bangla meaning, and keeping them as two fields is what
        stops them arriving as one word: `<span>` dissolves, so
        "Ich esse Reis." and "আমি ভাত খাই।" met with nothing
        between them, which is how the first German build drew
        every example on the site. */
    data class Sentences(val rows: List<SentenceRow>) : Block

    /** A shape the parser did not know. Its text survives; only
        the tag is lost. */
    data class Unknown(val tag: String, val inlines: List<Inline>) : Block
}

data class Figure(val value: List<Inline>, val caption: List<Inline>)

data class SentenceRow(val lead: List<Inline>, val gloss: List<Inline>)

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

    /** The whitespace CSS collapses: space, tab and both line
        enders. NOT `\s`, which would take ` ` with it, and an
        author who wrote `&nbsp;` meant precisely a space that
        does not collapse. */
    private val WHITESPACE = Regex("[ \t\n\r]+")

    fun parse(html: String): Body {
        val unknown = mutableListOf<String>()
        val nodes = Tokeniser(html, unknown).parse()
        val blocks = blocksOfChildren(nodes, unknown)
        return Body(blocks, unknown.distinct())
    }

    /* ---------- node to block ---------- */

    private fun blocksOf(node: Node, unknown: MutableList<String>): List<Block> = when (node) {
        is Node.Text ->
            if (node.text.isBlank()) emptyList()
            else listOf(Block.Paragraph(listOf(Inline.Text(node.text.trim()))))

        is Node.Element -> elementBlocks(node, unknown)
    }

    /** A wrapper's children, as blocks, with one rule the naive
        walk gets wrong: a RUN of inline children is one paragraph.

        `<div>খেয়াল করো: <span lang="de">sie</span> = সে</div>` is
        one sentence, and mapping each child to its own block dealt
        it out as three paragraphs, each on its own line. So text
        and inline tags gather until a real block arrives, and the
        gathered run becomes a single paragraph. */
    private fun blocksOfChildren(nodes: List<Node>, unknown: MutableList<String>): List<Block> {
        val out = mutableListOf<Block>()
        val run = mutableListOf<Node>()
        fun flush() {
            if (run.isEmpty()) return
            val inlines = prose(run.toList(), unknown)
            if (inlines.isNotEmpty()) out += Block.Paragraph(inlines)
            run.clear()
        }
        for (node in nodes) {
            val inline = node is Node.Text || (node is Node.Element && node.tag in INLINE)
            if (inline) run += node else { flush(); out += blocksOf(node, unknown) }
        }
        flush()
        return out
    }

    /** The tags that live inside a line rather than owning one. */
    private val INLINE = setOf("strong", "em", "code", "a", "sup", "sub", "br", "inline-plain")

    private fun elementBlocks(el: Node.Element, unknown: MutableList<String>): List<Block> {
        val classes = el.classes
        return when (el.tag) {
            "p" -> listOf(Block.Paragraph(prose(el.children, unknown)))
            "h2" -> listOf(Block.Heading(2, prose(el.children, unknown)))
            "h3" -> listOf(Block.Heading(3, prose(el.children, unknown)))
            "hr" -> listOf(Block.Rule)
            "blockquote" -> listOf(Block.Quote(prose(el.children, unknown)))

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
                listOf(Block.Paragraph(prose(listOf(el), unknown)))

            else -> {
                unknown += el.tag
                listOf(Block.Unknown(el.tag, prose(el.children, unknown)))
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
                    label = label?.let { prose(it.children, unknown) } ?: emptyList(),
                    body = blocksOfChildren(rest, unknown),
                )
            )
        }
        if ("table-scroll" in classes) {
            val table = el.children.filterIsInstance<Node.Element>().firstOrNull { it.tag == "table" }
            if (table != null) return listOf(tableOf(table, unknown, scrolls = true))
        }

        /* ---- the schools' own furniture, by SHAPE ----

           Not in the article allowlist, because a school lesson is
           sanitised against its school's own list, and every
           school has furniture of its own: the German muster, satz
           and merke; the Qur'an school's shobdo pairs, tothyo and
           mukhe; the English shape box, line pairs, word grid,
           mone and bolo. The first fix named the German three and
           the very next screenshot was the Arabic school glued the
           same way, which is the lesson: a list of class names is
           wrong the day a fifth school is written.

           So the rules below read the MARKUP'S SHAPE, not the
           class list, wherever a shape is distinctive enough to
           read: a child whose class ends in -label makes a
           labelled aside, and a wrapper of b-plus-span rows makes
           sentence pairs, whatever the wrapper is called. Only the
           pattern box and the remember rail are named, because
           "muster or shape" and "merke or mone" is what they are
           called and nothing about their outline says which child
           is the pattern. */

        /* The pattern box: German's .muster, English's .shape. */
        if ("muster" in classes || "shape" in classes) {
            val label = el.children.filterIsInstance<Node.Element>()
                .firstOrNull { c -> c.classes.any { it.endsWith("-label") } }
            val shape = el.children.filterIsInstance<Node.Element>()
                .firstOrNull { "muster-shape" in it.classes || "shape-line" in it.classes }
            val rest = el.children.filter { it !== label && it !== shape }
            return listOf(
                Block.Pattern(
                    label = label?.let { prose(it.children, unknown) } ?: emptyList(),
                    shape = shape?.let { prose(it.children, unknown) } ?: emptyList(),
                    body = blocksOfChildren(rest, unknown),
                )
            )
        }

        /* The remember rail: German's .merke, English's .mone. */
        if ("merke" in classes || "mone" in classes) {
            return listOf(
                Block.Callout(
                    kind = if ("warn" in classes) CalloutKind.CAUTION else CalloutKind.REMEMBER,
                    label = emptyList(),
                    body = blocksOfChildren(el.children, unknown),
                )
            )
        }

        /* Any box that names itself: a child whose class ends in
           -label is the label and the rest is the body. This one
           rule is tothyo, mukhe and bolo today and every box a
           school invents tomorrow. */
        val labelled = el.children.filterIsInstance<Node.Element>()
            .firstOrNull { c -> c.classes.any { it.endsWith("-label") } }
        if (labelled != null) {
            return listOf(
                Block.Callout(
                    kind = CalloutKind.NOTE,
                    label = prose(labelled.children, unknown),
                    body = blocksOfChildren(el.children.filter { it !== labelled }, unknown),
                )
            )
        }

        /* A wrapper of pair rows: .satz-list, .shobdo-list,
           .line-list, .word-grid, .shobdo-gitter, and whatever a
           fifth school calls its own. Read from the rows'
           shape. */
        pairsOf(el, unknown)?.let { return listOf(Block.Sentences(it)) }

        /* A div carrying no class this renderer knows is a
           wrapper, so its children are the blocks. */
        return blocksOfChildren(el.children, unknown)
    }

    private fun itemsOf(el: Node.Element, unknown: MutableList<String>): List<List<Inline>> =
        el.children.filterIsInstance<Node.Element>()
            .filter { it.tag == "li" }
            .map { prose(it.children, unknown) }

    private fun figuresOf(el: Node.Element, unknown: MutableList<String>): List<Figure> =
        el.children.filterIsInstance<Node.Element>()
            .filter { it.tag == "li" }
            .map { li ->
                val strong = li.children.filterIsInstance<Node.Element>()
                    .firstOrNull { it.tag == "strong" }
                val rest = li.children.filter { it !== strong }
                Figure(
                    value = strong?.let { prose(it.children, unknown) } ?: emptyList(),
                    caption = prose(rest, unknown),
                )
            }

    /** The rows of a pair-list wrapper, or null when this div is
        not one.

        A pair row is the shape every school writes its examples
        in. Two spellings exist in real lessons:

          <p class="…"><b>lead</b><span>gloss</span></p>
          <span><b>lead</b> gloss</span>

        and the b sitting HARD against its gloss is what makes a
        `<p>` row a row rather than a paragraph that happens to
        open bold: prose always has a space after the strong, the
        pair markup never does. A span at block level has no prose
        reading at all, so its gloss may follow a space.

        A wrapper counts when every element child is one of the
        two, at least one actually carries the shape, and there
        are at least two: one row is a sentence, not a list. A
        row without the shape keeps its words as the lead rather
        than sinking the whole list. */
    private fun pairsOf(el: Node.Element, unknown: MutableList<String>): List<SentenceRow>? {
        val children = el.children.filterIsInstance<Node.Element>()
        if (children.size < 2) return null
        /* Loose prose between the rows means this is not a list. */
        if (el.children.any { it is Node.Text && it.text.isNotBlank() }) return null

        var shaped = 0
        val rows = mutableListOf<SentenceRow>()
        for (child in children) {
            if (child.tag != "p" && child.tag != "inline-plain") return null
            val lead = child.children.filterIsInstance<Node.Element>()
                .firstOrNull()?.takeIf { it.tag == "strong" }
            val before = if (lead == null) emptyList() else child.children.takeWhile { it !== lead }
            val after = if (lead == null) emptyList() else child.children.dropWhile { it !== lead }.drop(1)
            val leadFirst = lead != null && before.all { it is Node.Text && it.text.isBlank() }
            val glossAdjacent = child.tag == "inline-plain" ||
                after.firstOrNull() is Node.Element
            if (lead != null && leadFirst && glossAdjacent && after.isNotEmpty()) {
                shaped++
                rows += SentenceRow(
                    lead = prose(lead.children, unknown),
                    gloss = prose(after, unknown),
                )
            } else {
                rows += SentenceRow(lead = prose(child.children, unknown), gloss = emptyList())
            }
        }
        if (shaped == 0) return null
        return rows.filter { it.lead.isNotEmpty() || it.gloss.isNotEmpty() }.takeIf { it.isNotEmpty() }
    }

    private fun photoOf(el: Node.Element, unknown: MutableList<String>): Block.Photo {
        val img = el.children.filterIsInstance<Node.Element>().firstOrNull { it.tag == "img" }
        val caption = el.children.filterIsInstance<Node.Element>()
            .firstOrNull { it.tag == "figcaption" }
        return Block.Photo(
            src = img?.attrs?.get("src").orEmpty(),
            alt = img?.attrs?.get("alt").orEmpty(),
            caption = caption?.let { prose(it.children, unknown) } ?: emptyList(),
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
                .map { prose(it.children, unknown) }
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

    /** Tags that own a line wherever they appear. When one has to
        DISSOLVE into a run of inlines (a paragraph inside a table
        cell, a dt/dd pair inside a wrapper nobody has met) its
        edges become line breaks, because two block children joined
        with nothing turn a sentence and its meaning into one word. */
    private val BLOCKISH = setOf(
        "p", "div", "li", "dt", "dd", "ul", "ol", "table", "tr",
        "h2", "h3", "blockquote", "figure", "pre",
    )

    private fun inlinesOf(nodes: List<Node>, unknown: MutableList<String>): List<Inline> {
        val out = mutableListOf<Inline>()
        /* A dissolved block child just closed here: whatever
           comes next starts its own line. */
        var wall = false

        fun add(items: List<Inline>, blockish: Boolean) {
            if (items.isEmpty()) return
            /* The whitespace between two dissolved paragraphs is
               the gap the wall already draws, not prose. */
            if (wall && items.all { it is Inline.Text && it.text.isBlank() }) return
            if ((wall || blockish) && out.isNotEmpty()) out += Inline.Break
            out += items
            wall = blockish
        }

        for (node in nodes) {
            when (node) {
                is Node.Text ->
                    if (node.text.isNotEmpty()) add(listOf(Inline.Text(node.text)), blockish = false)

                is Node.Element -> when (node.tag) {
                    "strong" -> add(listOf(Inline.Strong(inlinesOf(node.children, unknown))), false)
                    "em" -> add(listOf(Inline.Emphasis(inlinesOf(node.children, unknown))), false)
                    "code" -> add(listOf(Inline.Code(inlinesOf(node.children, unknown))), false)
                    "sup" -> add(listOf(Inline.Sup(inlinesOf(node.children, unknown))), false)
                    "sub" -> add(listOf(Inline.Sub(inlinesOf(node.children, unknown))), false)
                    /* An explicit break serves as any wall still
                       owed, rather than stacking a second blank
                       line on top of it. */
                    "br" -> { out += Inline.Break; wall = false }
                    "a" -> add(
                        listOf(
                            Inline.Link(
                                href = node.attrs["href"].orEmpty(),
                                children = inlinesOf(node.children, unknown),
                                isTerm = "term" in node.classes,
                            )
                        ),
                        false,
                    )
                    /* A wrapper with nothing to say inline: keep
                       the words, drop the box. */
                    "inline-plain" -> add(inlinesOf(node.children, unknown), false)
                    in BLOCKISH -> add(inlinesOf(node.children, unknown), blockish = true)
                    else -> {
                        unknown += node.tag
                        add(inlinesOf(node.children, unknown), false)
                    }
                }
            }
        }
        return collapse(out)
    }

    /** A block's own run: flattened, then trimmed the way a
        browser lays it out. Only a BLOCK's edges and its line
        breaks swallow the source's indentation; the spaces inside
        a strong or a link are prose and stay. */
    private fun prose(nodes: List<Node>, unknown: MutableList<String>): List<Inline> =
        tidy(inlinesOf(nodes, unknown))

    /** What a browser does at a block's edges. The whitespace that
        pretty-printed source leaves at the start and end of a run,
        and either side of a line break, is layout rather than
        prose: without this, every line the author wrapped arrived
        indented by one space, which is what put " জার্মান:" a step
        to the right of the line above it. */
    private fun tidy(inlines: List<Inline>): List<Inline> {
        if (inlines.isEmpty()) return inlines
        val out = inlines.toMutableList()

        fun trimEnd(at: Int): Int {
            val item = out.getOrNull(at) as? Inline.Text ?: return 0
            val trimmed = item.text.trimEnd()
            if (trimmed.isEmpty()) { out.removeAt(at); return 1 }
            out[at] = Inline.Text(trimmed)
            return 0
        }

        fun trimStart(at: Int) {
            val item = out.getOrNull(at) as? Inline.Text ?: return
            val trimmed = item.text.trimStart()
            if (trimmed.isEmpty()) out.removeAt(at) else out[at] = Inline.Text(trimmed)
        }

        trimStart(0)
        trimEnd(out.lastIndex)
        var i = 0
        while (i < out.size) {
            if (out[i] is Inline.Break) {
                i -= trimEnd(i - 1)
                trimStart(i + 1)
            }
            i++
        }
        return out
    }

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
            /* Stored prose is pretty-printed, and a browser
               collapses the indentation under `white-space:
               normal`; nothing here did, so every line the author
               wrapped arrived as a line break in the middle of a
               sentence: "তোমার / বন্ধু" split mid-phrase in the
               satzbau lesson was this. One space is what CSS
               leaves. */
            val decoded = unescape(text).replace(WHITESPACE, " ")
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
                   it in place. The pattern box holds blocks the
                   same way. */
                is Block.Callout -> walk(block.body, path)
                is Block.Pattern -> walk(block.body, path)
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
            is Block.Pattern -> walk(block.body)
            else -> Unit
        }
    }
    walk(blocks)
    return total
}
