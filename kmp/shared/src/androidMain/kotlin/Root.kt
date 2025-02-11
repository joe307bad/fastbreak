import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import com.arkivanov.decompose.extensions.android.ViewContext
import com.arkivanov.decompose.extensions.android.layoutInflater
import com.arkivanov.decompose.extensions.android.stack.StackRouterView
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.subscribe
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.joebad.fastbreak.R
import androidx.compose.material.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import com.arkivanov.sample.shared.FadeTransition
import com.arkivanov.sample.shared.viewSwitcher

interface ListComponent {
    val model: Value<Model>

    fun onItemClicked(item: String)

    data class Model(
        val items: List<String>,
    )
}

interface DetailsComponent

class DefaultDetailsComponent(
    componentContext: ComponentContext,
    item: String,
    onFinished: () -> Unit,
) : DetailsComponent, ComponentContext by componentContext

class DefaultListComponent(
    componentContext: ComponentContext,
    private val onItemSelected: (item: String) -> Unit,
) : ListComponent {
    override val model: Value<ListComponent.Model> =
        MutableValue(ListComponent.Model(items = List(100) { "Item $it" }))

    override fun onItemClicked(item: String) {
        onItemSelected(item)
    }
}

interface RootComponent {

    val stack: Value<ChildStack<*, Child>>

    // It's possible to pop multiple screens at a time on iOS
    fun onBackClicked(toIndex: Int)

    // Defines all possible child components
    sealed class Child {
        class ListChild(val component: ListComponent) : Child()
        class DetailsChild(val component: DetailsComponent) : Child()

        class TabsChild(val component: TabsComponent) : Child()  // /transitions
    }
}

@Composable
fun ListContent(component: ListComponent, modifier: Modifier = Modifier) {
    val model by component.model.subscribeAsState()

    LazyColumn {
        items(items = model.items) { item ->
            Text(
                text = item,
                modifier = Modifier.clickable { component.onItemClicked(item = item) },
            )
        }
    }
}

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    MaterialTheme {
        Children(
            stack = component.stack,
            modifier = modifier,
            animation = stackAnimation(fade()),
        ) {
            when (val child = it.instance) {
                is RootComponent.Child.TabsChild -> RootComponent.Child.TabsChild(component = child.component)
                is RootComponent.Child.DetailsChild -> RootComponent.Child.DetailsChild(component = child.component)
                is RootComponent.Child.ListChild -> ListContent(component = child.component)
            }
        }
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.List, // The initial child component is List
            handleBackButton = true, // Automatically pop from the stack on back button presses
            childFactory = ::child,
        )

    private fun child(config: Config, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            is Config.List -> RootComponent.Child.ListChild(listComponent(componentContext))
            is Config.Details -> RootComponent.Child.DetailsChild(
                detailsComponent(
                    componentContext,
                    config
                )
            )
        }

    private fun listComponent(componentContext: ComponentContext): ListComponent =
        DefaultListComponent(
            componentContext = componentContext,
            onItemSelected = { item: String -> // Supply dependencies and callbacks
                navigation.push(Config.Details(item = item)) // Push the details component
            },
        )

    private fun detailsComponent(componentContext: ComponentContext, config: Config.Details): DetailsComponent =
        DefaultDetailsComponent(
            componentContext = componentContext,
            item = config.item, // Supply arguments from the configuration
            onFinished = navigation::pop, // Pop the details component
        )

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @Serializable // kotlinx-serialization plugin must be applied
    private sealed interface Config {
        @Serializable
        data object List : Config

        @Serializable
        data class Details(val item: String) : Config
    }
}

@ExperimentalDecomposeApi
@Suppress("FunctionName") // Factory function
fun ViewContext.TabsView(component: TabsComponent): View {
    val layout = layoutInflater.inflate(R.layout.tabs, parent, false)
    val router: StackRouterView = layout.findViewById(R.id.router)

    router.children(
        stack = component.stack,
        lifecycle = lifecycle,
        replaceChildView = viewSwitcher(transition = FadeTransition) { child ->
            when (child) {
                is TabsComponent.Child.MenuChild -> NotImplementedView(title = "Decompose Sample")
                else -> NotImplementedView(title = "qwdqwDecompose Sample")
            }
        },
    )

    val navigationView: BottomNavigationView = layout.findViewById(R.id.navigation_view)

    val listener =
        BottomNavigationView.OnNavigationItemSelectedListener { item ->
            when (val id = item.itemId) {
                R.id.tab_menu -> component.onMenuTabClicked()
                R.id.tab_counters -> component.onCountersTabClicked()
                R.id.tab_cards -> component.onCardsTabClicked()
                R.id.tab_multipane -> component.onMultiPaneTabClicked()
                else -> error("Unrecognized item id: $id")
            }

            true
        }

    navigationView.setOnNavigationItemSelectedListener(listener)

    component.stack.subscribe(lifecycle) { state ->
        navigationView.setOnNavigationItemSelectedListener(null)

        navigationView.selectedItemId =
            when (state.active.instance) {
                is TabsComponent.Child.MenuChild -> R.id.tab_menu
                else -> 0
            }

        navigationView.setOnNavigationItemSelectedListener(listener)
    }

    return layout
}


@ExperimentalDecomposeApi
@Suppress("FunctionName") // Factory function
fun ViewContext.RootView(component: RootComponent): View {
    val layout = layoutInflater.inflate(R.layout.root, parent, false)
    val router: StackRouterView = layout.findViewById(R.id.router)

    router.children(
        stack = component.stack,
        lifecycle = lifecycle,
        replaceChildView = viewSwitcher { child ->
            when (child) {
                is RootComponent.Child.TabsChild -> TabsView(child.component)
                else -> NotImplementedView(title = "qwdqwDecompose Sample")
            }
        },
    )

    return layout
}