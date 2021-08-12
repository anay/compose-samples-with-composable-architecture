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

package com.example.jetnews.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.*
import arrow.optics.Lens
import arrow.optics.Optional
import arrow.optics.dsl.at
import arrow.optics.optics
import arrow.optics.typeclasses.At
import arrow.optics.typeclasses.Index
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.posts.PostsRepository
import com.example.jetnews.data.posts.impl.BlockingFakePostsRepository
import com.example.jetnews.framework.*
import com.example.jetnews.model.Post
import com.example.jetnews.ui.MainDestinations
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.state.UiState
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.produceUiState
import com.example.jetnews.utils.supportWideScreen
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Stateful HomeScreen which manages state using [produceUiState]
 *
 * @param postsRepository data source for this screen
 * @param navigateToArticle (event) request navigation to Article screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for the [Scaffold] component on this screen
 */

@optics
data class Address(
    val pincode:String
){
    companion object
}

@optics
data class Person(
    val address: Address
){
    companion object
}

@Composable
fun HomeScreen(
    postsRepository: PostsRepository,
    navigateToArticle: (String) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState = rememberScaffoldState()
) {

    val store = Store.of(
        state = HomeScreenState(
            posts = PostStatus.NotLoaded,
            currentScreen = MainDestinations.HOME_ROUTE,
            historyDialogOpenStatus = HistoryPostDialogStatus.NotOpen
        ),
        reducer = ComposedHomeScreenReducer(),
        environment = HomeScreenEnvironment(openDrawer = {
            flow{
                openDrawer()
                emit(Unit)
            }
        },postsRepository = postsRepository)
    )

    StoreView(store) { state ->

        if (state.currentScreen != MainDestinations.HOME_ROUTE){
            navigateToArticle(state.currentScreen)
            sendToStore(HomeScreenAction.StayOnHomeTemp)()
        }

        if (state.posts is PostStatus.NotLoaded){
            sendToStore(HomeScreenAction.LoadPost)()
            return@StoreView
        }

        if (state.posts is PostStatus.Loading){
            FullScreenLoading()
            return@StoreView
        }

        if (state.posts is PostStatus.Error) {
            val errorMessage = stringResource(id = R.string.load_error)
            val retryMessage = stringResource(id = R.string.retry)

            FullScreenLoading()

            // Show snackbar using a coroutine, when the coroutine is cancelled the snackbar will
            // automatically dismiss. This coroutine will cancel whenever posts.hasError is false
            // (thanks to the surrounding if statement) or if scaffoldState.snackbarHostState changes.
            sendToStore(HomeScreenAction.LoadPost)()
            LaunchedEffect(scaffoldState.snackbarHostState) {
                val snackbarResult = scaffoldState.snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = retryMessage
                )

//                when (snackbarResult) {
//                    SnackbarResult.ActionPerformed -> sendToStore(HomeScreenAction.LoadPost)()
//                    SnackbarResult.Dismissed -> sendToStore(HomeScreenAction.DismissError)()
//                }
            }
        }

        if (state.posts !is PostStatus.Loaded){
            return@StoreView
        }

        Scaffold(
            scaffoldState = scaffoldState,
            topBar = {
                val title = stringResource(id = R.string.app_name)
                InsetAwareTopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = sendToStore(HomeScreenAction.OpenDrawer)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_jetnews_logo),
                                contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            val modifier = Modifier.padding(innerPadding)

            SwipeRefresh(
                state = rememberSwipeRefreshState(state.posts is PostStatus.Loading),
                onRefresh = sendToStore(HomeScreenAction.LoadPost),
            ){

                HomeScreenState.postListState.getOrNull(state)?.let { postListState ->
                    PostList(store.forView(
                        appState = state,
                        stateBuilder = {postListState},
                        actionMapper = {
                            HomeScreenAction.PostListActions(it)
                        }
                    ))
                } ?:

                if (state.posts.posts.isEmpty()){
                    TextButton(onClick = sendToStore(HomeScreenAction.LoadPost), modifier.fillMaxSize()) {
                        Text(stringResource(id = R.string.home_tap_to_load_content), textAlign = TextAlign.Center)
                    }
                }
            }
        }

    }
}


