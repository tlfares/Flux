import SwiftUI

@main
struct FluxApp: App {
    @State private var account = AccountStore()
    @State private var catalog = Catalog()
    @State private var library = Library()
    @State private var epg = EPG()
    @State private var player = PlayerCenter()
    @AppStorage(AppAppearance.storageKey) private var appearance = AppAppearance.dark.rawValue

    init() {
        AppAppearance.startApplying()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(account)
                .environment(catalog)
                .environment(library)
                .environment(epg)
                .environment(player)
                .tint(FluxTint.blue)
                .onChange(of: appearance, initial: true) { old, new in AppAppearance.apply(animated: old != new) }
                .task { player.library = library }
        }
    }
}
