import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

interface TabsComponent {

    val stack: Value<ChildStack<*, Child>>

    fun onMenuTabClicked()
    fun onCountersTabClicked()
    fun onCardsTabClicked()
    fun onMultiPaneTabClicked()

    sealed class Child {
        class MenuChild(val component: NotImplementedError) : Child()
    }
}
