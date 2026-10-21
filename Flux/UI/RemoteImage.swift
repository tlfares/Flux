import SwiftUI
import ImageIO

/// Posters and logos, downsampled to the size they're drawn at and kept in
/// memory and on disk. AsyncImage decodes full-size on every appearance,
/// which stutters in a fast-scrolling grid.
/// NSCache is thread-safe; it just isn't marked Sendable.
nonisolated final class ImagePipeline: Sendable {
    static let shared = ImagePipeline()

    nonisolated(unsafe) private let memory: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 80 * 1024 * 1024
        return cache
    }()

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.urlCache = URLCache(memoryCapacity: 0, diskCapacity: 300 * 1024 * 1024, directory: URL.cachesDirectory.appending(path: "Images"))
        config.requestCachePolicy = .returnCacheDataElseLoad
        config.timeoutIntervalForRequest = 20
        config.httpMaximumConnectionsPerHost = 8
        return URLSession(configuration: config)
    }()

    func cached(_ url: URL, pixels: Int) -> UIImage? {
        memory.object(forKey: "\(pixels)|\(url.absoluteString)" as NSString)
    }

    @concurrent
    func image(_ url: URL, pixels: Int) async -> UIImage? {
        let key = "\(pixels)|\(url.absoluteString)" as NSString
        if let image = memory.object(forKey: key) { return image }
        guard let (data, _) = try? await session.data(from: url), !Task.isCancelled else { return nil }
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, options) else { return nil }
        let thumb = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: pixels,
        ] as CFDictionary
        guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, thumb) else { return nil }
        let image = UIImage(cgImage: cg)
        memory.setObject(image, forKey: key, cost: cg.bytesPerRow * cg.height)
        return image
    }
}

struct RemoteImage<Placeholder: View>: View {
    let url: String?
    /// The longest side it's drawn at, in points.
    var size: CGFloat = 200
    var contentMode: ContentMode = .fill
    @ViewBuilder var placeholder: Placeholder

    @Environment(\.displayScale) private var scale
    /// What was loaded, and for which address: a view handed a new one
    /// (zapping, a recycled row) never shows the last one's picture.
    @State private var loaded: (url: String, image: UIImage)?

    private var resolved: URL? { url.flatMap { URL(string: $0) } }
    private var pixels: Int { Int(size * scale) }

    var body: some View {
        ZStack {
            if let image = resolved.flatMap({ ImagePipeline.shared.cached($0, pixels: pixels) }) ?? (loaded?.url == url ? loaded?.image : nil) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
            } else {
                placeholder
            }
        }
        .task(id: url) {
            guard let resolved, ImagePipeline.shared.cached(resolved, pixels: pixels) == nil else { return }
            guard let image = await ImagePipeline.shared.image(resolved, pixels: pixels), !Task.isCancelled, let url else { return }
            withAnimation(.easeOut(duration: 0.2)) { loaded = (url, image) }
        }
    }
}