sealed class HistoryPostDialogStatus{
    data class OpenedFor(val id:String):HistoryPostDialogStatus()
    object NotOpen:HistoryPostDialogStatus()

    fun isOpenFor(id:String):Boolean = when(this){
        NotOpen -> false
        is OpenedFor -> this.id == id
    }
}

@optics
data class PostListState(
    val posts:List<PostState>,
    val historyDialogOpenStatus: HistoryPostDialogStatus
){
    companion object{

        val postListTopSectionState:Lens<PostListState, PostListTopSectionState> = Lens(
            get = { PostListTopSectionState(post = it.posts.get(3)) },
            set = { state, sectionState ->
                val posts = Index.list<PostState>().index(3).set(state.posts, sectionState.post)
                state.copy(posts = posts)
            }
        )

        val postListSimpleItems:Lens<PostListState, Map<String, PostState>> = Lens(
            get = { state -> (0..1).map { state.posts[it] }.map { it.post.id to it }.toMap() },
            set = { state, map -> PostListState.posts.set(
                source = state,
                focus = state.posts.map { postState -> map.getOrElse(postState.post.id, { postState }) }
            ) }
        )

        val postListPopularItems:Lens<PostListState, Map<String, PostState>> = Lens(
            get = { state -> (2..6).map { state.posts[it] }.map { it.post.id to it }.toMap() },
            set = { state, map -> PostListState.posts.set(
                source = state,
                focus = state.posts.map { postState -> map.getOrElse(postState.post.id, { postState }) }
            ) }
        )

        val postListHistoryItems:Lens<PostListState, Map<String, PostCardHistoryState>> = Lens(
            get = { state -> (7..9).map { state.posts[it] }.map { it.post.id to PostCardHistoryState(
                post = it,
                openDialog = state.historyDialogOpenStatus.isOpenFor(it.post.id)
            ) }.toMap() },
            set = { state, map -> PostListState.posts.set(
                source = state,
                focus = state.posts.map { postState -> map.get(postState.post.id)?.post ?: postState }
            ).copy(historyDialogOpenStatus = map.values.find { it.openDialog }?.let { HistoryPostDialogStatus.OpenedFor(it.post.post.id) } ?: HistoryPostDialogStatus.NotOpen) }
        )

    }

}

@optics
sealed class PostListAction{

    companion object{

    }

    @optics data class PostListTopSectionActions(val action:PostListTopSectionAction):PostListAction(){
        companion object
    }
    @optics data class PostListSimpleSectionActions(val action:Pair<String, PostCardSimpleAction>):PostListAction(){
        companion object
    }
    @optics data class PostListPopularSectionActions(val action:Pair<String, PostCardPopularAction>):PostListAction(){
        companion object
    }
    @optics data class PostListHistorySectionActions(val action:Pair<String, PostCardHistoryAction>):PostListAction(){
        companion object
    }

    data class ExternalNavigateTo(val path:String):PostListAction()

}

