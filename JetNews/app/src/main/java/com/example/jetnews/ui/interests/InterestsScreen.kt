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
import arrow.core.andThen
import arrow.core.none
import arrow.core.some
import arrow.optics.Optional
import arrow.optics.optics
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.interests.InterestsRepository
import com.example.jetnews.data.interests.TopicSelection
import com.example.jetnews.data.interests.impl.FakeInterestsRepository
import com.example.jetnews.framework.*
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.produceUiState
import com.example.jetnews.utils.supportWideScreen
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    object Loading:LoadedStatus<Nothing>()
    object NotLoaded:LoadedStatus<Nothing>()
}

data class InterestsScreenState(
    val topicListState:LoadedStatus<TopicListState>,
    val peopleListState: LoadedStatus<TabWithTopicsState>,
    val publicationListState: LoadedStatus<TabWithTopicsState>,
    val selectedTopics: Set<TopicSelection>,
    val selectedPeople: Set<String>,
    val selectedPublications:Set<String>,
    val currentTab: Sections
){
    companion object{

        val topicListState:Optional<InterestsScreenState, TopicListState> = arrow.optics.Optional(
            getOption = { if (it.topicListState is LoadedStatus.Loaded) it.topicListState.value.some() else none() },
            set = {screen, state -> screen.copy(topicListState = LoadedStatus.Loaded(state))}
        )

        val peopleListState:Optional<InterestsScreenState, TabWithTopicsState> = arrow.optics.Optional(
            getOption = { if (it.peopleListState is LoadedStatus.Loaded) it.peopleListState.value.some() else none() },
            set = {screen, state -> screen.copy(peopleListState = LoadedStatus.Loaded(state))}
        )

        val publicationListState:Optional<InterestsScreenState, TabWithTopicsState> = arrow.optics.Optional(
            getOption = { if (it.publicationListState is LoadedStatus.Loaded) it.publicationListState.value.some() else none() },
            set = {screen, state -> screen.copy(publicationListState = LoadedStatus.Loaded(state))}
        )
    }
}

@optics
sealed class InterestsScreenAction{
    companion object

    @optics data class TopicListActions(val action:TopicListAction):InterestsScreenAction(){
        companion object
    }

    @optics data class PeopleListActions(val action:TabWithTopicsAction):InterestsScreenAction(){
        companion object
    }

    @optics data class PublicationListActions(val action:TabWithTopicsAction):InterestsScreenAction(){
        companion object
    }

    object LoadPeopleList:InterestsScreenAction()
    object LoadTopics:InterestsScreenAction()
    object LoadPublicationList:InterestsScreenAction()

    data class TopicsLoaded(val value:Map<String, List<String>>, val selected:Set<TopicSelection>):InterestsScreenAction()
    data class PeopleListLoaded(val value:List<String>, val selected:Set<String>):InterestsScreenAction()
    data class PublicationListLoaded(val value: List<String>, val selected:Set<String>):InterestsScreenAction()
    data class NavigateTo(val tab:Sections):InterestsScreenAction()

    object OpenDrawer:InterestsScreenAction()
    object None:InterestsScreenAction()
}

class InterestScreenEnvironment(
    val interestsRepository: InterestsRepository,
    val openDrawer: () -> Flow<Unit>
)
{
    fun getPeople() = flow{
        val people = interestsRepository.getPeople()
        when(people){
            is Result.Success -> emit(people.data)
            is Result.Error -> throw people.exception
        }
    }

    fun getPublication() = flow{
        val publication = interestsRepository.getPublications()
        when(publication){
            is Result.Success -> emit(publication.data)
            is Result.Error -> throw publication.exception
        }
    }

    fun getTopics() = flow{
        val topics = interestsRepository.getTopics()
        when(topics){
            is Result.Success -> emit(topics.data)
            is Result.Error -> throw topics.exception
        }
    }

    fun selectedTopics() = interestsRepository.observeTopicsSelected().take(1)

    fun toggleTopic(data:TopicSelection) =
        flow<Unit> {
            interestsRepository.toggleTopicSelection(data)
            emit(Unit)
        }.flatMapConcat {
            interestsRepository.observeTopicsSelected().take(1)
        }

    fun selectedPeople() = interestsRepository.observePeopleSelected().take(1)

    fun togglePerson(data:String) =
        flow<Unit> {
            interestsRepository.togglePersonSelected(data)
            emit(Unit)
        }.flatMapConcat {
            interestsRepository.observePeopleSelected().take(1)
        }

    fun selectedPublications() = interestsRepository.observePublicationSelected().take(1)

    fun togglePublication(data:String) =
        flow<Unit> {
            interestsRepository.togglePublicationSelected(data)
            emit(Unit)
        }.flatMapConcat {
            interestsRepository.observePublicationSelected().take(1)
        }
}

