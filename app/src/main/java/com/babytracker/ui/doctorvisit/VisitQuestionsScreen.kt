package com.babytracker.ui.doctorvisit

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.component.DoctorVisitIcon
import com.babytracker.ui.theme.DoctorVisitPalette
import com.babytracker.ui.theme.doctorVisitColors

@Composable
fun VisitQuestionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VisitQuestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedMessage = stringResource(R.string.visit_questions_deleted)
    val undoLabel = stringResource(R.string.visit_questions_undo)
    LaunchedEffect(state.lastDeleted) {
        state.lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.onUndoDelete()
            SnackbarResult.Dismissed -> viewModel.onUndoConsumed()
        }
    }

    VisitQuestionsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onDraftChange = viewModel::onDraftChange,
        onAdd = viewModel::onAdd,
        onToggleAnswered = viewModel::onToggleAnswered,
        onExpand = viewModel::onExpand,
        onAnswerDraftChange = viewModel::onAnswerDraftChange,
        onSaveAnswer = viewModel::onSaveAnswer,
        onDelete = viewModel::onDelete,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitQuestionsContent(
    state: VisitQuestionsUiState,
    snackbarHostState: SnackbarHostState,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleAnswered: (Long) -> Unit,
    onExpand: (VisitQuestion?) -> Unit,
    onAnswerDraftChange: (String) -> Unit,
    onSaveAnswer: () -> Unit,
    onDelete: (VisitQuestion) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visit_questions_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            QuestionInputRow(
                draft = state.draft,
                onDraftChange = onDraftChange,
                onAdd = onAdd,
                colors = colors,
            )
            if (state.questions.isEmpty()) {
                EmptyInbox()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(state.questions, key = { it.id }) { question ->
                        val expanded = state.expandedQuestion?.id == question.id
                        QuestionRow(
                            question = question,
                            expanded = expanded,
                            answerDraft = if (expanded) state.answerDraft else "",
                            colors = colors,
                            onToggleAnswered = { onToggleAnswered(question.id) },
                            onExpand = { onExpand(if (expanded) null else question) },
                            onAnswerDraftChange = onAnswerDraftChange,
                            onSaveAnswer = onSaveAnswer,
                            onDelete = { onDelete(question) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionInputRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    colors: DoctorVisitPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.visit_questions_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        FilledIconButton(
            onClick = onAdd,
            enabled = draft.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.visit_questions_add),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionRow(
    question: VisitQuestion,
    expanded: Boolean,
    answerDraft: String,
    colors: DoctorVisitPalette,
    onToggleAnswered: () -> Unit,
    onExpand: () -> Unit,
    onAnswerDraftChange: (String) -> Unit,
    onSaveAnswer: () -> Unit,
    onDelete: () -> Unit,
) {
    val expandLabel = stringResource(
        if (expanded) R.string.visit_questions_collapse else R.string.visit_questions_expand,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = question.answered,
                onCheckedChange = { onToggleAnswered() },
            )
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onContainer,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (question.answered) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = expandLabel, onClick = onExpand)
                    .padding(vertical = 16.dp),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.visit_questions_delete),
                    tint = colors.onContainer,
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = answerDraft,
                onValueChange = onAnswerDraftChange,
                label = { Text(stringResource(R.string.visit_questions_answer_label)) },
                placeholder = { Text(stringResource(R.string.visit_questions_answer_hint)) },
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSaveAnswer() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .onFocusChanged { if (!it.isFocused) onSaveAnswer() },
            )
        }
    }
}

@Composable
private fun EmptyInbox() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoctorVisitIcon(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.visit_questions_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
