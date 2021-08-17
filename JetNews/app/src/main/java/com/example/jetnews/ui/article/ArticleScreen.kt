/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetnews.ui.article

import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.provider.MediaStore
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.Either
import arrow.core.none
import arrow.core.some
import arrow.optics.Optional
import arrow.optics.optics
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.posts.PostsRepository
import com.example.jetnews.data.posts.impl.BlockingFakePostsRepository
import com.example.jetnews.data.posts.impl.post3
import com.example.jetnews.framework.*
import com.example.jetnews.model.Post
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.home.BookmarkButton
import com.example.jetnews.ui.home.PostState
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.produceUiState
import com.example.jetnews.utils.supportWideScreen
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking



sealed class ArticleStatus{
    object NotRequested:ArticleStatus()
    data class NotLoadedFor(val id:String):ArticleStatus()
    data class LoadingInProgressFor(val id:String):ArticleStatus()
    data class ErrorFor(val id:String, val error:Throwable):ArticleStatus()
    data class Loaded(val id:String, val post:PostState):ArticleStatus()
}

@optics
data class ArticleScreenState(
    val article:ArticleStatus,
    val dialogShown: Boolean
){
    companion object{
        val articleViewState:Optional<ArticleScreenState, ArticleViewState> = Optional(
            getOption = {
                if (it.article is ArticleStatus.Loaded)
                    ArticleViewState(article = it.article.post, dialogShown = it.dialogShown).some()
                else none()
            },
            set = { screenState, viewState ->
                screenState.copy(
                    article = ArticleStatus.Loaded(viewState.article.post.id, viewState.article),
                    dialogShown = viewState.dialogShown
                )
            }
        )
    }
}

@optics
sealed class ArticleScreenAction{
    companion object{}

    @optics data class LoadFor(val id:String):ArticleScreenAction(){
        companion object
    }
    @optics data class Loaded(val post:PostState):ArticleScreenAction(){
        companion object
    }
    @optics data class Error(val id:String, val error:Throwable):ArticleScreenAction(){
        companion object
    }
    @optics data class ArticleViewActions(val action:ArticleViewAction):ArticleScreenAction(){
        companion object
    }

    object ExternalNavigateBack:ArticleScreenAction()

    @optics data class ExternalShare(val title:String, val url:String):ArticleScreenAction(){
        companion object
    }
}

class ArticleScreenEnvironment(
    val getPost:(String) -> Flow<Post>,
    val favouritePosts:() -> Flow<Set<String>>,
    val toggleFavorite:(String) -> Flow<Set<String>>
){

}

fun ComposedArticleScreenReducer():Reducer<ArticleScreenState, ArticleScreenAction, ArticleScreenEnvironment> =
    combine(
        ArticleViewReducer().pullbackOptional(
            stateMapper = ArticleScreenState.articleViewState,
            actionMapper = ArticleScreenAction.articleViewActions.action,
            environmentMapper = {
                ArticleViewEnvironment(
                    toggleFavorite = it.toggleFavorite
                )
            }
        ),
        ArticleScreenReducer(),
        {
            state, action, env, _ ->
            when{
                action is ArticleScreenAction.ArticleViewActions &&
                        action.action is ArticleViewAction.ExternalSharePost ->
                    state to flowOf(ArticleScreenAction.ExternalShare(action.action.title, action.action.url))

                action is ArticleScreenAction.ArticleViewActions &&
                        action.action is ArticleViewAction.ExternalNavigateBack ->
                    state to flowOf(ArticleScreenAction.ExternalNavigateBack)

                else -> state to emptyFlow()
            }
        }
    )

fun ArticleScreenReducer():Reducer<ArticleScreenState, ArticleScreenAction, ArticleScreenEnvironment> = {
    state, action, env, scope ->
    when(action){
        is ArticleScreenAction.LoadFor ->
            env.getPost(action.id)
                .combine(env.favouritePosts()){post, favorites -> PostState(
                    favorite = favorites.contains(post.id),
                    post = post
                )}
                .map { Either.Right(it) as Either<Throwable, PostState> }
                .catch { emit(Either.Left(it)) as Either<Throwable, PostState> }
                .map { it.fold(
                    ifLeft = { ArticleScreenAction.Error(action.id, it) },
                    ifRight = { ArticleScreenAction.Loaded(it) }
                 )
                }.let {
                    state.copy(article = ArticleStatus.LoadingInProgressFor(action.id)) to it
                }

        is ArticleScreenAction.Error -> state.copy(article = ArticleStatus.ErrorFor(action.id, action.error)) to emptyFlow()

        is ArticleScreenAction.Loaded -> state.copy(article = ArticleStatus.Loaded(action.post.post.id, action.post)) to emptyFlow()

        is ArticleScreenAction.ArticleViewActions -> state to emptyFlow()

        ArticleScreenAction.ExternalNavigateBack -> state to emptyFlow()

        is ArticleScreenAction.ExternalShare -> state to emptyFlow()
    }
}

/**
 * Stateful Article Screen that manages state using [produceUiState]
 *
 * @param postId (state) the post to show
 * @param postsRepository data source for this screen
 * @param onBack (event) request back navigation
 */
@Suppress("DEPRECATION") // allow ViewModelLifecycleScope call
@Composable
fun ArticleScreen(
    store: Store<ArticleScreenState, ArticleScreenAction>
) {

    StoreView(store) { state ->

        if (state.article is ArticleStatus.NotLoadedFor){
            sendToStore(ArticleScreenAction.LoadFor(state.article.id))()
        }

        if (state.article is ArticleStatus.ErrorFor){
            sendToStore(ArticleScreenAction.LoadFor(state.article.id))()
        }

        val articleViewState = ArticleScreenState.articleViewState.getOrNull(state);

        if (articleViewState == null) return@StoreView

        val articleViewStore = store.forView<ArticleViewState, ArticleViewAction>(
            appState = state,
            stateBuilder = { articleViewState },
            actionMapper = { ArticleScreenAction.ArticleViewActions(it) }
        )

        ArticleView(
            articleViewStore
        )
    }
}



