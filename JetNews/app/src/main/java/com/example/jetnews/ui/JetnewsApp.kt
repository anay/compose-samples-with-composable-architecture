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

package com.example.jetnews.ui

import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import arrow.core.none
import arrow.core.some
import arrow.optics.Lens
import arrow.optics.Optional
import arrow.optics.PLens
import arrow.optics.optics
import com.example.jetnews.JetnewsApplication
import com.example.jetnews.data.AppContainer
import com.example.jetnews.framework.*
import com.example.jetnews.ui.article.ArticleScreenState
import com.example.jetnews.ui.article.ArticleStatus
import com.example.jetnews.ui.article.article
import com.example.jetnews.ui.home.HistoryPostDialogStatus
import com.example.jetnews.ui.home.HomeScreenState
import com.example.jetnews.ui.home.PostStatus
import com.example.jetnews.ui.interests.InterestsScreenState
import com.example.jetnews.ui.theme.JetnewsTheme
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

sealed class ShareOption{
    object NoShare:ShareOption()
    class Share(val title:String, val url:String):ShareOption()
}

sealed class CurrentScreen{
    data class Is(val path:String):CurrentScreen()
    object NavigateBack:CurrentScreen()
}

@optics
data class JetNewsAppState(
    val currentScreen:CurrentScreen,
    val posts: PostStatus,
    val historyDialogOpenStatus: HistoryPostDialogStatus,
    val interestsScreenState: InterestsScreenState,
    val articleScreenState: ArticleScreenState,
    val drawerOpened:Boolean,
    val share:ShareOption
){
    companion object{

        val jetNewsState:Optional<JetNewsAppState, JetNewsState> = Optional(
            getOption = {
                when(it.currentScreen){
                    is CurrentScreen.Is -> JetNewsState(
                        currentScreen = it.currentScreen.path,
                        posts = it.posts,
                        historyDialogOpenStatus = it.historyDialogOpenStatus,
                        interestsScreenState = it.interestsScreenState,
                        articleScreenState = it.articleScreenState
                    ).some()
                    CurrentScreen.NavigateBack -> none()
                }
            },
            set = { state, childState ->
                state.copy(
                    currentScreen = CurrentScreen.Is(childState.currentScreen),
                    posts = childState.posts,
                    historyDialogOpenStatus = childState.historyDialogOpenStatus,
                    interestsScreenState = childState.interestsScreenState,
                    articleScreenState = childState.articleScreenState
                )
            }
        )

    }
}

@optics
sealed class JetNewsAppAction{
    companion object

    @optics
    data class JetNewActions(val action:JetNewsAction):JetNewsAppAction(){
        companion object
    }

    @optics
    data class NavigateTo(val path:String):JetNewsAppAction(){
        companion object
    }

    object OpenDrawer:JetNewsAppAction()

    object CloseDrawer:JetNewsAppAction()

    @optics data class StartShare(val title:String, val url:String):JetNewsAppAction(){
        companion object
    }

    object NavigateBack:JetNewsAppAction()

    object ShareOptionStarted:JetNewsAppAction()
}

fun ComposedJetNewsAppReducer():Reducer<JetNewsAppState, JetNewsAppAction, JetNewsEnvironment> =
    combine(
        ComposedJetNewsReducer().pullbackOptional(
            stateMapper = JetNewsAppState.jetNewsState,
            actionMapper = JetNewsAppAction.jetNewActions.action,
            environmentMapper = { it }
        ),
        JetNewsAppReducer(),
        {
            state, action, _, _ ->
            when{

                action is JetNewsAppAction.JetNewActions &&
                        action.action is JetNewsAction.ExternalSharePost ->
                    state to flowOf(JetNewsAppAction.StartShare(action.action.title, action.action.url))

                action is JetNewsAppAction.JetNewActions &&
                        action.action is JetNewsAction.ExternalNavigateBack ->
                    state to flowOf(JetNewsAppAction.NavigateBack)

                action is JetNewsAppAction.JetNewActions &&
                        action.action is JetNewsAction.ExternalOpenDrawer ->
                    state to flowOf(JetNewsAppAction.OpenDrawer)

                action is JetNewsAppAction.JetNewActions &&
                        action.action is JetNewsAction.ExternalNavigateTo ->
                    state to flowOf(JetNewsAppAction.NavigateTo(path = action.action.path))

                else -> state to emptyFlow()

            }
        }
    )