fun PostListReducer():Reducer<PostListState, PostListAction, HomeScreenEnvironment> =
    combine(
        PostCardHistoryReducer.forEach(
            states = PostListState.postListHistoryItems,
            actionMapper = PostListAction.postListHistorySectionActions.action,
            environmentMapper = {}
        ),
        PostCardPopularReducer.forEach(
            states = PostListState.postListPopularItems,
            actionMapper = PostListAction.postListPopularSectionActions.action,
            environmentMapper = {
                Unit
            }
        ),
        PostCardSimpleReducer.forEach(
            states = PostListState.postListSimpleItems,
            environmentMapper = { env ->
                PostCardSimpleEnvironment(
                    onToggleFavorite = env::toggleFavorite
                )
            },
            actionMapper = PostListAction.postListSimpleSectionActions.action
        ),
        PostListTopSectionReducer().pullback(
            stateMapper = PostListState.postListTopSectionState,
            actionMapper = PostListAction.postListTopSectionActions.action,
            environmentMapper = { Unit }
        ),
        {
            state, action,_,_ ->
            when{
                action is PostListAction.PostListTopSectionActions && action.action is PostListTopSectionAction.ExternalNavigateTo ->
                    state to flowOf(PostListAction.ExternalNavigateTo(action.action.id))
                action is PostListAction.PostListSimpleSectionActions && action.action.second is PostCardSimpleAction.ExternalNavigateTo ->
                    state to flowOf(PostListAction.ExternalNavigateTo(action.action.first))
                action is PostListAction.PostListPopularSectionActions && action.action.second is PostCardPopularAction.ExternalNavigateTo ->
                    state to flowOf(PostListAction.ExternalNavigateTo(action.action.first))
                action is PostListAction.PostListHistorySectionActions && action.action.second is PostCardHistoryAction.ExternalNavigateTo ->
                    state to flowOf(PostListAction.ExternalNavigateTo(action.action.first))
                else -> state to emptyFlow()
            }
        }
    )

/**
 * Display a list of posts.
 *
 * When a post is clicked on, [navigateToArticle] will be called to navigate to the detail screen
 * for that post.
 *
 * @param posts (state) the list to display
 * @param navigateToArticle (event) request navigation to Article screen
 * @param modifier modifier for the root element
 */
@Composable
private fun PostList(
    store:Store<PostListState, PostListAction>,
    modifier: Modifier = Modifier
) {
//    val postsPopular = posts.subList(2, 7)
//    val postsHistory = posts.subList(7, 10)


    StoreView(store) { state ->
        LazyColumn(
            modifier = modifier,
            contentPadding = rememberInsetsPaddingValues(
                insets = LocalWindowInsets.current.systemBars,
                applyTop = false
            )
        ) {
            item {
                PostListTopSection(store.forView(
                    appState = state,
                    stateBuilder = PostListState.postListTopSectionState::get,
                    actionMapper = {
                        PostListAction.PostListTopSectionActions(it)
                    }
                ))
            }
        item { PostListSimpleSection(store) }
        item { PostListPopularSection(store) }
        item { PostListHistorySection(store) }
        }
    }

}

/**
 * Full screen circular progress indicator
 */
@Composable
private fun FullScreenLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Top section of [PostList]
 *
 * @param post (state) highlighted post to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListTopSection(store:Store<PostListTopSectionState, PostListTopSectionAction>) {
    StoreView(store) { state ->
        Text(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            text = stringResource(id = R.string.home_top_section_title),
            style = MaterialTheme.typography.subtitle1
        )
        PostCardTop(
            post = state.post,
            modifier = Modifier.clickable(onClick = { sendToStore(PostListTopSectionAction.Clicked(state.post.post.id))() })
        )
        PostListDivider()
    }
}

data class PostListTopSectionState(val post:PostState)

sealed class PostListTopSectionAction{
    data class Clicked(val id: String):PostListTopSectionAction()
    data class ExternalNavigateTo(val id:String):PostListTopSectionAction()
}

fun PostListTopSectionReducer():Reducer<PostListTopSectionState, PostListTopSectionAction, Unit> = {
    state, action, env, _ ->
    when(action){

        is PostListTopSectionAction.Clicked -> Pair(
            state,
            flowOf(PostListTopSectionAction.ExternalNavigateTo(action.id))
        )

        is PostListTopSectionAction.ExternalNavigateTo -> state to emptyFlow()
    }
}

/**
 * Full-width list items for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */

@Composable
private fun PostListSimpleSection(
    store:Store<PostListState, PostListAction>
) {

    StoreView(store) { state ->
        Column {
            store.forStates<PostState, PostCardSimpleAction, String>(
                appState = state,
                states = { PostListState.postListSimpleItems.get(it) },
                actionMapper = {id, action-> PostListAction.PostListSimpleSectionActions(id to action) }
            ){ viewStore ->
                PostCardSimple(viewStore)
            }
        }
    }

}


@optics
data class PostListPopularSectionState(
    val posts:Map<String, PostState>
){
    companion object
}

