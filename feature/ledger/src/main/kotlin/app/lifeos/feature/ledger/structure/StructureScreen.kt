package app.lifeos.feature.ledger.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifePanel
import app.lifeos.core.designsystem.component.LifeTextField
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.ledger.LedgerMath
import app.lifeos.core.model.AccountKind
import app.lifeos.core.model.CategoryDirection
import app.lifeos.core.model.MoneyText
import app.lifeos.feature.ledger.presentation.LedgerFormat

private fun AccountKind.label() = when (this) {
    AccountKind.CASH -> "Bargeld"
    AccountKind.CHECKING -> "Girokonto"
    AccountKind.SAVINGS -> "Sparkonto"
    AccountKind.CREDIT_CARD -> "Kreditkarte"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructureRoute(viewModel: StructureViewModel, onBack: () -> Unit) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val forms by viewModel.forms.collectAsStateWithLifecycle()
    val month = viewModel.month

    Column(
        Modifier
            .fillMaxSize()
            .background(ContinuumColors.Obsidian)
            .imePadding(),
    ) {
        LifeTopBar(overline = "Haushaltskasse", title = "Konten & Budgets", onBack = onBack)
        val snapshot = snap ?: return@Column

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContinuumSpacing.gutter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            forms.message?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Cyan) }

            // ───── Budgets ─────
            val budgets = LedgerMath.effectiveBudgets(snapshot.budgets, month, snapshot.currency)
            val spent = snapshot.expenseLines.associate { it.category.id to it.totalMinor }
            LifePanel(title = "Monatsbudgets · ${LedgerFormat.monthTitle(month)}") {
                Text(
                    "Ein Budget gilt ab ${LedgerFormat.monthName(month)} für alle folgenden Monate, bis du es änderst.",
                    style = Continuum.type.bodyMuted,
                )
                snapshot.categories.filter { it.direction == CategoryDirection.EXPENSE && !it.archived }.forEach { cat ->
                    val budget = budgets[cat.id]
                    val spentMinor = spent[cat.id] ?: 0L
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ContinuumSpacing.touchTarget)
                                .clickable(role = Role.Button, onClickLabel = "Budget bearbeiten") {
                                    viewModel.startBudgetEdit(cat.id, budget?.amountMinor)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.name, style = Continuum.type.body, modifier = Modifier.weight(1f))
                            Text(
                                if (budget != null) "${MoneyText.format(spentMinor)} / ${MoneyText.format(budget.amountMinor)}" else "kein Budget",
                                style = Continuum.type.figures,
                                color = if (budget == null) ContinuumColors.IceFaint else ContinuumColors.IceMuted,
                            )
                        }
                        if (budget != null) {
                            LifeLine(progress = LedgerMath.permille(spentMinor, budget.amountMinor) / 1000f, meaning = LineMeaning.PROGRESS)
                        }
                        if (forms.budgetEditing == cat.id) {
                            Spacer(Modifier.height(ContinuumSpacing.s))
                            LifeTextField(
                                value = forms.budgetText,
                                onValueChange = viewModel::onBudgetText,
                                label = "Monatsbudget in €",
                                placeholder = "0,00",
                                error = forms.budgetError,
                                textStyle = Continuum.type.figures,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), modifier = Modifier.padding(top = ContinuumSpacing.s)) {
                                LifeButton("Speichern", viewModel::saveBudget)
                                LifeButton("Abbrechen", viewModel::cancelBudgetEdit, style = LifeButtonStyle.GHOST)
                            }
                        }
                    }
                }
            }

            // ───── Konten ─────
            LifePanel(title = "Konten") {
                snapshot.balances.forEach { ab ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ab.account.name, style = Continuum.type.body)
                            Text(ab.account.kind.label(), style = Continuum.type.bodyMuted)
                        }
                        Text(MoneyText.format(ab.balance), style = Continuum.type.figures)
                    }
                }
                LifeLine(meaning = LineMeaning.TIME)
                Text("NEUES KONTO", style = Continuum.type.label)
                LifeTextField(forms.accountName, viewModel::onAccountName, label = "Name", placeholder = "z. B. Bargeld", error = forms.accountError)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                    AccountKind.entries.forEach { kind ->
                        LifeChoiceChip(kind.label(), selected = forms.accountKind == kind, onClick = { viewModel.onAccountKind(kind) })
                    }
                }
                LifeTextField(
                    forms.accountOpening, viewModel::onAccountOpening,
                    label = "Anfangsbestand in €", placeholder = "0,00",
                    textStyle = Continuum.type.figures,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                LifeButton("Konto anlegen", viewModel::createAccount)
            }

            // ───── Kategorien ─────
            LifePanel(title = "Neue Kategorie") {
                LifeTextField(forms.categoryName, viewModel::onCategoryName, label = "Name", placeholder = "z. B. Haustier", error = forms.categoryError)
                Row(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                    LifeChoiceChip("Ausgabe", selected = forms.categoryDirection == CategoryDirection.EXPENSE, onClick = { viewModel.onCategoryDirection(CategoryDirection.EXPENSE) })
                    LifeChoiceChip("Einnahme", selected = forms.categoryDirection == CategoryDirection.INCOME, onClick = { viewModel.onCategoryDirection(CategoryDirection.INCOME) })
                }
                LifeButton("Kategorie anlegen", viewModel::createCategory)
            }
            Spacer(Modifier.height(ContinuumSpacing.xxl))
        }
    }
}
