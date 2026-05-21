import SwiftUI
import shared

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var wasInBackground = false

    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .background {
                    wasInBackground = true
                } else if newPhase == .active && wasInBackground {
                    // Track app boot when returning from background
                    TelemetryService.shared.trackAppBoot(trigger: "foreground")
                }
            }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