@optics
sealed class PostListPopularSectionAction{
    companion object
    @optics data class PostCardPopularActions(val value:Pair<String, PostCardPopularAction>):PostListPopularSectionAction(){
        companion object
    }

    @optics
    data class ExternalNavigateTo(val id:String):PostListPopularSectionAction(){
        companion object
    }
}


/**
 * Horizontal scrolling cards for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListPopularSection(
    store:Store<PostListState, PostListAction>
) {
    StoreView(store) { state ->
        Column {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(id = R.string.home_popular_section_title),
                style = MaterialTheme.typography.subtitle1
            )

            LazyRow(modifier = Modifier.padding(end = 16.dp)) {
                items(PostListState.postListPopularItems.get(state).values.toList()) { post ->

                    PostCardPopular(
                        store.forView(
                            appState = state,
                            stateBuilder = { post },
                            actionMapper = { PostListAction.PostListPopularSectionActions(post.post.id to it) }
                        ),
                        Modifier.padding(start = 16.dp, bottom = 16.dp)
                    )
                }
            }
            PostListDivider()
        }
    }


}

@optics
data class PostListHistorySectionState(
    val posts:Map<String, PostCardHistoryState>
){
    companion object
}

@optics
sealed class PostListHistorySectionAction{
    companion object
    @optics
    data class PostCardHistoryActions(val value:Pair<String, PostCardHistoryAction>):PostListHistorySectionAction(){
        companion object
    }
}

/**
 * Full-width list items that display "based on your history" for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListHistorySection(
    store:Store<PostListState, PostListAction>
) {

    StoreView(store) { state ->
        Column {

            store.forStates<PostCardHistoryState, PostCardHistoryAction, String>(
                appState = state,
                states = { PostListState.postListHistoryItems.get(it) },
                actionMapper = {id, action -> PostListAction.PostListHistorySectionActions(id to action)}
            ) { viewStore ->
                PostCardHistory(viewStore)
                PostListDivider()
            }
        }
    }
}

/**
 * Full-width divider with padding for [PostList]
 */
@Composable
private fun PostListDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    )
}

//@Preview("Home screen")
//@Preview("Home screen (dark)", uiMode = UI_MODE_NIGHT_YES)
//@Preview("Home screen (big font)", fontScale = 1.5f)
//@Preview("Home screen (large screen)", device = Devices.PIXEL_C)
//@Composable
//fun PreviewHomeScreen() {
//    val posts = runBlocking {
//        (BlockingFakePostsRepository().getPosts() as Result.Success).data
//    }
//    JetnewsTheme {
//        HomeScreen(
//            posts = UiState(data = posts),
//            favorites = setOf(),
//            onToggleFavorite = { /*TODO*/ },
//            onRefreshPosts = { /*TODO*/ },
//            onErrorDismiss = { /*TODO*/ },
//            navigateToArticle = { /*TODO*/ },
//            openDrawer = { /*TODO*/ },
//            scaffoldState = rememberScaffoldState()
//        )
//    }
//}


sealed class PostStatus{
    data class Loaded(val posts:List<PostState>):PostStatus()
    object NotLoaded:PostStatus()
    object Loading:PostStatus()
    data class Error(val exception:Throwable):PostStatus()
    object ErrorDismissed:PostStatus()
}

@optics
data class PostState(
    val favorite:Boolean,
    val post:Post
){
    companion object
}

data class HomeScreenState(
    val posts:PostStatus,
    val historyDialogOpenStatus: HistoryPostDialogStatus,
    val currentScreen: String
){
    companion object{
        val postListState:Optional<HomeScreenState, PostListState> = Optional(
            getOption = { if (it.posts is PostStatus.Loaded) PostListState(it.posts.posts, it.historyDialogOpenStatus).some() else none() },
            set = { state, listState -> state.copy(posts = PostStatus.Loaded(listState.posts), historyDialogOpenStatus = listState.historyDialogOpenStatus) }
        )
    }
}

