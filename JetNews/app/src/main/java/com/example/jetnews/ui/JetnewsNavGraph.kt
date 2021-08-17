/*
 * Copyright 2021 The Android Open Source Project
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

package com.example.jetnews.ui

import android.net.Uri
import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import arrow.optics.Lens
import arrow.optics.optics
import com.example.jetnews.data.AppContainer
import com.example.jetnews.data.Result
import com.example.jetnews.data.interests.TopicSelection
import com.example.jetnews.data.interests.TopicsMap
import com.example.jetnews.data.succeeded
import com.example.jetnews.framework.Reducer
import com.example.jetnews.framework.Store
import com.example.jetnews.framework.StoreView
import com.example.jetnews.framework.pullback
import com.example.jetnews.model.Post
import com.example.jetnews.ui.MainDestinations.ARTICLE_ID_KEY
import com.example.jetnews.ui.article.*
import com.example.jetnews.ui.home.*
import com.example.jetnews.ui.interests.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Destinations used in the ([JetnewsApp]).
 */
object MainDestinations {
    const val HOME_ROUTE = "home"
    const val INTERESTS_ROUTE = "interests"
    const val ARTICLE_ROUTE = "post"
    const val ARTICLE_ID_KEY = "postId"
}

data class JetNewsState(
    val currentScreen:String,
    val posts:PostStatus,
    val historyDialogOpenStatus:HistoryPostDialogStatus,
    val interestsScreenState: InterestsScreenState,
    val articleScreenState: ArticleScreenState
){
    companion object{

        val interestsScreenState:Lens<JetNewsState, InterestsScreenState> = Lens(
            get = {it.interestsScreenState},
            set = { state, childState -> state.copy(interestsScreenState = childState) }
        )

        val articleScreenState:Lens<JetNewsState, ArticleScreenState> = Lens(
            get = {it.articleScreenState},
            set = { state, childState -> state.copy(articleScreenState = childState) }
        )

        val homeScreenState:Lens<JetNewsState, HomeScreenState> = Lens(
            get = {
                HomeScreenState(
                    posts = it.posts,
                    historyDialogOpenStatus = it.historyDialogOpenStatus,
                    currentScreen = it.currentScreen
                )
            },
            set = { newsState, homeScreenState ->
                newsState.copy(
                    currentScreen = homeScreenState.currentScreen,
                    posts = homeScreenState.posts,
                    historyDialogOpenStatus = homeScreenState.historyDialogOpenStatus
                )
            }
        )
    }
}

@optics
sealed class JetNewsAction{
    companion object

    @optics
    data class HomeScreenActions(val action:HomeScreenAction):JetNewsAction(){
        companion object
    }

    @optics
    data class InterestScreenActions(val action:InterestsScreenAction):JetNewsAction(){
        companion object
    }

    @optics
    data class ArticleScreenActions(val action:ArticleScreenAction):JetNewsAction(){
        companion object
    }

    @optics
    data class ExternalNavigateTo(val path:String):JetNewsAction(){
        companion object
    }

    object ExternalOpenDrawer:JetNewsAction()

    object ExternalNavigateBack:JetNewsAction()

    @optics
    data class ExternalSharePost(val title:String, val url:String):JetNewsAction(){
        companion object
    }

}

class JetNewsEnvironment(
    val getPost:(String) -> Flow<Post>,
    val getPosts:() -> Flow<List<Post>>,
    val favouritePosts:() -> Flow<Set<String>>,
    val toggleFavorite:(String) -> Flow<Set<String>>,
    val getPeople:() -> Flow<List<String>>,
    val getPublication:() -> Flow<List<String>>,
    val getTopics: () -> Flow<TopicsMap>,
    val getSelectedTopics:() -> Flow<Set<TopicSelection>>,
    val toggleTopic:(TopicSelection) -> Flow<Set<TopicSelection>>,
    val getSelectedPeople:() -> Flow<Set<String>>,
    val togglePerson:(String) -> Flow<Set<String>>,
    val getSelectedPublications:() -> Flow<Set<String>>,
    val togglePublications:(String) -> Flow<Set<String>>
)


