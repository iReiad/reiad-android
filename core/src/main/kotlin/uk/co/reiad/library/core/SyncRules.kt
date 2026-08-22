package uk.co.reiad.library.core

/* ============================================================
   The sync arithmetic, ported from `aab/src/sync.ts`.

   One sentence governs all of it: **the account is the record and
   the device is a mirror.** Signing in adopts the account's rows,
   including its deletions; nothing on the device is merged
   upwards. Signing out takes the mirror off, so the next person
   at the same handset does not inherit somebody's ticks.

   What is left after that is two signed-in devices, and this file
   is that. Three rules, one per kind of value:

     SET    a set of ids. `(remote UNION added) MINUS removed`,
            where `added` is what this device gained since the
            last exchange and `removed` is what it dropped. A
            reset needs no special case: resetting REMOVES the
            key, an absent key is an empty set, and the
            subtraction takes the account down with it. The
            version before this one needed a timestamp per key to
            get that right and got it wrong for a year.

     MARK   an object carrying its own `ts`. Newest wins.

     COUNT  a number. The larger wins, UNLESS this device went
            backwards, which is what a reset looks like to a
            counter.

   `base` is what the account said at the last exchange. It is
   what makes the difference between "this device added it" and
   "the other device removed it", which are the same state
   otherwise. Any failed exchange drops `base` rather than
   trusting half a conversation.
   ============================================================ */

/** What the account said last time, per key. Null before the
    first exchange, and after any failure. */
typealias Base = Map<String, StoredValue>

/** A value as it travels. Deliberately not a sealed hierarchy of
    domain types: this layer moves whatever the key holds, and the
    key decides how. */
sealed interface StoredValue {
    data class Ids(val ids: Set<String>) : StoredValue
    data class Mark(val ts: Long, val json: String) : StoredValue
    data class Count(val n: Long) : StoredValue
}

object SyncRules {

    /** `(theirs UNION added) MINUS removed`.

        `added` is `mine MINUS was`, `removed` is `was MINUS mine`.
        Both are computed against `was` rather than against
        `theirs`, which is the whole point: without it, a tick the
        other device removed comes straight back. */
    fun reconcileSet(was: Set<String>, mine: Set<String>, theirs: Set<String>): Set<String> {
        val added = mine - was
        val removed = was - mine
        return (theirs + added) - removed
    }

    /** Newest `ts` wins. Equal timestamps keep what is already
        here, so an exchange that changes nothing writes nothing. */
    fun reconcileMark(mine: StoredValue.Mark?, theirs: StoredValue.Mark?): StoredValue.Mark? {
        if (mine == null) return theirs
        if (theirs == null) return mine
        return if (theirs.ts > mine.ts) theirs else mine
    }

    /** The larger wins, unless this device went backwards.

        A counter only ever climbs in normal use, so `mine < was`
        is not a stale device: it is a reset, and a reset has to
        beat a larger number on the account or it undoes itself on
        the next exchange. */
    fun reconcileCount(was: Long, mine: Long, theirs: Long): Long =
        if (mine < was) mine else maxOf(mine, theirs)

    /** One key, whichever kind it is. Returns what the device
        should hold and what should go up, which are not always
        the same value: a device can be already correct and still
        owe the account a write. */
    fun reconcile(
        rule: MergeRule,
        was: StoredValue?,
        mine: StoredValue?,
        theirs: StoredValue?,
    ): StoredValue? = when (rule) {
        MergeRule.SET -> StoredValue.Ids(
            reconcileSet(
                (was as? StoredValue.Ids)?.ids.orEmpty(),
                (mine as? StoredValue.Ids)?.ids.orEmpty(),
                (theirs as? StoredValue.Ids)?.ids.orEmpty(),
            )
        )

        MergeRule.MARK -> reconcileMark(mine as? StoredValue.Mark, theirs as? StoredValue.Mark)

        MergeRule.COUNT -> StoredValue.Count(
            reconcileCount(
                (was as? StoredValue.Count)?.n ?: 0L,
                (mine as? StoredValue.Count)?.n ?: 0L,
                (theirs as? StoredValue.Count)?.n ?: 0L,
            )
        )
    }

    /** What signing in does: the account's rows are written on to
        the device, and any synced key the account does NOT hold is
        removed from it.

        The removal is the half that looks like a bug and is the
        point. A browser is not a copy of an account: it may be a
        library machine or a phone handed over for five minutes,
        and the site cannot tell. Merging what was already there
        would upload a stranger's reading. */
    fun adopt(remote: Map<String, StoredValue>): AdoptResult {
        val write = remote.toMap()
        val forget = SyncKeys.ALL.keys - remote.keys
        return AdoptResult(write = write, forget = forget)
    }
}

/** What `adopt` decided: what to write, and what to take off the
    device because the account does not have it. */
data class AdoptResult(
    val write: Map<String, StoredValue>,
    val forget: Set<String>,
)
