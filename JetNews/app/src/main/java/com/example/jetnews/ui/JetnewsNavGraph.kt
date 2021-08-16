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
import com.example.jetnews.data.AppContainer
import com.example.jetnews.data.Result
import com.example.jetnews.data.succeeded
import com.example.jetnews.framework.Store
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

@Composable
fun JetnewsNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController(),
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    startDestination: String = MainDestinations.HOME_ROUTE
) {
    val actions = remember(navController) { MainActions(navController) }
    val coroutineScope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { coroutineScope.launch { scaffoldState.drawerState.open() } }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(MainDestinations.HOME_ROUTE) {
            HomeScreen(
                Store.of(
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
                    },postsRepository = appContainer.postsRepository)
                )
            )
        }
        composable(MainDestinations.INTERESTS_ROUTE) {
            InterestsScreen(
                Store.of(
                    state = InterestsScreenState(
                        topicListState = LoadedStatus.NotLoaded,
                        peopleListState = LoadedStatus.NotLoaded,
                        publicationListState = LoadedStatus.NotLoaded,
                        selectedTopics = emptySet(),
                        selectedPeople = emptySet(),
                        selectedPublications = emptySet(),
                        currentTab = Sections.Topics
                    ),
                    reducer = ComposedInterestScreenReducer,
                    environment = InterestScreenEnvironment(appContainer.interestsRepository, { flowOf(Unit).map { openDrawer() }.map { Unit } })
                )
            )
        }
        composable("${MainDestinations.ARTICLE_ROUTE}/{$ARTICLE_ID_KEY}") { backStackEntry ->
            ArticleScreen(
                Store.of(
                    state = ArticleScreenState(
                        article = ArticleStatus.NotLoadedFor(backStackEntry.arguments?.getString(ARTICLE_ID_KEY) ?: ""),
                        dialogShown = false
                    ),
                    reducer = ComposedArticleScreenReducer(),
                    environment = ArticleScreenEnvironment(
                        getPost = { articleId ->
                            flow {
                                val post = appContainer.postsRepository.getPost(articleId)
                                if (post is Result.Success){
                                    emit(post.data)
                                }
                                if (post is Result.Error){
                                    throw post.exception
                                }
                            }
                        },

                        favouritePosts = {
                            flow {
                                val favorites = appContainer.postsRepository.observeFavorites().take(1).first()
                                emit(favorites)
                            }
                        },

                        toggleFavorite = { articleId ->
                            flow {
                                appContainer.postsRepository.toggleFavorite(articleId)
                                val favorites = appContainer.postsRepository.observeFavorites().take(1).first()
                                emit(favorites)
                            }
                        }
                    )
                )
            )
        }
    }
}

/**
 * Models the navigation actions in the app.
 */
class MainActions(navController: NavHostController) {
    val navigateToArticle: (String) -> Unit = { postId: String ->
        navController.navigate(postId)
    }
    val upPress: () -> Unit = {
        navController.navigateUp()
    }
}
