import CoreImage
import SwiftUI

@MainActor
final class EditorModel: ObservableObject {
    @Published private(set) var original: UIImage?
    @Published private(set) var preview: UIImage?
    @Published private(set) var thumbs: [Effect: UIImage] = [:]
    @Published var effect: Effect = .original { didSet { render() } }
    @Published var amount: Double = 1 { didSet { render() } }
    @Published var adjustments = Adjustments() { didSet { render() } }

    private var previewSource: CIImage?
    private var renderTask: Task<Void, Never>?

    func load(_ image: UIImage) {
        let small = image.resized(maxSide: 1400)
        original = image
        preview = small
        previewSource = CIImage(image: small)
        effect = .original
        amount = 1
        adjustments = Adjustments()
        makeThumbs(from: image.resized(maxSide: 160))
    }

    func reset() {
        effect = .original
        amount = 1
        adjustments = Adjustments()
    }

    private func render() {
        guard let src = previewSource else { return }
        let effect = effect, amount = amount, adjustments = adjustments
        renderTask?.cancel()
        renderTask = Task {
            let img = await Task.detached(priority: .userInitiated) {
                Renderer.render(src, effect: effect, amount: amount, adjustments: adjustments)
            }.value
            if !Task.isCancelled, let img { preview = img }
        }
    }

    private func makeThumbs(from image: UIImage) {
        guard let src = CIImage(image: image) else { return }
        Task {
            let result = await Task.detached(priority: .utility) { () -> [Effect: UIImage] in
                var out: [Effect: UIImage] = [:]
                for e in Effect.allCases {
                    out[e] = Renderer.render(src, effect: e, amount: 1, adjustments: Adjustments())
                }
                return out
            }.value
            thumbs = result
        }
    }

    /// Renders the full-size photo and saves it to the camera roll.
    func save() async -> Bool {
        guard let original else { return false }
        let effect = effect, amount = amount, adjustments = adjustments
        let img = await Task.detached(priority: .userInitiated) { () -> UIImage? in
            guard let src = CIImage(image: original.resized(maxSide: 4096)) else { return nil }
            return Renderer.render(src, effect: effect, amount: amount, adjustments: adjustments)
        }.value
        guard let img else { return false }
        UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
        return true
    }
}
