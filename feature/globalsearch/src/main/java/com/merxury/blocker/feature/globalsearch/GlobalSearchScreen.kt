package com.merxury.blocker.feature.globalsearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.globalsearch.model.LocalSearchUiState
import com.merxury.blocker.feature.globalsearch.model.LocalSearchViewModel
import com.merxury.blocker.feature.globalsearch.model.SearchBoxUiState

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun GlobalSearchRoute(
    viewModel: LocalSearchViewModel = hiltViewModel()
) {
    val searchBoxUiState by viewModel.searchBoxUiState.collectAsStateWithLifecycle()
    val localSearchUiState by viewModel.localSearchUiState.collectAsStateWithLifecycle()
    GlobalSearchScreen(
        searchBoxUiState = searchBoxUiState,
        localSearchUiState = localSearchUiState
    )
}

@Composable
fun GlobalSearchScreen(
    modifier: Modifier = Modifier,
    searchBoxUiState: SearchBoxUiState,
    localSearchUiState: LocalSearchUiState
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SearchBar(
            modifier = modifier,
            uiState = searchBoxUiState
        )
        when (localSearchUiState) {
            LocalSearchUiState.Loading -> {
                Column(
                    modifier = modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BlockerLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = R.string.searching),
                    )
                }
            }

            is LocalSearchUiState.LocalSearchResult -> {
                GlobalSearchContent(
                    modifier = modifier,
                    localSearchUiState = localSearchUiState
                )
            }

            is LocalSearchUiState.Error -> {
                ErrorScreen(localSearchUiState.message)
            }
        }
    }
}

@Composable
fun GlobalSearchContent(
    modifier: Modifier = Modifier,
    localSearchUiState: LocalSearchUiState.LocalSearchResult
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FilledTonalButton(onClick = { /*TODO*/ }) {
            Text(text = stringResource(id = R.string.application, localSearchUiState.appCount))
        }
        Spacer(modifier = modifier.width(8.dp))
        FilledTonalButton(onClick = { /*TODO*/ }) {
            Text(text = stringResource(id = R.string.component, localSearchUiState.componentCount))
        }
        Spacer(modifier = modifier.width(8.dp))
        FilledTonalButton(onClick = { /*TODO*/ }) {
            Text(
                text = stringResource(
                    id = R.string.online_rule,
                    localSearchUiState.onlineRuleCount
                )
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    uiState: SearchBoxUiState,
    placeholderText: String = "",
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onClearClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var showClearButton by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    BlockerTopAppBar(
        title = "",
        navigationIcon = BlockerIcons.Back,
        navigationIconContentDescription = null,
        onNavigationClick = onNavigateBack,
        actions = {
            TextField(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 4.dp)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { focusState ->
                        showClearButton = (focusState.isFocused)
                    }
                    .focusRequester(focusRequester),
                value = uiState.keyword,
                onValueChange = onSearchTextChanged,
                placeholder = {
                    Text(text = placeholderText)
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = showClearButton,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { onClearClick() }) {
                            Icon(
                                imageVector = BlockerIcons.Clear,
                                contentDescription = null
                            )
                        }
                    }
                },
                maxLines = 1,
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                }),
            )
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ErrorScreen(message: ErrorMessage) {
    Text(text = message.message)
}

@Composable
@Preview
fun GlobalSearchScreenPreview() {
    val searchBoxUiState = SearchBoxUiState()
    val localSearchUiState = LocalSearchUiState.LocalSearchResult(
        filter = listOf(),
        appCount = 0,
        componentCount = 99,
        onlineRuleCount = 6
    )
    BlockerTheme {
        Surface {
            GlobalSearchScreen(
                searchBoxUiState = searchBoxUiState,
                localSearchUiState = localSearchUiState
            )
        }
    }
}
