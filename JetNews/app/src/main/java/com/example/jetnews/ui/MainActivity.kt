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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import arrow.optics.optics
import com.example.jetnews.JetnewsApplication
import com.example.jetnews.R
import com.example.jetnews.framework.*
import com.example.jetnews.model.Post
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val store = (application as JetnewsApplication).store

        val listItemStates = (1..10).map {
            ListItemState(id = it, count = it)
        }.toIdentifiedList { it.id }

        val listAppStore = Store.of(
            state = ListAppState(
                screenName = "List App",
                items = listItemStates
            ),
            reducer = ListAppReducer(),
            environment = Unit
        )

        setContent {
            JetnewsApp(store)
//            ListApp(store = listAppStore)
        }

        lifecycleScope.launchWhenResumed {
            val scope = this
            store.state.map { it.share }.distinctUntilChanged().collect {
                if (it is ShareOption.Share){
                    sharePost(it.title, it.url)
                    store.send(JetNewsAppAction.ShareOptionStarted, scope)
                }
            }
        }
    }
}

/**
 * Show a share sheet for a post
 *
 * @param post to share
 * @param context Android context to show the share sheet in
 >*/
fun Context.sharePost(title: String, url:String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.article_share_post)))
}

@optics
data class ListItemState(val id:Int, val count:Int){
    companion object
}

@optics
sealed class ListItemAction{
    object Clicked:ListItemAction()
    companion object
}

fun ListItemReducer():Reducer<ListItemState, ListItemAction, Unit> = {
    state, action, _, _ ->
    when(action){
        ListItemAction.Clicked ->
            state.copy(count = state.count+1) to emptyFlow()
    }
}

@Composable
fun ListItem(store: Store<ListItemState, ListItemAction>){
    StoreView(store = store) { state ->
        Button(onClick = sendToStore(ListItemAction.Clicked)) {
            Text("I'm clicked ${state.count} times")
        }
    }
}

@optics
data class ListAppState(
    val screenName:String,
    val items:IdentifiedList<Int, ListItemState>
){
    companion object
}

@optics
sealed class ListAppAction{

    companion object

    object Reset:ListAppAction()

    @optics data class ListItemActions(val action:Pair<Int, ListItemAction>):ListAppAction(){
        companion object
    }
}

fun InternalListAppReducer():Reducer<ListAppState, ListAppAction, Unit> = {
    state, action, _, _ ->
    when(action){
        is ListAppAction.ListItemActions -> state to emptyFlow()
        ListAppAction.Reset ->
            state.copy(
                items = state.items.map { it.value }.map { it.copy(count = 0) }.toIdentifiedList { it.id }
            ) to emptyFlow()
    }
}

fun ListAppReducer():Reducer<ListAppState, ListAppAction, Unit> = combine(
    InternalListAppReducer(),
    ListItemReducer().forEachOnList(
        states = ListAppState.items,
        actionMapper = ListAppAction.listItemActions.action,
        environmentMapper = { it }
    )
)

@Composable
fun ListApp(store:Store<ListAppState, ListAppAction>){
    StoreView(store = store) { state ->

        Column {
            Text(state.screenName)
            Button(onClick = sendToStore(ListAppAction.Reset)) {
                Text("Reset")
            }
            LazyColumn{
                items(items = state.items, key = { item:IdentifiedItem<Int, ListItemState> -> item.id }){ listItemState ->

                    val listItemStore = store.forView<ListItemState, ListItemAction>(
                        appState = state,
                        stateBuilder = { listItemState.value },
                        actionMapper = { action -> ListAppAction.ListItemActions(listItemState.id to action) }
                    )

                    ListItem(store = listItemStore)
                }
            }
        }

    }
}