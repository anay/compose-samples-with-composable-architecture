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
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arrow.core.andThen
import com.example.jetnews.R
import com.example.jetnews.framework.Reducer
import com.example.jetnews.framework.Store
import com.example.jetnews.framework.StoreView
import com.example.jetnews.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class PostCardPopularState(val post:Post)

sealed class PostCardPopularAction{
    data class ExternalNavigateTo(val id:String):PostCardPopularAction()
}

val PostCardPopularReducer:Reducer<PostState,PostCardPopularAction,Unit> = {
    state, action, _, _ ->
    when(action){
        is PostCardPopularAction.ExternalNavigateTo -> state to emptyFlow()
    }
}

@Composable
fun PostCardPopular(
    store: Store<PostState, PostCardPopularAction>,
    modifier: Modifier = Modifier
) {

    StoreView(store) { state ->

        val navigateToArticle = sendToStore(PostCardPopularAction.ExternalNavigateTo(state.post.id))

        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.size(280.dp, 240.dp)
        ) {
            Column(modifier = Modifier.clickable(onClick = navigateToArticle)) {

                Image(
                    painter = painterResource(state.post.imageId),
                    contentDescription = null, // decorative
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth()
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.post.title,
                        style = MaterialTheme.typography.h6,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.post.metadata.author.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.body2
                    )

                    Text(
                        text = stringResource(
                            id = R.string.home_post_min_read,
                            formatArgs = arrayOf(
                                state.post.metadata.date,
                                state.post.metadata.readTimeMinutes
                            )
                        ),
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
    }


}

//@Preview("Regular colors")
//@Preview("Dark colors", uiMode = UI_MODE_NIGHT_YES)
//@Composable
//fun PreviewPostCardPopular() {
//    JetnewsTheme {
//        Surface {
//            PostCardPopular(post1, {})
//        }
//    }
//}
//
//@Preview("Regular colors, long text")
//@Composable
//fun PreviewPostCardPopularLongText() {
//    val loremIpsum =
//        """
//        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras ullamcorper pharetra massa,
//        sed suscipit nunc mollis in. Sed tincidunt orci lacus, vel ullamcorper nibh congue quis.
//        Etiam imperdiet facilisis ligula id facilisis. Suspendisse potenti. Cras vehicula neque sed
//        nulla auctor scelerisque. Vestibulum at congue risus, vel aliquet eros. In arcu mauris,
//        facilisis eget magna quis, rhoncus volutpat mi. Phasellus vel sollicitudin quam, eu
//        consectetur dolor. Proin lobortis venenatis sem, in vestibulum est. Duis ac nibh interdum,
//        """.trimIndent()
//    JetnewsTheme {
//        Surface {
//            PostCardPopular(
//                post1.copy(
//                    title = "Title$loremIpsum",
//                    metadata = post1.metadata.copy(
//                        author = PostAuthor("Author: $loremIpsum"),
//                        readTimeMinutes = Int.MAX_VALUE
//                    )
//                ),
//                {}
//            )
//        }
//    }
//}
