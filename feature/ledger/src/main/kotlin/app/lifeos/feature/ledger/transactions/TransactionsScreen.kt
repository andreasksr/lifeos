package app.lifeos.feature.ledger.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.MoneyText
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.TransactionKind
import app.lifeos.feature.ledger.ui.TransactionLine

@Composable
fun TransactionsRoute(viewModel: TransactionsViewModel, onBack: () -> Unit, onOpen: (NodeId) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val today = s.today
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = "Buchungen",
                title = when (val p = s.period) {
                    is Period.Month -> LifeFormat.monthTitle(p.month)
                    Period.All -> "Alle Zeiträume"
                },
                subtitle = if (s.resultCount == s.totalCount) "${s.totalCount} Buchungen" else "${s.resultCount} von ${s.totalCount} Buchungen",
                onBack = onBack,
                actions = {
                    if (s.period is Period.Month) {
                        LifeIconButton(Glyph.CHEVRON_LEFT, "Vorheriger Monat", viewModel::previousMonth)
                        LifeIconButton(Glyph.CHEVRON_RIGHT, "Nächster Monat", viewModel::nextMonth)
                    }
                    LifeIconButton(Glyph.FILTER, "Filter", { showFilters = !showFilters }, highlighted = showFilters || !s.query.isEmpty)
                },
            )
        }
        item(key = "search") {
            Column(inset, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeTextField(
                    value = s.query.text,
                    onValueChange = viewModel::setText,
                    label = "",
                    placeholder = "Suchen: Händler, Notiz, Kategorie, Betrag",
                    leading = Glyph.SEARCH,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeChoiceChip("Dieser Monat", s.period is Period.Month, viewModel::showMonth)
                    LifeChoiceChip("Alle Zeiträume", s.period is Period.All, viewModel::showAll)
                }
            }
        }
        if (showFilters) {
            item(key = "filters") {
                Column(inset.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ART", style = Continuum.type.label)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.REFUND, TransactionKind.TRANSFER, TransactionKind.CORRECTION_IN)) { k ->
                            val selected = k in s.query.kinds || (k == TransactionKind.CORRECTION_IN && TransactionKind.CORRECTION_OUT in s.query.kinds)
                            LifeChoiceChip(LifeFormat.kindLabel(k), selected, {
                                viewModel.toggleKind(k)
                                if (k == TransactionKind.CORRECTION_IN) viewModel.toggleKind(TransactionKind.CORRECTION_OUT)
                            })
                        }
                    }
                    Text("KATEGORIE", style = Continuum.type.label)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { LifeChoiceChip("Alle", s.query.categoryId == null, { viewModel.setCategory(null) }) }
                        items(s.categories.filter { !it.archived }, key = { it.id.value }) { c ->
                            LifeChoiceChip(c.name, s.query.categoryId == c.id, { viewModel.setCategory(if (s.query.categoryId == c.id) null else c.id) })
                        }
                    }
                    Text("KONTO", style = Continuum.type.label)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { LifeChoiceChip("Alle", s.query.accountId == null, { viewModel.setAccount(null) }) }
                        items(s.accounts, key = { it.id.value }) { a ->
                            LifeChoiceChip(a.name, s.query.accountId == a.id, { viewModel.setAccount(if (s.query.accountId == a.id) null else a.id) })
                        }
                    }
                    if (!s.query.isEmpty) LifeLink("Filter zurücksetzen", viewModel::clearFilters)
                }
            }
        }
        if (s.days.isEmpty()) {
            item(key = "empty") {
                LifeEmpty(
                    if (s.totalCount == 0) "Keine Buchungen in diesem Zeitraum." else "Keine Buchung passt zur Suche.",
                    modifier = inset.padding(top = 16.dp),
                    glyph = Glyph.SEARCH,
                )
            }
        }
        s.days.forEach { day ->
            item(key = "d-${day.date}") {
                LifeTimelineHeader(
                    title = LifeFormat.dayTitle(day.date, today),
                    trailing = MoneyText.format(day.netMinor, showPlus = true),
                    highlighted = day.date == today,
                )
            }
            items(day.entries, key = { it.transaction.id.value }) { e ->
                TransactionLine(e, onClick = { onOpen(e.transaction.id) })
            }
        }
    }
}
