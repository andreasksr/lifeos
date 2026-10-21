package app.lifeos.core.domain.home

import app.lifeos.core.domain.ledger.LedgerMonthSnapshot
import app.lifeos.core.domain.ledger.OccurrenceState
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId

/** Abschnitte der Startseite. Neue Abschnitte nur anhängen (Einstellungen speichern die Namen). */
enum class HomeSectionId { TODAY, ATTENTION, FINANCE, PLANNING, PROJECTS, LEARNING, CAPTURE }

enum class HomeDensity { MINIMAL, STANDARD, ADVANCED }

/** Persönliche Anordnung der Startseite. */
data class HomeLayout(
    val order: List<HomeSectionId> = HomeSectionId.entries.toList(),
    val hidden: Set<HomeSectionId> = emptySet(),
    val density: HomeDensity = HomeDensity.STANDARD,
) {
    /** Sichtbare Abschnitte in Reihenfolge – nur solche, deren Modul tatsächlich verfügbar ist. */
    fun visible(available: Set<HomeSectionId>): List<HomeSectionId> =
        order.filter { it in available && it !in hidden }

    fun moveUp(id: HomeSectionId): HomeLayout {
        val i = order.indexOf(id)
        if (i <= 0) return this
        return copy(order = order.toMutableList().apply { add(i - 1, removeAt(i)) })
    }

    fun moveDown(id: HomeSectionId): HomeLayout {
        val i = order.indexOf(id)
        if (i < 0 || i >= order.lastIndex) return this
        return copy(order = order.toMutableList().apply { add(i + 1, removeAt(i)) })
    }

    fun toggle(id: HomeSectionId): HomeLayout = copy(hidden = if (id in hidden) hidden - id else hidden + id)

    companion object {
        const val SETTINGS_KEY = "home.layout.v1"

        /** Format: `ORDER=A,B,C;HIDDEN=X,Y;DENSITY=STANDARD` – robust gegen unbekannte/neue Werte. */
        fun encode(layout: HomeLayout): String =
            "ORDER=${layout.order.joinToString(",")};HIDDEN=${layout.hidden.joinToString(",")};DENSITY=${layout.density}"

        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return HomeLayout()
            val parts = text.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun ids(s: String?) = s.orEmpty().split(',').mapNotNull { n -> HomeSectionId.entries.firstOrNull { it.name == n } }
            val known = ids(parts["ORDER"]).distinct()
            // Neue Abschnitte (aus späteren Versionen) hinten anhängen, damit nichts verloren geht.
            val order = known + HomeSectionId.entries.filter { it !in known }
            val density = HomeDensity.entries.firstOrNull { it.name == parts["DENSITY"] } ?: HomeDensity.STANDARD
            return HomeLayout(order, ids(parts["HIDDEN"]).toSet(), density)
        }
    }
}

enum class AttentionSeverity { INFO, WARN, ALERT }

/** Wohin ein Hinweis führt – die App übersetzt das in Navigation. */
sealed interface AttentionTarget {
    data object RecurringPayments : AttentionTarget
    data object Budgets : AttentionTarget
    data class Node(val id: NodeId) : AttentionTarget
}

data class AttentionItem(
    val title: String,
    val detail: String,
    val severity: AttentionSeverity,
    val target: AttentionTarget,
    /** Für Sortierung: kleiner = dringender. */
    val rank: Long,
)

/**
 * Sammelt „Aufmerksamkeit erforderlich"-Hinweise. Nur aus echten Daten; keine Hinweise ohne Anlass.
 */
object AttentionBuilder {

    fun fromFinance(s: LedgerMonthSnapshot): List<AttentionItem> {
        val items = mutableListOf<AttentionItem>()
        s.pending.forEach { o ->
            val overdue = o.state == OccurrenceState.OVERDUE
            val days = java.time.temporal.ChronoUnit.DAYS.between(s.today, o.dueDate)
            if (overdue || days <= 3) {
                items += AttentionItem(
                    title = o.payment.name,
                    detail = "${LifeFormat.relativeDue(o.dueDate, s.today)} · ${MoneyText.format(o.payment.amount)}",
                    severity = if (overdue) AttentionSeverity.ALERT else AttentionSeverity.WARN,
                    target = AttentionTarget.RecurringPayments,
                    rank = days,
                )
            }
        }
        if (s.isCurrentMonth) {
            s.totalBudget?.takeIf { it.isExceeded }?.let {
                items += AttentionItem(
                    "Monatsbudget überschritten",
                    "um ${MoneyText.format(-it.remainingMinor)} · ${LifeFormat.percent(it.usedPermille)}",
                    AttentionSeverity.ALERT, AttentionTarget.Budgets, -1,
                )
            }
            s.expenseLines.filter { it.budget?.isExceeded == true }.forEach { line ->
                val b = line.budget!!
                items += AttentionItem(
                    "Budget ${line.category.name} überschritten",
                    "um ${MoneyText.format(-b.remainingMinor)}",
                    AttentionSeverity.WARN, AttentionTarget.Budgets, 0,
                )
            }
            s.forecast?.takeIf { it.expectedAtMonthEnd.isNegative && !s.totalBalance.isNegative }?.let {
                items += AttentionItem(
                    "Kontostand wird bis Monatsende negativ",
                    "voraussichtlich ${MoneyText.format(it.expectedAtMonthEnd)}",
                    AttentionSeverity.WARN, AttentionTarget.RecurringPayments, 1,
                )
            }
        }
        return items.sortedWith(compareBy<AttentionItem> { -it.severity.ordinal }.thenBy { it.rank })
    }

    fun merge(vararg sources: List<AttentionItem>): List<AttentionItem> =
        sources.toList().flatten().sortedWith(compareBy<AttentionItem> { -it.severity.ordinal }.thenBy { it.rank })
}
