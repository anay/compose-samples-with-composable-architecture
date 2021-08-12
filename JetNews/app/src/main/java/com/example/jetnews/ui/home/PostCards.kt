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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.andThen
import arrow.optics.optics
import com.example.jetnews.R
import com.example.jetnews.framework.Reducer
import com.example.jetnews.framework.Store
import com.example.jetnews.framework.StoreView
import com.example.jetnews.model.Post
import com.example.jetnews.ui.theme.JetnewsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Composable
fun AuthorAndReadTime(
    post: Post,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = stringResource(
                    id = R.string.home_post_min_read,
                    formatArgs = arrayOf(
                        post.metadata.author.name,
                        post.metadata.readTimeMinutes
                    )
                ),
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@Composable
fun PostImage(post: Post, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(post.imageThumbId),
        contentDescription = null, // decorative
        modifier = modifier
            .size(40.dp, 40.dp)
            .clip(MaterialTheme.shapes.small)
    )
}

@Composable
fun PostTitle(post: Post) {
    Text(post.title, style = MaterialTheme.typography.subtitle1)
}

@Composable
fun PostCardSimple(
    store:Store<PostState, PostCardSimpleAction>
) {
    StoreView(store) { state ->
        val bookmarkAction = stringResource(if (state.favorite) R.string.unbookmark else R.string.bookmark)


        val sendToggleMessage = sendToStore(PostCardSimpleAction.ToggleFavorite(state.post.id))

        Row(
            modifier = Modifier
                .clickable(onClick = { state.post.id }.andThen(sendToStore(PostCardSimpleAction::ExternalNavigateTo)))
                .padding(16.dp)
                .semantics {
                    // By defining a custom action, we tell accessibility services that this whole
                    // composable has an action attached to it. The accessibility service can choose
                    // how to best communicate this action to the user.
                    customActions = listOf(
                        CustomAccessibilityAction(
                            label = bookmarkAction,
                            action = sendToggleMessage.andThen { true }
                        )
                    )
                }
        ) {
            PostImage(state.post, Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                PostTitle(state.post)
                AuthorAndReadTime(state.post)
            }
            BookmarkButton(
                isBookmarked = state.favorite,
                onClick = sendToggleMessage,
                // Remove button semantics so action can be handled at row level
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

sealed class PostCardSimpleAction{
    data class ToggleFavorite(val id:String):PostCardSimpleAction()
    data class UpdatedFavorites(val favorites:Set<String>):PostCardSimpleAction()
    data class ExternalNavigateTo(val id:String):PostCardSimpleAction()
}

class PostCardSimpleEnvironment(
    val onToggleFavorite:(String) -> Flow<Set<String>>
)

val PostCardSimpleReducer:Reducer<PostState, PostCardSimpleAction, PostCardSimpleEnvironment> = {
    state, action, env, scope ->
    when(action){
        is PostCardSimpleAction.ToggleFavorite -> Pair(
            state,
            env
                .onToggleFavorite(action.id)
                .map { PostCardSimpleAction.UpdatedFavorites(it) }
        )
        is PostCardSimpleAction.ExternalNavigateTo -> state to emptyFlow()
        is PostCardSimpleAction.UpdatedFavorites -> PostState.favorite.set(
            source = state,
            focus = action.favorites.contains(state.post.id)
        ) to emptyFlow()
    }
}

data class PostCardHistoryState(val post:PostState, val openDialog:Boolean)

sealed class PostCardHistoryAction{
    data class ExternalNavigateTo(val id:String):PostCardHistoryAction()
    object OpenDialog:PostCardHistoryAction()
    object CloseDialog:PostCardHistoryAction()
}

val PostCardHistoryReducer:Reducer<PostCardHistoryState, PostCardHistoryAction, Unit> = {
    state, action, env, _ ->
    when(action){
        is PostCardHistoryAction.ExternalNavigateTo -> state to emptyFlow()
        PostCardHistoryAction.OpenDialog -> state.copy(openDialog = true) to emptyFlow()
        PostCardHistoryAction.CloseDialog -> state.copy(openDialog = false) to emptyFlow()
    }
}

@Composable
fun PostCardHistory(store:Store<PostCardHistoryState, PostCardHistoryAction>) {
    StoreView(store) { state ->

        val navigateToArticle = sendToStore(PostCardHistoryAction.ExternalNavigateTo(state.post.post.id))

        Row(
            Modifier
                .clickable(onClick = navigateToArticle)
        ) {
            PostImage(
                post = state.post.post,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = stringResource(id = R.string.home_post_based_on_history),
                        style = MaterialTheme.typography.overline
                    )
                }
                PostTitle(post = state.post.post)
                AuthorAndReadTime(
                    post = state.post.post,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                IconButton(onClick = sendToStore(PostCardHistoryAction.OpenDialog)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_actions)
                    )
                }
            }
        }

        if (state.openDialog) {
            AlertDialog(
                modifier = Modifier.padding(20.dp),
                onDismissRequest = sendToStore(PostCardHistoryAction.CloseDialog),
                title = {
                    Text(
                        text = stringResource(id = R.string.fewer_stories),
                        style = MaterialTheme.typography.h6
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.fewer_stories_content),
                        style = MaterialTheme.typography.body1
                    )
                },
                confirmButton = {
                    Text(
                        text = stringResource(id = R.string.agree),
                        style = MaterialTheme.typography.button,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier
                            .padding(15.dp)
                            .clickable(onClick = sendToStore(PostCardHistoryAction.CloseDialog))
                    )
                }
            )
        }
    }
}

@Composable
fun BookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clickLabel = stringResource(
        if (isBookmarked) R.string.unbookmark else R.string.bookmark
    )
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        IconToggleButton(
            checked = isBookmarked,
            onCheckedChange = { onClick() },
            modifier = modifier.semantics {
                // Use a custom click label that accessibility services can communicate to the user.
                // We only want to override the label, not the actual action, so for the action we pass null.
                this.onClick(label = clickLabel, action = null)
            }
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = null // handled by click label of parent
            )
        }
    }
}

@Preview("Bookmark Button")
@Composable
fun BookmarkButtonPreview() {
    JetnewsTheme {
        Surface {
            BookmarkButton(isBookmarked = false, onClick = { })
        }
    }
}

@Preview("Bookmark Button Bookmarked")
@Composable
fun BookmarkButtonBookmarkedPreview() {
    JetnewsTheme {
        Surface {
            BookmarkButton(isBookmarked = true, onClick = { })
        }
    }
}

//@Preview("Simple post card")
//@Preview("Simple post card (dark)", uiMode = UI_MODE_NIGHT_YES)
//@Composable
//fun SimplePostPreview() {
//    JetnewsTheme {
//        Surface {
//            PostCardSimple(post3, {}, false, {})
//        }
//    }
//}
//
//@Preview("Post History card")
//@Composable
//fun HistoryPostPreview() {
//    JetnewsTheme {
//        Surface {
//            PostCardHistory(post3, {})
//        }
//    }
//}
