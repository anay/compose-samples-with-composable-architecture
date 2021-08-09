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

package com.example.jetnews.ui.interests

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.optics.optics
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.interests.InterestsRepository
import com.example.jetnews.data.interests.TopicSelection
import com.example.jetnews.data.interests.TopicsMap
import com.example.jetnews.data.interests.impl.FakeInterestsRepository
import com.example.jetnews.framework.Reducer
import com.example.jetnews.framework.Store
import com.example.jetnews.framework.StoreView
import com.example.jetnews.framework.forEach
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.produceUiState
import com.example.jetnews.utils.supportWideScreen
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

enum class Sections(@StringRes val titleResId: Int) {
    Topics(R.string.interests_section_topics),
    People(R.string.interests_section_people),
    Publications(R.string.interests_section_publications)
}

/**
 * TabContent for a single tab of the screen.
 *
 * This is intended to encapsulate a tab & it's content as a single object. It was added to avoid
 * passing several parameters per-tab from the stateful composable to the composable that displays
 * the current tab.
 *
 * @param section the tab that this content is for
 * @param section content of the tab, a composable that describes the content
 */
class TabContent(val section: Sections, val content: @Composable () -> Unit)


sealed class LoadedStatus<out T>{
    data class Loaded<T>(val value:T):LoadedStatus<T>()
    object NotLoaded:LoadedStatus<Nothing>()
}

@optics
data class InterestsScreenState(
    val topicListState:LoadedStatus<TopicListState>,
    val peopleListState: LoadedStatus<TabWithTopicsState>,
    val publicationListState: LoadedStatus<TabWithTopicsState>
){
    companion object
}

@optics
sealed class InterestsScreenAction{
    companion object

    @optics data class TopicListActions(val action:Pair<String, TopicListAction>):InterestsScreenAction(){
        companion object
    }

    @optics data class PeopleListActions(val action:Pair<String, TabWithTopicsAction>):InterestsScreenAction(){
        companion object
    }

    @optics data class PublicationListActions(val action:Pair<String, TabWithTopicsAction>):InterestsScreenAction(){
        companion object
    }
}

class InterestScreenEnvironment(
    val interestsRepository: InterestsRepository,
    val openDrawer: () -> Flow<Unit>
)
{

}



/**
 * Stateful InterestsScreen manages state using [produceUiState]
 *
 * @param interestsRepository data source for this screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for screen Scaffold
 */
@Composable
fun InterestsScreen(
    interestsRepository: InterestsRepository,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState = rememberScaffoldState()
) {
    // Returns a [CoroutineScope] that is scoped to the lifecycle of [InterestsScreen]. When this
    // screen is removed from composition, the scope will be cancelled.
    val coroutineScope = rememberCoroutineScope()

    // Describe the screen sections here since each section needs 2 states and 1 event.
    // Pass them to the stateless InterestsScreen using a tabContent.
    val topicsSection = TabContent(Sections.Topics) {
        val (topics) = produceUiState(interestsRepository) {
            getTopics()
        }
        // collectAsState will read a [Flow] in Compose
        val selectedTopics by interestsRepository.observeTopicsSelected().collectAsState(setOf())
        val onTopicSelect: (TopicSelection) -> Unit = {
            coroutineScope.launch { interestsRepository.toggleTopicSelection(it) }
        }
        val data = topics.value.data ?: return@TabContent


        val isTopicSelected:(String, String) -> Boolean = {section, topic -> selectedTopics.contains(TopicSelection(section, topic)) }

        val sectionData = data.map { (section, topicStrings) -> section to SectionState(
            title = section,
            topics = topicStrings.map { topicName -> topicName to TopicItemState(
                id = TopicSelection(section, topicName),
                title = topicName,
                selected = isTopicSelected(section, topicName)
            ) }.toMap()
        ) }.toMap()

        val topicListState = TopicListState(sections = sectionData)


        val store = Store.of(
            state = topicListState,
            reducer = TopicListReducer,
            environment = TopicListEnvironment(
                onTopicSelect = { topicSelection ->
                    flow {
                        onTopicSelect(topicSelection)
                        emit(Unit)
                    }
                }
            )
        )


        TopicList(store)
    }

    val peopleSection = TabContent(Sections.People) {
        val (people) = produceUiState(interestsRepository) {
            getPeople()
        }
        val selectedPeople by interestsRepository.observePeopleSelected().collectAsState(setOf())
        val onPeopleSelect: (String) -> Unit = {
            coroutineScope.launch { interestsRepository.togglePersonSelected(it) }
        }
        val data = people.value.data ?: return@TabContent

        val state = TabWithTopicsState(
            topics = data.map { topic -> topic to TopicItemState<String>(
                id = topic,
                title = topic,
                selected = selectedPeople.contains(topic)
            ) }.toMap()
        )

        val store = Store.of(
            state = state,
            reducer = TabWithTopicsReducer,
            environment = TabWithTopicsEnvironment(
                onTopicSelect = { id ->
                    flow {
                        onPeopleSelect(id)
                        emit(Unit)
                    }
                }
            )
        )

        TabWithTopics(store)
    }

    val publicationSection = TabContent(Sections.Publications) {
        val (publications) = produceUiState(interestsRepository) {
            getPublications()
        }
        val selectedPublications by interestsRepository.observePublicationSelected()
            .collectAsState(setOf())
        val onPublicationSelect: (String) -> Unit = {
            coroutineScope.launch { interestsRepository.togglePublicationSelected(it) }
        }
        val data = publications.value.data ?: return@TabContent


        val state = TabWithTopicsState(
            topics = data.map { topic -> topic to TopicItemState<String>(
                id = topic,
                title = topic,
                selected = selectedPublications.contains(topic)
            ) }.toMap()
        )

        val store = Store.of(
            state = state,
            reducer = TabWithTopicsReducer,
            environment = TabWithTopicsEnvironment(
                onTopicSelect = { id ->
                    flow {
                        onPublicationSelect(id)
                        emit(Unit)
                    }
                }
            )
        )
        TabWithTopics(store)
    }

    val tabContent = listOf(topicsSection, peopleSection, publicationSection)
    val (currentSection, updateSection) = rememberSaveable { mutableStateOf(tabContent.first().section) }
    InterestsScreen(
        tabContent = tabContent,
        tab = currentSection,
        onTabChange = updateSection,
        openDrawer = openDrawer,
        scaffoldState = scaffoldState
    )
}

