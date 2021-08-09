package com.example.jetnews.framework

import androidx.compose.runtime.Composable
import arrow.optics.Lens
import arrow.optics.Optional
import arrow.optics.typeclasses.Index
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

typealias Effect<Action> = Flow<Action>
typealias Reducer<State, Action, AppEnvironment> = suspend (State, Action, AppEnvironment, CoroutineScope) -> Pair<State, Effect<Action>>

class Store<State,  Action> private constructor(
    private val initialState: State,
    private val reducer: suspend (State, Action, CoroutineScope) -> Pair<State, Effect<Action>>
){

    companion object{
        fun <State, Action, AppEnvironment> of(state: State, reducer: Reducer<State, Action, AppEnvironment>, environment: AppEnvironment) =
            Store<State, Action>(
                initialState = state,
                reducer = {viewState, viewAction, scope -> reducer(viewState, viewAction, environment, scope)}
            )
    }
    private val stateData = MutableStateFlow(initialState)
    val state = stateData.asStateFlow()

    suspend fun send(action:Action, scope:CoroutineScope){
        val currentState = stateData.value
        val (nextState, effect) = reducer(currentState, action, scope)
        stateData.value = nextState
        scope.launch {
            withContext(Dispatchers.Default){
                effect.collect { action ->
                    withContext(Dispatchers.Main){
                        send(action, scope)
                    }
                }
            }
        }
    }



    fun <ViewState, ViewAction> forView(
        appState: State,
        stateBuilder: (State) -> ViewState,
        actionMapper: (ViewAction) -> Action
    ): Store<ViewState, ViewAction> = of<ViewState, ViewAction, Unit>(
        state = stateBuilder(appState),
        reducer = { _, viewAction, _, scope ->
            send(actionMapper(viewAction), scope)
            Pair(
                stateBuilder(this.state.value),
                emptyFlow()
            )
        },
        environment = Unit
    )

    @Composable
    fun <ViewState, ViewAction, StateId> forStates(
        appState: State,
        states:(State) -> Map<StateId, ViewState>,
        actionMapper: (StateId, ViewAction) -> Action,
        content:@Composable (Store<ViewState, ViewAction>) -> Unit
    ) {
        val stateValues = states(appState)
        for ((id, viewState) in stateValues){
            val store = forView<ViewState, ViewAction>(
                appState = appState,
                stateBuilder = { viewState },
                actionMapper = { actionMapper(id, it) }
            )
            content(store)
        }

    }

}



fun <State, Action, ViewState, ViewAction, AppEnvironment, ViewEnvironment> pullBack(
    reducer: Reducer<ViewState, ViewAction, ViewEnvironment>,
    stateMapper: Lens<State, ViewState>,
    actionMapper: Optional<Action, ViewAction>,
    environmentMapper: (AppEnvironment) -> ViewEnvironment
): Reducer<State, Action, AppEnvironment> = { state, action, environment, scope ->
    val viewState:ViewState = stateMapper.get(state)
    val viewAction = actionMapper.getOrNull(action)
    if (viewAction != null){
        val (nextViewState, nextEffects) = reducer(viewState, viewAction, environmentMapper(environment), scope)
        Pair(
            stateMapper.set(state, nextViewState),
            nextEffects.map { actionMapper.set(action, it) }
        )
    } else {
        Pair(
            state,
            emptyFlow()
        )
    }
}

fun <State, Action, ViewState, ViewAction, AppEnvironment, ViewEnvironment> Reducer<ViewState, ViewAction, ViewEnvironment>.pullback(
    stateMapper: Lens<State, ViewState>,
    actionMapper: Optional<Action, ViewAction>,
    environmentMapper: (AppEnvironment) -> ViewEnvironment
): Reducer<State, Action, AppEnvironment> = pullBack(reducer = this, stateMapper = stateMapper, actionMapper = actionMapper, environmentMapper = environmentMapper)

fun <State, Action, ViewState, ViewAction, AppEnvironment, ViewEnvironment, StateId> Reducer<ViewState, ViewAction, ViewEnvironment>.forEach(
    states:Lens<State, Map<StateId, ViewState>>,
    actionMapper:Optional<Action, Pair<StateId, ViewAction>>,
    environmentMapper: (AppEnvironment) -> ViewEnvironment
): Reducer<State, Action, AppEnvironment> = { state, action, environment, scope ->
    val reducer = this
    val actionEntry = actionMapper.getOrNull(action)
    if (actionEntry != null){
        val id = actionEntry.first
        val viewAction = actionEntry.second
        val stateAccess = states
            .compose(Index.map<StateId, ViewState>().index(id))
        stateAccess
            .getOrNull(state)
            ?.let { reducer(it, viewAction, environmentMapper(environment), scope) }
            ?.let { (nextState, effect) -> Pair(
                stateAccess.set(state, nextState),
                effect.map { actionMapper.set(action, id to it) }
            ) }
            ?: Pair(state, emptyFlow())
    } else {
        Pair(state, emptyFlow())
    }
}

fun <State, Action, AppEnvironment> combine(
    vararg reducers: Reducer<State, Action, AppEnvironment>
): Reducer<State, Action, AppEnvironment> = { state, action, environment, scope ->
    reducers.fold(Pair(state, emptyFlow())){ result, reducer ->
        val (nextState, nextEffects) = reducer(result.first, action, environment, scope)
        Pair(
            nextState,
            flowOf(result.second, nextEffects).flattenMerge()
        )
    }
}