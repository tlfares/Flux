import SwiftUI

/// The Flux mark: three streams flowing into a play button. Same geometry as
/// `AppIcon.icon`, drawn on a 1024-point grid and scaled to fit.
nonisolated struct FluxStreams: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 1024
        var path = Path()
        for y in [366.0, 512, 658] {
            path.move(to: CGPoint(x: 196 * k, y: y * k))
            path.addCurve(to: CGPoint(x: 410 * k, y: y * k),
                          control1: CGPoint(x: 262 * k, y: (y - 70) * k),
                          control2: CGPoint(x: 332 * k, y: (y + 70) * k))
        }
        return path
    }
}

nonisolated struct FluxPlay: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 1024
        var path = Path()
        path.move(to: CGPoint(x: 512 * k, y: 352 * k))
        path.addLine(to: CGPoint(x: 512 * k, y: 672 * k))
        path.addLine(to: CGPoint(x: 790 * k, y: 512 * k))
        path.closeSubpath()
        // Rounded corners: the triangle plus its own round-joined outline.
        return path.union(path.strokedPath(StrokeStyle(lineWidth: 64 * k, lineJoin: .round)))
    }
}

enum FluxBrand {
    static let top = Color(.displayP3, red: 0.10, green: 0.62, blue: 1.0)
    static let bottom = Color(.displayP3, red: 0.30, green: 0.14, blue: 0.86)
    static var gradient: LinearGradient {
        LinearGradient(colors: [top, bottom], startPoint: .top, endPoint: .bottom)
    }
}

struct FluxLogo: View {
    var size: CGFloat = 88

    var body: some View {
        let k = size / 1024
        ZStack {
            FluxStreams()
                .stroke(.white.opacity(0.7), style: StrokeStyle(lineWidth: 58 * k, lineCap: .round))
            FluxPlay().fill(.white)
        }
        .shadow(color: FluxBrand.bottom.opacity(0.5), radius: 14 * k, y: 10 * k)
        .frame(width: size, height: size)
        .background(FluxBrand.gradient, in: .rect(cornerRadius: size * 0.225, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)
                .strokeBorder(.white.opacity(0.35), lineWidth: max(1, size / 90))
        }
        .shadow(color: FluxBrand.bottom.opacity(0.35), radius: size * 0.14, y: size * 0.07)
        .accessibilityHidden(true)
    }
}