/**
 * Stateless interest screen displays the tabs specified in [tabContent]
 *
 * @param tabContent (slot) the tabs and their content to display on this screen, must be a non-empty
 * list, tabs are displayed in the order specified by this list
 * @param tab (state) the current tab to display, must be in [tabContent]
 * @param onTabChange (event) request a change in [tab] to another tab from [tabContent]
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) the state for the screen's [Scaffold]
 */
@Composable
fun InterestsScreen(
    tabContent: List<TabContent>,
    tab: Sections,
    onTabChange: (Sections) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState,
) {
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            InsetAwareTopAppBar(
                title = { Text(stringResource(id = R.string.interests_title)) },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_jetnews_logo),
                            contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                        )
                    }
                }
            )
        }
    ) {

        val store = Store.of(
            state = TabContentState(
                tabContent = tabContent,
                currentSection = tab
            ),
            reducer = TabContentReducer,
            environment = TabContentEnvironment(
                updateSection = { section ->
                    flow {
                        onTabChange(section)
                        emit(Unit)
                    }
                }
            )
        )

        TabContent(store)
    }
}

data class TabContentState(
    val tabContent: List<TabContent>,
    val currentSection: Sections
)

class TabContentEnvironment(
    val updateSection:(Sections) -> Flow<Unit>
)

sealed class TabContentAction{
    data class UpdateSection(val section:Sections):TabContentAction()
    object None:TabContentAction()
}

val TabContentReducer:Reducer<TabContentState, TabContentAction, TabContentEnvironment> = {
    state,action, env, _ ->
    when(action){
        TabContentAction.None -> state to emptyFlow()
        is TabContentAction.UpdateSection -> state to env.updateSection(action.section).flowOn(
            Dispatchers.Main).map { TabContentAction.None }
    }
}

/**
 * Displays a tab row with [currentSection] selected and the body of the corresponding [tabContent].
 *
 * @param currentSection (state) the tab that is currently selected
 * @param updateSection (event) request a change in tab selection
 * @param tabContent (slot) tabs and their content to display, must be a non-empty list, tabs are
 * displayed in the order of this list
 */
