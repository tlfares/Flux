import SwiftUI

/// Flux's accent, from the app icon: its blue is the tint of everything
/// drawn in `.tint` (text and symbols stay one plain color); the blue
/// running into the icon's violet only fills shapes, like a chosen chip.
enum FluxTint {
    static let blue = Color(.displayP3, red: 0.10, green: 0.62, blue: 1.0)
    static let violet = Color(.displayP3, red: 0.30, green: 0.14, blue: 0.86)

    /// Along a row: chips and capsules read better left to right.
    static let horizontal = LinearGradient(colors: [blue, violet], startPoint: .leading, endPoint: .trailing)
}