val InterestScreenReducer:Reducer<InterestsScreenState, InterestsScreenAction, InterestScreenEnvironment> =
    { state, action, env, scope ->

        when(action){
            InterestsScreenAction.LoadPeopleList -> state to env
                .getPeople()
                .combine(env.selectedPeople()){
                    peopleList, selectedPeople -> Pair(peopleList, selectedPeople)
                }
                .flowOn(Dispatchers.IO)
                .map { InterestsScreenAction.PeopleListLoaded(it.first, it.second) }

            InterestsScreenAction.LoadTopics -> state to env
                .getTopics()
                .combine(env.selectedTopics()){
                    topicList, selectedTopics -> Pair(topicList, selectedTopics)
                }
                .flowOn(Dispatchers.IO)
                .map { InterestsScreenAction.TopicsLoaded(it.first, it.second) }

            InterestsScreenAction.LoadPublicationList -> state to env
                .getPublication()
                .combine(env.selectedPublications()){
                    publicationList, selectedPublications -> Pair(publicationList, selectedPublications)
                }
                .flowOn(Dispatchers.IO)
                .map { InterestsScreenAction.PublicationListLoaded(it.first, it.second) }

            is InterestsScreenAction.PublicationListLoaded ->
                TabWithTopicsState(
                    topics = action.value.map { topic -> topic to TopicItemState<String>(
                        id = topic,
                        title = topic,
                        selected = action.selected.contains(topic)
                    ) }.toMap()
                ).let {
                    InterestsScreenState.publicationListState.set(state, it) to emptyFlow()
                }

            is InterestsScreenAction.TopicsLoaded ->
                TopicListState(
                    sections = action.value.map { (section, topicStrings) -> section to SectionState(
                    title = section,
                    topics = topicStrings.map { topicName -> topicName to TopicItemState(
                        id = TopicSelection(section, topicName),
                        title = topicName,
                        selected = action.selected.contains(TopicSelection(section, topicName))
                        )
                    }.toMap()
                    ) }.toMap()
                ).let {
                    InterestsScreenState.topicListState.set(state, it) to emptyFlow()
                }

            is InterestsScreenAction.PeopleListLoaded ->
                TabWithTopicsState(
                    topics = action.value.map { topic -> topic to TopicItemState<String>(
                        id = topic,
                        title = topic,
                        selected = action.selected.contains(topic)
                ) }.toMap()).let {
                    InterestsScreenState.peopleListState.set(state, it) to emptyFlow()
                }

            is InterestsScreenAction.NavigateTo ->
                state.copy(currentTab = action.tab) to emptyFlow()

            InterestsScreenAction.OpenDrawer -> state to
                env
                    .openDrawer()
                    .flowOn(Dispatchers.Main)
                    .map { InterestsScreenAction.None }

            InterestsScreenAction.None -> state to emptyFlow()

            else -> state to emptyFlow()
        }
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
    store:Store<InterestsScreenState, InterestsScreenAction>,
    scaffoldState: ScaffoldState = rememberScaffoldState()
) {
    // Returns a [CoroutineScope] that is scoped to the lifecycle of [InterestsScreen]. When this
    // screen is removed from composition, the scope will be cancelled.
    val coroutineScope = rememberCoroutineScope()

    // Describe the screen sections here since each section needs 2 states and 1 event.
    // Pass them to the stateless InterestsScreen using a tabContent.

    StoreView(store) { state ->

        val topicsSection = TabContent(Sections.Topics) topicContent@{

            if (state.topicListState is LoadedStatus.NotLoaded){
                sendToStore(InterestsScreenAction.LoadTopics)()
                return@topicContent
            }

            if (state.topicListState !is LoadedStatus.Loaded) return@topicContent

            val topicStore = store.forView<TopicListState, TopicListAction>(
                appState = state,
                stateBuilder = { state.topicListState.value },
                actionMapper = { action -> InterestsScreenAction.TopicListActions(action)}
            )

            TopicList(topicStore)
        }

        val peopleSection = TabContent(Sections.People) peopleSection@{

            if (state.peopleListState is LoadedStatus.NotLoaded){
                sendToStore(InterestsScreenAction.LoadPeopleList)()
                return@peopleSection
            }

            if (state.peopleListState !is LoadedStatus.Loaded) return@peopleSection

            val peopleStore = store.forView<TabWithTopicsState, TabWithTopicsAction>(
                appState = state,
                stateBuilder = { state.peopleListState.value },
                actionMapper = { action -> InterestsScreenAction.PeopleListActions(action) }
            )

            TabWithTopics(peopleStore)
        }

        val publicationSection = TabContent(Sections.Publications) publicationSection@{

            if (state.publicationListState is LoadedStatus.NotLoaded){
                sendToStore(InterestsScreenAction.LoadPublicationList)()
                return@publicationSection
            }

            if (state.publicationListState !is LoadedStatus.Loaded) return@publicationSection

            val publicationStore = store.forView<TabWithTopicsState, TabWithTopicsAction>(
                appState = state,
                stateBuilder = { state.publicationListState.value },
                actionMapper = { action -> InterestsScreenAction.PublicationListActions(action) }
            )

            TabWithTopics(publicationStore)
        }

        val tabContent = listOf(topicsSection, peopleSection, publicationSection)
        InterestsScreen(
            tabContent = tabContent,
            tab = state.currentTab,
            onTabChange = { section -> sendToStore(InterestsScreenAction.NavigateTo(section))() },
            openDrawer = sendToStore(InterestsScreenAction.OpenDrawer),
            scaffoldState = scaffoldState
        )
    }

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
    val onTopicSelect: (TopicSelection) -> Flow<Set<TopicSelection>>
)

