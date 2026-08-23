package uk.co.reiad.library.core.stock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Every number the model reads is a number a reader can reach.

   The registry is presentation and it is ported rather than
   fetched, which is safe for one reason: a field added on the
   site is a field `Inputs` does not have, and `ShareTest` fails
   on it against the site's own `DEFAULTS`. This file closes the
   other half, which nothing else can see: a field `Inputs` HAS
   and no group shows.

   That is a real shape of bug rather than a hypothetical one. A
   number left out of the form still has a default, still feeds
   every ratio that reads it, and still moves the verdict; it is
   simply not on the screen. Nothing looks wrong, and a reader
   whose company differs from the default on exactly that number
   gets an answer about a company that does not exist.
   ============================================================ */
class FieldsTest {

    @Test
    fun `every field of the model is somewhere a reader can reach it`() {
        val shown = FIELDS.map { it.id }.toSet()
        val model = fieldsOf(Inputs()).keys
        assertEquals(
            emptySet(), model - shown,
            "the model reads these and no group shows them, so they can only ever hold " +
                "their default while still moving the verdict",
        )
        assertEquals(
            emptySet(), shown - model,
            "the form offers these and the model has no such field",
        )
    }

    @Test
    fun `every field is in exactly one group, and every group has fields`() {
        assertEquals(FIELDS.size, FIELDS.map { it.id }.toSet().size, "a field is listed twice")
        for (f in FIELDS) {
            assertTrue(f.group in GROUPS, "${f.id} is in the group '${f.group}', which is not one")
        }
        for (g in GROUPS) {
            assertTrue(FIELDS.any { it.group == g }, "the group '$g' has no fields in it")
        }
    }

    /* A capital adequacy ratio on a textile mill is a box that
       can only be filled in wrongly, and the metrics one layer
       down already refuse to score it. The form has to agree, or
       it is asking for a number it will then ignore. */
    @Test
    fun `the bank group is offered only where it means anything`() {
        assertTrue("bank" in groupsFor("bank"))
        assertTrue("bank" in groupsFor("nbfi"))
        assertTrue("bank" in groupsFor("insurance"))
        assertTrue("bank" !in groupsFor("textile"))
        assertTrue("bank" !in groupsFor("pharma"))
        /* And nothing else moves, or the form would reshuffle
           under a reader who changed one dropdown. */
        assertEquals(GROUPS - "bank", groupsFor("pharma"))
    }

    @Test
    fun `a slider that cannot reach its own default is a slider nobody can use`() {
        for (f in FIELDS) {
            val s = f.slider ?: continue
            val start = valueOf(Inputs(), f.id).toDouble()
            assertTrue(
                start in s.low..s.high,
                "${f.id} starts at $start and its slider runs ${s.low} to ${s.high}, so the " +
                    "first drag would jump the value",
            )
            assertTrue(s.by > 0 && s.high > s.low, "${f.id}'s slider is not a range")
        }
    }

    @Test
    fun `a choice field offers choices the model knows`() {
        val sector = FIELDS.first { it.id == "sector" }
        assertEquals(SECTORS.keys.toList(), sector.choices)
        assertEquals("sector.", sector.choicePrefix)
        assertTrue(Inputs().sector in sector.choices!!)

        val benchmark = FIELDS.first { it.id == "benchmark" }
        assertEquals(INDICES.keys.toList(), benchmark.choices)
        assertTrue(Inputs().benchmark in benchmark.choices!!)

        val category = FIELDS.first { it.id == "category" }
        assertEquals(listOf("A", "B", "N", "Z"), category.choices)
        assertEquals(null, category.choicePrefix, "a market category is the letter itself")
        assertTrue(Inputs().category in category.choices!!)
    }

    /* ---------- reading and writing one box ---------- */

    @Test
    fun `a box reads and writes the field it names`() {
        assertEquals("210", valueOf(Inputs(), "price"))
        assertEquals("pharma", valueOf(Inputs(), "sector"))
        assertEquals(340.0, withField(Inputs(), "price", "340").price)
        assertEquals("bank", withField(Inputs(), "sector", "bank").sector)
    }

    /* A box being typed into goes through the same decoder a
       shared link does, so the two cannot come to different
       conclusions about what a number is. */
    @Test
    fun `a half-typed number leaves the value alone rather than becoming nought`() {
        val d = Inputs().copy(price = 340.0)
        for (half in listOf("", "-", ".", "1e", "abc")) {
            assertEquals(
                340.0, withField(d, "price", half).price,
                "'$half' should not have moved the price",
            )
        }
        assertEquals(1.5, withField(d, "price", "1.5").price)
    }

    @Test
    fun `writing one field leaves every other alone`() {
        val d = Inputs().copy(sector = "bank", npl = 4.5, revenue = 41000.0)
        val after = withField(d, "price", "99")
        assertEquals(d.copy(price = 99.0), after)
    }
}
