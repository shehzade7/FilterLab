import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

enum Effect: String, CaseIterable, Identifiable {
    case original, grain, vhs, glitch, lightLeak, vintage, faded, dreamy, glow
    case cinematic, warm, cool, pop, mono, noir

    var id: String { rawValue }

    var name: String {
        switch self {
        case .original: return "Original"
        case .grain: return "Grain"
        case .vhs: return "VHS"
        case .glitch: return "Glitch"
        case .lightLeak: return "Leak"
        case .vintage: return "Vintage"
        case .faded: return "Faded"
        case .dreamy: return "Dreamy"
        case .glow: return "Glow"
        case .cinematic: return "Cinema"
        case .warm: return "Warm"
        case .cool: return "Cool"
        case .pop: return "Pop"
        case .mono: return "Mono"
        case .noir: return "Noir"
        }
    }

    func apply(to img: CIImage) -> CIImage {
        let e = img.extent
        switch self {
        case .original:
            return img
        case .grain:
            return img.addingGrain(0.7)
        case .vhs:
            return img.rgbSplit(offset: e.width * 0.004)
                .colorControls(saturation: 1.3, contrast: 1.05)
                .scanlines()
                .addingGrain(0.3)
        case .glitch:
            var out = img.rgbSplit(offset: e.width * 0.012)
            let bands: [(CGFloat, CGFloat, CGFloat)] = [(0.18, 0.05, 0.04), (0.52, 0.03, -0.06), (0.78, 0.04, 0.03)]
            for (y, h, dx) in bands {
                let rect = CGRect(x: e.minX, y: e.minY + e.height * y, width: e.width, height: e.height * h)
                let band = out.cropped(to: rect).transformed(by: CGAffineTransform(translationX: e.width * dx, y: 0))
                out = band.composited(over: out)
            }
            return out.colorControls(contrast: 1.15).cropped(to: e)
        case .lightLeak:
            let r = max(e.width, e.height)
            let orange = img.radialLight(center: CGPoint(x: e.maxX, y: e.maxY), radius: r * 0.8,
                                         color: CIColor(red: 1, green: 0.45, blue: 0.1))
            let pink = img.radialLight(center: CGPoint(x: e.minX, y: e.minY), radius: r * 0.6,
                                       color: CIColor(red: 1, green: 0.2, blue: 0.5))
            return img.screen(orange).screen(pink).addingGrain(0.2)
        case .vintage:
            return img.applyingFilter("CISepiaTone", parameters: [kCIInputIntensityKey: 0.45])
                .fadedCurve()
                .vignette()
                .addingGrain(0.35)
        case .faded:
            return img.fadedCurve().colorControls(saturation: 0.75)
        case .dreamy:
            return img.bloom(radius: e.width * 0.03, intensity: 1)
                .colorMatrix(r: 1, g: 0.97, b: 1, bias: CIVector(x: 0.06, y: 0.02, z: 0.06, w: 0))
                .fadedCurve()
        case .glow:
            return img.bloom(radius: e.width * 0.025, intensity: 0.9)
        case .cinematic:
            var out = img
                .colorMatrix(r: 1.08, g: 1.0, b: 0.9, bias: CIVector(x: -0.02, y: 0, z: 0.06, w: 0))
                .colorControls(contrast: 1.12)
            if e.width > e.height {
                let barH = e.height * 0.1
                let black = CIImage(color: .black)
                let bars = black.cropped(to: CGRect(x: e.minX, y: e.minY, width: e.width, height: barH))
                    .composited(over: black.cropped(to: CGRect(x: e.minX, y: e.maxY - barH, width: e.width, height: barH)))
                out = bars.composited(over: out)
            }
            return out.cropped(to: e)
        case .warm:
            return img.colorMatrix(r: 1.1, g: 1.02, b: 0.88).colorControls(saturation: 1.1)
        case .cool:
            return img.colorMatrix(r: 0.9, g: 1.0, b: 1.1)
        case .pop:
            return img.applyingFilter("CIPhotoEffectChrome")
                .applyingFilter("CIVibrance", parameters: ["inputAmount": 0.5])
        case .mono:
            return img.applyingFilter("CIPhotoEffectTonal").addingGrain(0.25)
        case .noir:
            return img.applyingFilter("CIPhotoEffectNoir").vignette()
        }
    }
}

struct Adjustments: Equatable {
    var brightness = 0.0   // -0.3...0.3
    var contrast = 1.0     // 0.5...1.5
    var saturation = 1.0   // 0...2
    var warmth = 0.0       // -1...1

    func apply(to img: CIImage) -> CIImage {
        guard self != Adjustments() else { return img }
        var out = img.applyingFilter("CIColorControls", parameters: [
            kCIInputBrightnessKey: brightness,
            kCIInputContrastKey: contrast,
            kCIInputSaturationKey: saturation,
        ])
        if warmth != 0 {
            out = out.colorMatrix(r: 1 + 0.12 * warmth, g: 1, b: 1 - 0.12 * warmth)
        }
        return out
    }
}

enum Renderer {
    static let context = CIContext()