fun JetNewsAppReducer():Reducer<JetNewsAppState, JetNewsAppAction, JetNewsEnvironment> = {
    state, action, env, _ ->
    when(action){
        is JetNewsAppAction.JetNewActions -> state to emptyFlow()
        is JetNewsAppAction.NavigateTo ->
            when{
                action.path == "home" -> state.copy(posts = PostStatus.NotLoaded)
                action.path.contains("post/") -> JetNewsAppState.jetNewsState.articleScreenState.article.set(
                    state,
                    ArticleStatus.NotLoadedFor(action.path.replace("post/",""))
                )
                else -> state
            }.copy(currentScreen = CurrentScreen.Is(action.path)) to emptyFlow()
        is JetNewsAppAction.OpenDrawer -> state.copy(drawerOpened = true) to emptyFlow()
        is JetNewsAppAction.CloseDrawer -> state.copy(drawerOpened = false) to emptyFlow()
        is JetNewsAppAction.StartShare -> state.copy(share = ShareOption.Share(action.title, action.url)) to emptyFlow()
        JetNewsAppAction.ShareOptionStarted -> state.copy(share = ShareOption.NoShare) to emptyFlow()
        JetNewsAppAction.NavigateBack -> state.copy(currentScreen = CurrentScreen.NavigateBack) to emptyFlow()
    }
}

@Composable
fun JetnewsApp(
    store: Store<JetNewsAppState, JetNewsAppAction>
) {
    JetnewsTheme {
        ProvideWindowInsets {
            val systemUiController = rememberSystemUiController()
            SideEffect {
                systemUiController.setSystemBarsColor(Color.Transparent, darkIcons = false)
            }

            val navController = rememberNavController()
            val coroutineScope = rememberCoroutineScope()
            // This top level scaffold contains the app drawer, which needs to be accessible
            // from multiple screens. An event to open the drawer is passed down to each
            // screen that needs it.
            val scaffoldState = rememberScaffoldState()

            val navBackStackEntry by navController.currentBackStackEntryAsState()

            StoreView(store) { state ->
                val currentRoute = navBackStackEntry?.destination?.route

                if (currentRoute != null && state.currentScreen is CurrentScreen.Is && state.currentScreen.path.split("/").first() != currentRoute.split("/").first()){
                    navController.navigate(state.currentScreen.path)
                }


                if (state.currentScreen is CurrentScreen.NavigateBack && currentRoute != null ){
                    navController.navigateUp()
                    navBackStackEntry?.destination?.route?.let {
                        sendToStore(JetNewsAppAction.NavigateTo(it))()
                    }
                }

                if (state.drawerOpened){

                    LaunchedEffect("drawerOpen"){
                        launch { scaffoldState.drawerState.open() }
                    }
                } else {
                    LaunchedEffect("drawerClose"){
                        launch { scaffoldState.drawerState.close() }
                    }
                }

                if (state.currentScreen !is CurrentScreen.Is) return@StoreView

                Scaffold(
                    scaffoldState = scaffoldState,
                    drawerContent = {
                        AppDrawer(
                            currentRoute = state.currentScreen.path,
                            navigateToHome = sendToStore(JetNewsAppAction.NavigateTo(MainDestinations.HOME_ROUTE)),
                            navigateToInterests = sendToStore(JetNewsAppAction.NavigateTo(MainDestinations.INTERESTS_ROUTE)),
                            closeDrawer = sendToStore(JetNewsAppAction.CloseDrawer)
                        )
                    }
                ) {

                    JetNewsAppState.jetNewsState.getOrNull(
                        state
                    )?.let { jetNewsState ->
                        JetnewsNavGraph(
                        store.forView(
                                appState = state,
                                stateBuilder = { jetNewsState },
                                actionMapper = { JetNewsAppAction.JetNewActions(it) }
                            ),
                            navController = navController,
                            scaffoldState = scaffoldState
                        )
                    }
                }
            }
        }
    }
}
