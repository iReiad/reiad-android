package uk.co.reiad.library.core

/* ============================================================
   What gets read out, and what is furniture.

   The site's own read-aloud speaks `h1,h2,h3,h4,p,li` and skips
   anything inside the prev/next pair, the note under every piece,
   the byline, the reactions, the questions and the toolbar
   itself, which is the one thing on the page that could read
   itself out.

   Here the furniture is not in the block list at all: a byline is
   a component, not a block. So what this decides is the other
   half of the same question, which blocks are PROSE, and it is in
   core for the usual reason: a decision about what a piece says
   is the same decision everywhere, and only the speaking differs.

   ---- what is deliberately not read ----

   A table, a row of key figures and a photo's caption. The site
   does not read them either, and the reason is the same: read out
   loud, a table is a stream of numbers with no shape, and a
   synthesiser saying "sixteen by nine" over a caption is noise
   where a reader wanted the argument.

   An UNKNOWN block IS read. Its tag was lost and its words were
   not, and a reader listening should not silently lose a
   paragraph because the parser had not met its wrapper.
   ============================================================ */

/** One thing to say, and which block it came from, so a renderer
    can mark the place. */
data class Utterance(val block: Int, val text: String)

/** A one-character line is a bullet, a dash or a stray letter,
    and a synthesiser reads it as a noise rather than a word. */
private const val TOO_SHORT = 2

/** The prose of a piece, in the order it is said. */
fun speakable(blocks: List<Block>): List<Utterance> {
    val out = mutableListOf<Utterance>()

    fun say(at: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.length >= TOO_SHORT) out += Utterance(at, trimmed)
    }

    fun walk(list: List<Block>, at: Int) {
        for (block in list) when (block) {
            is Block.Heading -> say(at, block.inlines.words())
            is Block.Paragraph -> say(at, block.inlines.words())
            is Block.Quote -> say(at, block.inlines.words())
            is Block.Bullets -> for (item in block.items) say(at, item.words())
            is Block.Numbers -> for (item in block.items) say(at, item.words())
            is Block.Steps -> for (item in block.items) say(at, item.words())
            is Block.Checklist -> for (item in block.items) say(at, item.words())

            /* A callout is prose in a box: its label first, then
               what is in it. The site reads it for the same
               reason, that the paragraphs inside are `p`. */
            is Block.Callout -> {
                say(at, block.label.words())
                walk(block.body, at)
            }

            is Block.Unknown -> say(at, block.inlines.words())

            /* Read out loud these are noise rather than argument. */
            is Block.Table, is Block.KeyFigures, is Block.Photo, Block.Rule -> Unit
        }
    }

    for ((index, block) in blocks.withIndex()) walk(listOf(block), index)
    return out
}

/** The words of a run, with nothing but the words. */
fun List<Inline>.words(): String = buildString {
    fun walk(list: List<Inline>) {
        for (inline in list) when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Strong -> walk(inline.children)
            is Inline.Emphasis -> walk(inline.children)
            is Inline.Code -> walk(inline.children)
            is Inline.Sup -> walk(inline.children)
            is Inline.Sub -> walk(inline.children)
            is Inline.Link -> walk(inline.children)
            Inline.Break -> append(" ")
        }
    }
    walk(this@words)
}.replace(Regex("\\s+"), " ").trim()

/** Which language to ask a synthesiser for.

    One Bangla character is enough. A piece is written in one
    language here, and the alternative is a ratio nobody could
    explain: "more than forty per cent Bangla" is a rule that
    breaks on a Bangla piece quoting three English sentences. */
fun languageOf(text: String): String =
    if (text.any { it.code in 0x0980..0x09FF }) "bn" else "en"

/** The closest available voice to the language wanted.

    The exact tag, then the language whatever the region, then an
    English one for a language this machine has no voice for at
    all. That last fallback is deliberate and it is the site's:
    a Bangla sentence read by an English voice is mispronounced,
    and mispronounced words are still words, where silence is
    nothing. */
fun voiceFor(available: List<String>, want: String): String? {
    val short = want.substringBefore('-')
    available.firstOrNull { it.equals(want, ignoreCase = true) }?.let { return it }
    available.firstOrNull { it.substringBefore('-').equals(short, ignoreCase = true) }
        ?.let { return it }
    if (short.equals("en", ignoreCase = true)) return null
    return available.firstOrNull { it.startsWith("en", ignoreCase = true) }
}

/** How fast, as the three steps a reader is offered.

    Three rather than a slider, because a slider on a phone is a
    thing to fight with mid-sentence and the useful range is
    small. 1.0 is the synthesiser's own pace. */
enum class Pace(val id: String, val rate: Float) {
    SLOW("slow", 0.8f), NORMAL("normal", 1.0f), QUICK("quick", 1.3f)
}

val PACES = listOf(
    PrefOption(Pace.SLOW, "Slower", "for a language you are learning"),
    PrefOption(Pace.NORMAL, "Normal", "the voice's own pace"),
    PrefOption(Pace.QUICK, "Quicker", "for something you have read before"),
)

fun paceOf(id: String?): Pace = Pace.entries.firstOrNull { it.id == id } ?: Pace.NORMAL