data class ArticleViewState(
    val article:PostState,
    val dialogShown:Boolean
)

sealed class ArticleViewAction {
    data class ToggleFavorite(val id: String) : ArticleViewAction()
    data class UpdatedFavorites(val favorites: Set<String>) : ArticleViewAction()
    object ShowFunctionalityNotAvailableDialog : ArticleViewAction()
    object HideFunctionalityNotAvailableDialog : ArticleViewAction()
    object ExternalNavigateBack : ArticleViewAction()
    class ExternalSharePost(val title:String, val url:String):ArticleViewAction()
}

class ArticleViewEnvironment(
    val toggleFavorite:(String) -> Flow<Set<String>>
)

fun ArticleViewReducer():Reducer<ArticleViewState, ArticleViewAction, ArticleViewEnvironment> = {
    state, action, env, _ ->
    when(action){
        ArticleViewAction.ShowFunctionalityNotAvailableDialog ->
            state.copy(dialogShown = true) to emptyFlow()

        ArticleViewAction.HideFunctionalityNotAvailableDialog ->
            state.copy(dialogShown = false) to emptyFlow()

        ArticleViewAction.ExternalNavigateBack -> state to emptyFlow()

        is ArticleViewAction.ToggleFavorite ->
            env.toggleFavorite(action.id)
                .map { ArticleViewAction.UpdatedFavorites(it) }
                .let {
                    state to it
                }

        is ArticleViewAction.UpdatedFavorites ->
            state.copy(
                article = state.article.copy(
                    favorite = action.favorites.contains(state.article.post.id)
                )
            ) to emptyFlow()

        is ArticleViewAction.ExternalSharePost -> state to emptyFlow()
    }
}

/**
 * Stateless Article Screen that displays a single post.
 *
 * @param post (state) item to display
 * @param onBack (event) request navigate back
 * @param isFavorite (state) is this item currently a favorite
 * @param onToggleFavorite (event) request that this post toggle it's favorite state
 */
@Composable
fun ArticleView(
    store:Store<ArticleViewState, ArticleViewAction>
) {


    StoreView(store) { state ->
        if (state.dialogShown) {
            FunctionalityNotAvailablePopup(sendToStore(ArticleViewAction.HideFunctionalityNotAvailableDialog))
        }



        Scaffold(
            topBar = {
                InsetAwareTopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                id = R.string.article_published_in,
                                formatArgs = arrayOf(state.article.post.publication?.name.orEmpty())
                            ),
                            style = MaterialTheme.typography.subtitle2,
                            color = LocalContentColor.current
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = sendToStore(ArticleViewAction.ExternalNavigateBack)) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_up)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                BottomBar(
                    onUnimplementedAction = sendToStore(ArticleViewAction.ShowFunctionalityNotAvailableDialog),
                    isFavorite = state.article.favorite,
                    onToggleFavorite = sendToStore(ArticleViewAction.ToggleFavorite(state.article.post.id)),
                    onSharePost = sendToStore(ArticleViewAction.ExternalSharePost(state.article.post.title, state.article.post.url))
                )
            }
        ) { innerPadding ->
            PostContent(
                post = state.article.post,
                modifier = Modifier
                    // innerPadding takes into account the top and bottom bar
                    .padding(innerPadding)
                    // offset content in landscape mode to account for the navigation bar
                    .navigationBarsPadding(bottom = false)
                    // center content in landscape mode
                    .supportWideScreen()
            )
        }
    }
}

/**
 * Bottom bar for Article screen
 *
 * @param post (state) used in share sheet to share the post
 * @param onUnimplementedAction (event) called when the user performs an unimplemented action
 * @param isFavorite (state) if this post is currently a favorite
 * @param onToggleFavorite (event) request this post toggle it's favorite status
 */
@Composable
private fun BottomBar(
    onUnimplementedAction: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSharePost:() -> Unit
) {
    Surface(elevation = 8.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .navigationBarsPadding()
                .height(56.dp)
                .fillMaxWidth()
        ) {
            IconButton(onClick = onUnimplementedAction) {
                Icon(
                    imageVector = Icons.Filled.ThumbUpOffAlt,
                    contentDescription = stringResource(R.string.cd_add_to_favorites)
                )
            }
            BookmarkButton(
                isBookmarked = isFavorite,
                onClick = onToggleFavorite
            )
            IconButton(onClick = onSharePost) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.cd_share)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onUnimplementedAction) {
                Icon(
                    painter = painterResource(R.drawable.ic_text_settings),
                    contentDescription = stringResource(R.string.cd_text_settings)
                )
            }
        }
    }
}

/**
 * Display a popup explaining functionality not available.
 *
 * @param onDismiss (event) request the popup be dismissed
 */
@Composable
private fun FunctionalityNotAvailablePopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(id = R.string.article_functionality_not_available),
                style = MaterialTheme.typography.body2
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.close))
            }
        }
    )
}

//@Preview("Article screen")
//@Preview("Article screen (dark)", uiMode = UI_MODE_NIGHT_YES)
//@Preview("Article screen (big font)", fontScale = 1.5f)
//@Preview("Article screen (large screen)", device = Devices.PIXEL_C)
//@Composable
//fun PreviewArticle() {
//    JetnewsTheme {
//        val post = runBlocking {
//            (BlockingFakePostsRepository().getPost(post3.id) as Result.Success).data
//        }
//        ArticleScreen(post, {}, false, {})
//    }
//}
