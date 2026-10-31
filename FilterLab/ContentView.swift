import PhotosUI
import SwiftUI

struct ContentView: View {
    @StateObject private var model = EditorModel()
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var showAdjust = false
    @State private var showOriginal = false
    @State private var toast: String?

    private let accent = Color(red: 1, green: 0.42, blue: 0.55)
    private let hasCamera = UIImagePickerController.isSourceTypeAvailable(.camera)

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 12) {
                header
                previewArea
                if model.original != nil { controls }
            }
            .padding(.bottom, 8)

            if let toast {
                Text(toast)
                    .font(.subheadline.bold())
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule())
                    .transition(.opacity)
            }
        }
        .preferredColorScheme(.dark)
        .tint(accent)
        .onChange(of: pickerItem) { item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    model.load(img)
                }
                pickerItem = nil
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { model.load($0) }.ignoresSafeArea()
        }
    }

    // MARK: - Sections

    private var header: some View {
        HStack(spacing: 20) {
            Text("FilterLab").font(.title2.bold())
            Spacer()
            PhotosPicker(selection: $pickerItem, matching: .images) {
                Image(systemName: "photo.on.rectangle")
            }
            if hasCamera {
                Button { showCamera = true } label: { Image(systemName: "camera") }
            }
            Button { Task { await save() } } label: { Image(systemName: "square.and.arrow.down") }
                .disabled(model.original == nil)
        }
        .font(.title3)
        .padding(.horizontal)
        .padding(.top, 8)
    }

    @ViewBuilder
    private var previewArea: some View {
        if let preview = model.preview {
            Image(uiImage: showOriginal ? (model.original ?? preview) : preview)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal)
                .onLongPressGesture(minimumDuration: 0.05, pressing: { showOriginal = $0 }, perform: {})
                .overlay(alignment: .topLeading) {
                    if showOriginal {
                        Text("Original").font(.caption.bold())
                            .padding(6).background(.black.opacity(0.6), in: Capsule())
                            .padding(24)
                    }
                }
        } else {
            VStack(spacing: 16) {
                Image(systemName: "sparkles").font(.system(size: 48)).foregroundStyle(accent)
                Text("Pick a photo to start").font(.headline)
                HStack(spacing: 12) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label("Gallery", systemImage: "photo")
                    }
                    .buttonStyle(.borderedProminent)
                    if hasCamera {
                        Button { showCamera = true } label: { Label("Camera", systemImage: "camera") }
                            .buttonStyle(.bordered)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var controls: some View {
        VStack(spacing: 10) {
            if model.effect != .original {
                sliderRow("Strength", value: $model.amount, range: 0...1)
            }
            if showAdjust {
                sliderRow("Brightness", value: $model.adjustments.brightness, range: -0.3...0.3)
                sliderRow("Contrast", value: $model.adjustments.contrast, range: 0.5...1.5)
                sliderRow("Saturation", value: $model.adjustments.saturation, range: 0...2)
                sliderRow("Warmth", value: $model.adjustments.warmth, range: -1...1)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Effect.allCases) { e in
                        Button { model.effect = e } label: { effectCell(e) }
                            .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal)
            }

            HStack {
                Button(showAdjust ? "Hide adjust" : "Adjust") {
                    withAnimation { showAdjust.toggle() }
                }
                Spacer()
                Text("Hold photo to compare").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button("Reset") { model.reset() }
            }
            .font(.subheadline.bold())
            .padding(.horizontal)
        }
    }

    // MARK: - Pieces

    private func effectCell(_ e: Effect) -> some View {
        let selected = model.effect == e
        return VStack(spacing: 4) {
            Group {
                if let thumb = model.thumbs[e] {
                    Image(uiImage: thumb).resizable().scaledToFill()
                } else {
                    Color.gray.opacity(0.3)
                }
            }
            .frame(width: 64, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? accent : .clear, lineWidth: 2))
            Text(e.name)
                .font(.caption2)
                .foregroundStyle(selected ? accent : .secondary)
        }
    }

    private func sliderRow(_ title: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        HStack {
            Text(title).font(.caption).frame(width: 74, alignment: .leading)
            Slider(value: value, in: range)
        }
        .padding(.horizontal)
    }

    private func save() async {
        let ok = await model.save()
        withAnimation { toast = ok ? "Saved to Photos" : "Couldn't save" }
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        withAnimation { toast = nil }
    }
}