fun ComposedJetNewsReducer():Reducer<JetNewsState, JetNewsAction, JetNewsEnvironment> =
    com.example.jetnews.framework.combine(
        ComposedHomeScreenReducer().pullback(
            stateMapper = JetNewsState.homeScreenState,
            actionMapper = JetNewsAction.homeScreenActions.action,
            environmentMapper = {
                HomeScreenEnvironment(
                    getPost = it.getPost,
                    getPosts = it.getPosts,
                    favouritePosts = it.favouritePosts,
                    toggleFavorite = it.toggleFavorite
                )
            }
        ),

        ComposedInterestScreenReducer.pullback(
            stateMapper = JetNewsState.interestsScreenState,
            actionMapper = JetNewsAction.interestScreenActions.action,
            environmentMapper = {
                InterestScreenEnvironment(
                    getPeople = it.getPeople,
                    getPublication = it.getPublication,
                    getTopics = it.getTopics,
                    getSelectedTopics = it.getSelectedTopics,
                    toggleTopic = it.toggleTopic,
                    getSelectedPeople = it.getSelectedPeople,
                    togglePerson = it.togglePerson,
                    getSelectedPublications = it.getSelectedPublications,
                    togglePublications = it.togglePublications
                )
            }
        ),

        ComposedArticleScreenReducer().pullback(
            stateMapper = JetNewsState.articleScreenState,
            actionMapper = JetNewsAction.articleScreenActions.action,
            environmentMapper = {
                ArticleScreenEnvironment(
                    getPost = it.getPost,
                    favouritePosts = it.favouritePosts,
                    toggleFavorite = it.toggleFavorite
                )
            }
        ),

        JetNewsReducer()
    )

fun JetNewsReducer():Reducer<JetNewsState, JetNewsAction, JetNewsEnvironment> = {
    state, action, env, _ ->
    when{

        action is JetNewsAction.HomeScreenActions &&
                action.action is HomeScreenAction.ExternalNavigateTo ->
            state to flowOf(JetNewsAction.ExternalNavigateTo(action.action.path))

        action is JetNewsAction.HomeScreenActions &&
                action.action is HomeScreenAction.ExternalOpenDrawer ->
            state to flowOf(JetNewsAction.ExternalOpenDrawer)

        action is JetNewsAction.ArticleScreenActions &&
                action.action is ArticleScreenAction.ExternalNavigateBack ->
            state to flowOf(JetNewsAction.ExternalNavigateBack)

        action is JetNewsAction.ArticleScreenActions &&
                action.action is ArticleScreenAction.ExternalShare ->
            state to flowOf(JetNewsAction.ExternalSharePost(action.action.title, action.action.url))

        action is JetNewsAction.InterestScreenActions &&
                action.action is InterestsScreenAction.OpenDrawer ->
            state to flowOf(JetNewsAction.ExternalOpenDrawer)

        else -> state to emptyFlow()
    }
}


@Composable
fun JetnewsNavGraph(
    store:Store<JetNewsState, JetNewsAction>,
    navController: NavHostController = rememberNavController(),
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    startDestination: String = MainDestinations.HOME_ROUTE
) {

    StoreView(store) { state ->
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(MainDestinations.HOME_ROUTE) {
                HomeScreen(
                    store.forView(
                        appState = state,
                        stateBuilder = { JetNewsState.homeScreenState.get(it) },
                        actionMapper = { JetNewsAction.HomeScreenActions(it) }
                    )
                )
            }
            composable(MainDestinations.INTERESTS_ROUTE) {
                InterestsScreen(
                    store.forView(
                        appState = state,
                        stateBuilder = { it.interestsScreenState },
                        actionMapper = { JetNewsAction.InterestScreenActions(it) }
                    )
                )
            }
            composable("${MainDestinations.ARTICLE_ROUTE}/{$ARTICLE_ID_KEY}") { backStackEntry ->
                ArticleScreen(
                    store.forView(
                        appState = state,
                        stateBuilder = { it.articleScreenState },
                        actionMapper = { JetNewsAction.ArticleScreenActions(it) }
                    )
                )
            }
        }
    }
}