fun <TopicId> TopicItemReducer():Reducer<TopicItemState<TopicId>, TopicItemAction<TopicId>, TopicItemEnvironment<TopicId>> = {
        state, action, environment, scope ->
    when(action){
        TopicItemAction.None -> state to emptyFlow()
        is TopicItemAction.Toggle -> state to environment.onToggle(state.id).map { TopicItemAction.UpdatedSelections(it) }
        is TopicItemAction.UpdatedSelections -> state.copy(selected = action.selected.contains(state.id)) to emptyFlow()
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

class TabWithTopicsEnvironment<TopicId>(
    val onTopicSelect: (TopicId) -> Flow<Set<TopicId>>
)

val TabWithTopicsReducer:Reducer<TabWithTopicsState, TabWithTopicsAction, TabWithTopicsEnvironment<String>> =
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
    val onToggle:(TopicId) -> Flow<Set<TopicId>>
)

sealed class TopicItemAction<out TopicId>{
    data class Toggle<TopicId>(val title:TopicId):TopicItemAction<TopicId>()
    data class UpdatedSelections<TopicId>(val selected:Set<TopicId>):TopicItemAction<TopicId>()
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
//
//@Preview("Interests screen", "Interests")
//@Preview("Interests screen (dark)", "Interests", uiMode = UI_MODE_NIGHT_YES)
//@Preview("Interests screen (big font)", "Interests", fontScale = 1.5f)
//@Preview("Interests screen (large screen)", "Interests", device = Devices.PIXEL_C)
//@Composable
//fun PreviewInterestsScreen() {
//    JetnewsTheme {
//        InterestsScreen(
//            interestsRepository = FakeInterestsRepository(),
//            openDrawer = {}
//        )
//    }
//}

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

val ComposedInterestScreenReducer:Reducer<InterestsScreenState, InterestsScreenAction, InterestScreenEnvironment> =
    com.example.jetnews.framework.combine(
        TopicListReducer.pullbackOptional(
            stateMapper = InterestsScreenState.topicListState,
            environmentMapper = { env ->
                TopicListEnvironment(
                    onTopicSelect = { id -> env.toggleTopic(id) }
                )
            },
            actionMapper = InterestsScreenAction.topicListActions.action
        ),
        TabWithTopicsReducer.pullBackConditional(
            condition = { it is InterestsScreenAction.PeopleListActions },
            stateMapper = InterestsScreenState.peopleListState,
            actionMapper = InterestsScreenAction.peopleListActions.action,
            environmentMapper = { env ->
                TabWithTopicsEnvironment<String>(
                    onTopicSelect = env::togglePerson
                )
            }
        ),
        TabWithTopicsReducer.pullBackConditional(
            condition = { it is InterestsScreenAction.PublicationListActions },
            stateMapper = InterestsScreenState.publicationListState,
            actionMapper = InterestsScreenAction.publicationListActions.action,
            environmentMapper = { env ->
                TabWithTopicsEnvironment<String>(
                    onTopicSelect = env::togglePublication
                )
            }
        ),
        InterestScreenReducer
    )


