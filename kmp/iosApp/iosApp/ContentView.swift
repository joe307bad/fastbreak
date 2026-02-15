import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            bracketViewFactory: { FilamentBracketUIView() }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