@Composable
private fun TabContent(
    store:Store<TabContentState, TabContentAction>
) {
    StoreView(store) { state ->
        val selectedTabIndex = state.tabContent.indexOfFirst { it.section == state.currentSection }
        Column {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                backgroundColor = MaterialTheme.colors.onPrimary,
                contentColor = MaterialTheme.colors.primary,

                ) {
                state.tabContent.forEachIndexed { index, tabContent ->
                    val colorText = if (selectedTabIndex == index) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = sendToStore(TabContentAction.UpdateSection(tabContent.section)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(id = tabContent.section.titleResId),
                            color = colorText,
                            style = MaterialTheme.typography.subtitle2,
                            modifier = Modifier.paddingFromBaseline(top = 20.dp)
                        )
                    }
                }
            }
            Divider(
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .supportWideScreen()
            ) {
                // display the current tab content which is a @Composable () -> Unit
                state.tabContent[selectedTabIndex].content()
            }
        }
    }

}

@optics
data class SectionState(
    val title:String,
    val topics:Map<String, TopicItemState<TopicSelection>>
){
    companion object
}

@optics
data class TopicListState(
    val sections: Map<String, SectionState>
){
    companion object
}

@optics
sealed class SectionItemAction{
    companion object
    @optics data class TopicItemActions(val action:Pair<String, TopicItemAction<TopicSelection>>):SectionItemAction(){
        companion object
    }

    object None:SectionItemAction()
}

@optics
sealed class TopicListAction{
    companion object

    @optics data class SectionItemActions(val action:Pair<String, SectionItemAction>):TopicListAction(){
        companion object
    }

}

class TopicListEnvironment(
    val onTopicSelect: (TopicSelection) -> Flow<Unit>
)

fun <TopicId> TopicItemReducer():Reducer<TopicItemState<TopicId>, TopicItemAction<TopicId>, TopicItemEnvironment<TopicId>> = {
        state, action, environment, scope ->
    when(action){
        TopicItemAction.None -> state to emptyFlow()
        is TopicItemAction.Toggle -> state to environment.onToggle(state.id).map { TopicItemAction.None }
    }
}

val SectionItemReducer:Reducer<SectionState, SectionItemAction, TopicListEnvironment> =
        TopicItemReducer<TopicSelection>().forEach(
            states = SectionState.topics,
            actionMapper = SectionItemAction.topicItemActions.action,
            environmentMapper =  { env -> TopicItemEnvironment<TopicSelection>(
                onToggle = env.onTopicSelect
            ) }
        )

val TopicListReducer:Reducer<TopicListState, TopicListAction, TopicListEnvironment> = SectionItemReducer.forEach(
    states = TopicListState.sections,
    actionMapper = TopicListAction.sectionItemActions.action,
    environmentMapper = { it }
)


/**
 * Display the list for the topic tab
 *
 * @param topics (state) topics to display, mapped by section
 * @param selectedTopics (state) currently selected topics
 * @param onTopicSelect (event) request a topic selection be changed
 */
@Composable
private fun TopicList(
    store:Store<TopicListState, TopicListAction>
) {
    StoreView(store) { state ->
        LazyColumn(Modifier.navigationBarsPadding()) {

            store.forStatesLazy<SectionState, SectionItemAction, String>(
                appState = state,
                states = { it.sections },
                actionMapper = {id, action -> TopicListAction.SectionItemActions(id to action)}
            ){ childStore ->

                item {
                    StoreView(store = childStore) { childState ->
                        Text(
                            text = childState.title,
                            modifier = Modifier
                                .padding(16.dp)
                                .semantics { heading() },
                            style = MaterialTheme.typography.subtitle1
                        )
                        Column {
                            childStore.forStates<TopicItemState<TopicSelection>, TopicItemAction<TopicSelection>, String>(
                                appState = childState,
                                states = { it.topics },
                                actionMapper = {id, action -> SectionItemAction.TopicItemActions(id to action)}
                            )
                            { topicStore ->
                                TopicItem(topicStore)
                                TopicDivider()
                            }
                        }
                    }
                }
            }
        }
    }


}



@optics
data class TabWithTopicsState(
    val topics: Map<String, TopicItemState<String>>
){
    companion object
}

@optics
sealed class TabWithTopicsAction{
    companion object
    @optics data class TopicItemActions(val action:Pair<String, TopicItemAction<String>>):TabWithTopicsAction(){
        companion object
    }
}

class TabWithTopicsEnvironment(
    val onTopicSelect: (String) -> Flow<Unit>
)

val TabWithTopicsReducer:Reducer<TabWithTopicsState, TabWithTopicsAction, TabWithTopicsEnvironment> =
    TopicItemReducer<String>().forEach(
        states = TabWithTopicsState.topics,
        actionMapper = TabWithTopicsAction.topicItemActions.action,
        environmentMapper = {env -> TopicItemEnvironment(
            onToggle = env.onTopicSelect
        )}
    )