@optics
sealed class HomeScreenAction{
    companion object
    object OpenDrawer:HomeScreenAction()
    object ExternalOpenDrawer:HomeScreenAction()
    object LoadPost:HomeScreenAction()
    data class PostLoaded(val posts:List<Post>, val favourites:Set<String>):HomeScreenAction()
    data class PostError(val exception:Throwable):HomeScreenAction()
    data class NavigateTo(val path:String):HomeScreenAction()
    data class ExternalNavigateTo(val path:String):HomeScreenAction()
    object StayOnHomeTemp:HomeScreenAction()
    object DismissError:HomeScreenAction()

    @optics data class PostListActions(val action:PostListAction):HomeScreenAction(){
        companion object
    }
}

fun ComposedHomeScreenReducer():Reducer<HomeScreenState, HomeScreenAction, HomeScreenEnvironment> =
    combine(
        PostListReducer().pullbackOptional(
            stateMapper = HomeScreenState.postListState,
            actionMapper = HomeScreenAction.postListActions.action,
            environmentMapper = { it }
        ),
        {
            state, action, _, _ ->
            when{
                action is HomeScreenAction.PostListActions && action.action is PostListAction.ExternalNavigateTo ->
                    state to flowOf(HomeScreenAction.ExternalNavigateTo(action.action.path))
                else -> state to emptyFlow()
            }
        },
        HomeScreenReducer(),
    )

fun HomeScreenReducer():Reducer<HomeScreenState, HomeScreenAction, HomeScreenEnvironment> = {state, action, env, scope ->
    when(action){
        HomeScreenAction.OpenDrawer -> Pair(
            state,
            flowOf(HomeScreenAction.ExternalOpenDrawer)
        )


        HomeScreenAction.LoadPost -> state.copy(posts = PostStatus.Loading) to env
            .getPosts()
            .combine(env.favouritePosts()){ posts, favorites ->
                Pair(posts, favorites)
            }.flowOn(Dispatchers.IO)
            .map {
                Either.Right(it) as Either<Throwable, Pair<List<Post>, Set<String>>>
            }
            .catch { emit(Either.Left(it)) }
            .map {
                it.fold(
                    ifLeft = { HomeScreenAction.PostError(it) },
                    ifRight = { HomeScreenAction.PostLoaded(it.first, it.second) }
                )
            }
        is HomeScreenAction.PostLoaded -> action.posts.map { post ->
            PostState(favorite = action.favourites.contains(post.id), post = post)
        }.let {
            state.copy(posts = PostStatus.Loaded(it)) to emptyFlow()
        }

        is HomeScreenAction.PostError -> state.copy(posts = PostStatus.Error(action.exception)) to emptyFlow()

        HomeScreenAction.DismissError -> state.copy(posts = PostStatus.ErrorDismissed) to emptyFlow()

        is HomeScreenAction.NavigateTo ->
            state to flowOf(HomeScreenAction.ExternalNavigateTo(action.path))

        is HomeScreenAction.ExternalNavigateTo ->
            state.copy(currentScreen = "${MainDestinations.ARTICLE_ROUTE}/${action.path}") to emptyFlow()

        HomeScreenAction.StayOnHomeTemp ->
            state.copy(currentScreen = "home") to emptyFlow()

        else -> state to emptyFlow()
    }
}

class HomeScreenEnvironment(
    val openDrawer:() -> Flow<Unit>,
    private val postsRepository: PostsRepository
){
    fun getPost(postId:String) = flow{
        val post = postsRepository.getPost(postId)
        when(post){
            is Result.Success -> emit(post.data)
            is Result.Error -> throw post.exception
        }
    }

    fun getPosts() = flow{
        val posts = postsRepository.getPosts()
        when(posts){
            is Result.Success -> emit(posts.data)
            is Result.Error -> throw posts.exception
        }
    }

    fun favouritePosts() = postsRepository.observeFavorites()
        .take(1)

    fun toggleFavorite(postId: String) = flow {
        postsRepository.toggleFavorite(postId)
        val favorites = favouritePosts().first()
        emit(favorites)
    }
}