    static func render(_ src: CIImage, effect: Effect, amount: Double, adjustments: Adjustments) -> UIImage? {
        var out = effect.apply(to: src).cropped(to: src.extent)
        if amount < 1 { out = src.mixed(with: out, amount: amount) }
        out = adjustments.apply(to: out)
        guard let cg = context.createCGImage(out, from: src.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

// MARK: - Building blocks

extension CIImage {
    func mixed(with other: CIImage, amount: Double) -> CIImage {
        let f = CIFilter.dissolveTransition()
        f.inputImage = self
        f.targetImage = other
        f.time = Float(amount)
        return f.outputImage!.cropped(to: extent)
    }

    func colorMatrix(r: CGFloat, g: CGFloat, b: CGFloat, bias: CIVector = CIVector(x: 0, y: 0, z: 0, w: 0)) -> CIImage {
        applyingFilter("CIColorMatrix", parameters: [
            "inputRVector": CIVector(x: r, y: 0, z: 0, w: 0),
            "inputGVector": CIVector(x: 0, y: g, z: 0, w: 0),
            "inputBVector": CIVector(x: 0, y: 0, z: b, w: 0),
            "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 1),
            "inputBiasVector": bias,
        ])
    }

    func colorControls(saturation: Double = 1, contrast: Double = 1, brightness: Double = 0) -> CIImage {
        applyingFilter("CIColorControls", parameters: [
            kCIInputSaturationKey: saturation,
            kCIInputContrastKey: contrast,
            kCIInputBrightnessKey: brightness,
        ])
    }

    func addingGrain(_ strength: Double) -> CIImage {
        let scale = max(1, extent.width / 1200)
        let noise = CIFilter.randomGenerator().outputImage!
            .transformed(by: CGAffineTransform(scaleX: scale, y: scale))
            .applyingFilter("CIColorMatrix", parameters: [
                "inputRVector": CIVector(x: 0, y: 1, z: 0, w: 0),
                "inputGVector": CIVector(x: 0, y: 1, z: 0, w: 0),
                "inputBVector": CIVector(x: 0, y: 1, z: 0, w: 0),
                "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 0),
                "inputBiasVector": CIVector(x: 0, y: 0, z: 0, w: 1),
            ])
            .cropped(to: extent)
        let grained = noise.applyingFilter("CISoftLightBlendMode", parameters: [kCIInputBackgroundImageKey: self])
        return mixed(with: grained, amount: strength)
    }

    func rgbSplit(offset: CGFloat) -> CIImage {
        let red = colorMatrix(r: 1, g: 0, b: 0).transformed(by: CGAffineTransform(translationX: offset, y: 0))
        let green = colorMatrix(r: 0, g: 1, b: 0)
        let blue = colorMatrix(r: 0, g: 0, b: 1).transformed(by: CGAffineTransform(translationX: -offset, y: 0))
        return red
            .applyingFilter("CIAdditionCompositing", parameters: [kCIInputBackgroundImageKey: green])
            .applyingFilter("CIAdditionCompositing", parameters: [kCIInputBackgroundImageKey: blue])
            .cropped(to: extent)
    }

    func scanlines() -> CIImage {
        let f = CIFilter.stripesGenerator()
        f.color0 = CIColor(red: 0, green: 0, blue: 0, alpha: 0.22)
        f.color1 = CIColor(red: 0, green: 0, blue: 0, alpha: 0)
        f.width = Float(max(1.5, extent.height / 400))
        f.sharpness = 0.5
        let lines = f.outputImage!.transformed(by: CGAffineTransform(rotationAngle: .pi / 2)).cropped(to: extent)
        return lines.composited(over: self).cropped(to: extent)
    }

    func radialLight(center: CGPoint, radius: CGFloat, color: CIColor) -> CIImage {
        let f = CIFilter.radialGradient()
        f.center = center
        f.radius0 = 0
        f.radius1 = Float(radius)
        f.color0 = color
        f.color1 = CIColor(red: 0, green: 0, blue: 0)
        return f.outputImage!.cropped(to: extent)
    }

    func screen(_ top: CIImage) -> CIImage {
        top.applyingFilter("CIScreenBlendMode", parameters: [kCIInputBackgroundImageKey: self]).cropped(to: extent)
    }

    func fadedCurve() -> CIImage {
        applyingFilter("CIToneCurve", parameters: [
            "inputPoint0": CIVector(x: 0, y: 0.12),
            "inputPoint1": CIVector(x: 0.25, y: 0.3),
            "inputPoint2": CIVector(x: 0.5, y: 0.52),
            "inputPoint3": CIVector(x: 0.75, y: 0.75),
            "inputPoint4": CIVector(x: 1, y: 0.95),
        ])
    }

    func vignette() -> CIImage {
        applyingFilter("CIVignette", parameters: [kCIInputIntensityKey: 1.0, kCIInputRadiusKey: 1.5])
    }

    func bloom(radius: CGFloat, intensity: Double) -> CIImage {
        let f = CIFilter.bloom()
        f.inputImage = clampedToExtent()
        f.radius = Float(radius)
        f.intensity = Float(intensity)
        return f.outputImage!.cropped(to: extent)
    }
}

extension UIImage {
    /// Redraws the image upright (bakes in orientation) and caps its longest side.
    func resized(maxSide: CGFloat) -> UIImage {
        let factor = min(1, maxSide / max(size.width, size.height))
        let newSize = CGSize(width: (size.width * factor).rounded(), height: (size.height * factor).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
