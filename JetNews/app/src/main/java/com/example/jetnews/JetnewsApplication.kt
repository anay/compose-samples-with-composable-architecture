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

package com.example.jetnews

import android.app.Application
import com.example.jetnews.data.AppContainer
import com.example.jetnews.data.AppContainerImpl
import com.example.jetnews.framework.Store
import com.example.jetnews.ui.*
import com.example.jetnews.ui.article.ArticleScreenState
import com.example.jetnews.ui.article.ArticleStatus
import com.example.jetnews.ui.home.HistoryPostDialogStatus
import com.example.jetnews.ui.home.PostState
import com.example.jetnews.ui.home.PostStatus
import com.example.jetnews.ui.interests.InterestsScreenState
import com.example.jetnews.ui.interests.LoadedStatus
import com.example.jetnews.ui.interests.Sections
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class JetnewsApplication : Application() {

    // AppContainer instance used by the rest of classes to obtain dependencies
    lateinit var container: AppContainer

    lateinit var store: Store<JetNewsAppState, JetNewsAppAction>

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
        store = Store.of(
            state = JetNewsAppState(
                currentScreen = CurrentScreen.Is(MainDestinations.HOME_ROUTE),
                posts = PostStatus.NotLoaded,
                historyDialogOpenStatus = HistoryPostDialogStatus.NotOpen,
                interestsScreenState = InterestsScreenState(
                    topicListState = LoadedStatus.NotLoaded,
                    peopleListState = LoadedStatus.NotLoaded,
                    publicationListState = LoadedStatus.NotLoaded,
                    currentTab = Sections.Topics
                ),
                articleScreenState = ArticleScreenState(
                    article = ArticleStatus.NotRequested,
                    dialogShown = false
                ),
                drawerOpened = false,
                share = ShareOption.NoShare
            ),
            reducer = ComposedJetNewsAppReducer(),
            environment = JetNewsEnvironment(
                getPost = { id ->
                    flow{
                        val post = container.postsRepository.getPost(id)
                        when(post){
                            is com.example.jetnews.data.Result.Success -> emit(post.data)
                            is com.example.jetnews.data.Result.Error -> throw post.exception
                        }
                    }
                },

                getPosts = {
                    flow {
                        val posts = container.postsRepository.getPosts()
                        when(posts){
                            is com.example.jetnews.data.Result.Success -> emit(posts.data)
                            is com.example.jetnews.data.Result.Error -> throw posts.exception
                        }
                    }
                },

                favouritePosts = { container.postsRepository.observeFavorites().take(1) },

                toggleFavorite = { id ->

                    flow {
                        container.postsRepository.toggleFavorite(id)
                        emit(Unit)
                    }.flatMapConcat {
                        container.postsRepository.observeFavorites().take(1)
                    }

                },

                getPeople = {
                    flow{
                        val people = container.interestsRepository.getPeople()
                        when(people){
                            is com.example.jetnews.data.Result.Success -> emit(people.data)
                            is com.example.jetnews.data.Result.Error -> throw people.exception
                        }
                    }
                },

                getPublication = {
                    flow{
                        val publication = container.interestsRepository.getPublications()
                        when(publication){
                            is com.example.jetnews.data.Result.Success -> emit(publication.data)
                            is com.example.jetnews.data.Result.Error -> throw publication.exception
                        }
                    }
                },

                getTopics = {
                    flow{
                        val topics = container.interestsRepository.getTopics()
                        when(topics){
                            is com.example.jetnews.data.Result.Success -> emit(topics.data)
                            is com.example.jetnews.data.Result.Error -> throw topics.exception
                        }
                    }
                },

                getSelectedTopics = { container.interestsRepository.observeTopicsSelected().take(1) },

                toggleTopic = { topic ->
                    flow<Unit> {
                        container.interestsRepository.toggleTopicSelection(topic)
                        emit(Unit)
                    }.flatMapConcat {
                        container.interestsRepository.observeTopicsSelected().take(1)
                    }
                },

                getSelectedPeople = { container.interestsRepository.observePeopleSelected().take(1) },

                togglePerson = { id ->
                    flow<Unit> {
                        container.interestsRepository.togglePersonSelected(id)
                        emit(Unit)
                    }.flatMapConcat {
                        container.interestsRepository.observePeopleSelected().take(1)
                    }
                },

                getSelectedPublications = { container.interestsRepository.observePublicationSelected().take(1) },

                togglePublications = { id ->
                    flow<Unit> {
                        container.interestsRepository.togglePublicationSelected(id)
                        emit(Unit)
                    }.flatMapConcat {
                        container.interestsRepository.observePublicationSelected().take(1)
                    }
                }

            )
        )
    }
}
