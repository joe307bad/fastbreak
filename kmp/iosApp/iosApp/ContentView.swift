import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        VStack {
            Text(Greeting().greet())
            Spacer()
            ComposeView() // Embed the Compose UI here
                .frame(height: 200) // Adjust frame size if needed
            Spacer()
        }
    }
}

// Bridge Kotlin's UIViewController to SwiftUI
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return ThemeKt.CupertinoButtonViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