/**
 * Display a simple list of topics
 *
 * @param topics (state) topics to display
 * @param selectedTopics (state) currently selected topics
 * @param onTopicSelect (event) request a topic selection be changed
 */
@Composable
private fun TabWithTopics(
    store: Store<TabWithTopicsState, TabWithTopicsAction>
) {
    StoreView(store) { state ->
        LazyColumn(
            modifier = Modifier
                .padding(top = 16.dp)
                .navigationBarsPadding()
        ) {

            items(items = state.topics.values.toList(),key = {it.id}) { topic ->

                val store = store.forView<TopicItemState<String>, TopicItemAction<String>>(
                    appState = state,
                    stateBuilder = { topic },
                    actionMapper = { action -> TabWithTopicsAction.TopicItemActions(topic.title to action) }
                )
                TopicItem<String>(store)
                TopicDivider()
            }
        }
    }



}

@optics
data class SectionTopicState(
    val section:String,
    val topics: Map<String, TopicItemState<TopicSelection>>
){
    companion object
}


data class TopicItemState<TopicId>(
    val id: TopicId,
    val title:String,
    val selected:Boolean
)

class TopicItemEnvironment<TopicId>(
    val onToggle:(TopicId) -> Flow<Unit>
)

sealed class TopicItemAction<out TopicId>{
    data class Toggle<TopicId>(val title:TopicId):TopicItemAction<TopicId>()
    object None:TopicItemAction<Nothing>()
}



/**
 * Display a full-width topic item
 *
 * @param itemTitle (state) topic title
 * @param selected (state) is topic currently selected
 * @param onToggle (event) toggle selection for topic
 */
@Composable
private fun <TopicId> TopicItem(store:Store<TopicItemState<TopicId>, TopicItemAction<TopicId>>) {
    StoreView(store) { state ->
        val image = painterResource(R.drawable.placeholder_1_1)
        Row(
            modifier = Modifier
                .toggleable(
                    value = state.selected,
                    onValueChange = { sendToStore(TopicItemAction.Toggle(state.id))() }
                )
                .padding(horizontal = 16.dp)
        ) {
            Image(
                painter = image,
                contentDescription = null, // decorative
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(56.dp, 56.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                text = state.title,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(16.dp),
                style = MaterialTheme.typography.subtitle1
            )
            Spacer(Modifier.weight(1f))
            SelectTopicButton(
                modifier = Modifier.align(Alignment.CenterVertically),
                selected = state.selected
            )
        }
    }
}

/**
 * Full-width divider for topics
 */
@Composable
private fun TopicDivider() {
    Divider(
        modifier = Modifier.padding(start = 90.dp, top = 8.dp, bottom = 8.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
    )
}

@Preview("Interests screen", "Interests")
@Preview("Interests screen (dark)", "Interests", uiMode = UI_MODE_NIGHT_YES)
@Preview("Interests screen (big font)", "Interests", fontScale = 1.5f)
@Preview("Interests screen (large screen)", "Interests", device = Devices.PIXEL_C)
@Composable
fun PreviewInterestsScreen() {
    JetnewsTheme {
        InterestsScreen(
            interestsRepository = FakeInterestsRepository(),
            openDrawer = {}
        )
    }
}

//@Preview("Interests screen topics tab", "Topics")
//@Preview("Interests screen topics tab (dark)", "Topics", uiMode = UI_MODE_NIGHT_YES)
//@Composable
//fun PreviewTopicsTab() {
//    val topics = runBlocking {
//        (FakeInterestsRepository().getTopics() as Result.Success).data
//    }
//    JetnewsTheme {
//        Surface {
//            TopicList(topics, setOf(), {})
//        }
//    }
//}
//
//@Preview("Interests screen people tab", "People")
//@Preview("Interests screen people tab (dark)", "People", uiMode = UI_MODE_NIGHT_YES)
//@Composable
//fun PreviewPeopleTab() {
//    val people = runBlocking {
//        (FakeInterestsRepository().getPeople() as Result.Success).data
//    }
//    JetnewsTheme {
//        Surface {
//            PeopleList(people, setOf(), {})
//        }
//    }
//}
//
//@Preview("Interests screen publications tab", "Publications")
//@Preview("Interests screen publications tab (dark)", "Publications", uiMode = UI_MODE_NIGHT_YES)
//@Composable
//fun PreviewPublicationsTab() {
//    val publications = runBlocking {
//        (FakeInterestsRepository().getPublications() as Result.Success).data
//    }
//    JetnewsTheme {
//        Surface {
//            PublicationList(publications, setOf(), {})
//        }
//    }
//}
