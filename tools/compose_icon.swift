import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Builds the Android adaptive launcher icon's background layer from the iOS app icon.
//
// The iOS icon (aura-apps/Aura/Assets.xcassets/AppIcon.appiconset/icon-1024.png) is a full-bleed square:
// a warm sun glow top-left over a blue sky, the white "A", and wind swirls. Android adaptive icons are
// masked to the launcher's shape (a circle, a squircle) and reserve an outer bleed, so a full-bleed drop
// would clip the A's wide leg corners. Instead this recreates the sky gradient (colours sampled from the
// source) to fill the whole canvas, then draws the artwork centred at a fraction that keeps the A inside
// the mask's safe zone, with an optional upward nudge so the top and bottom margins match.
//
// The result is used as the adaptive icon's background layer (foreground transparent), see
// res/mipmap-anydpi-v26/ic_launcher.xml.
//
// Usage:
//   swift tools/compose_icon.swift <in.png> <out.png> <sizePx> <scaleFraction> [<yShiftUpFraction>]
//
// The shipped icon was generated for each density bucket (108/162/216/324/432 px) with:
//   scaleFraction = 0.64   yShiftUpFraction = 0.035
//
// Coordinate note: a CGBitmapContext is y-up (origin bottom-left) and ImageIO saves it upright, so we draw
// WITHOUT any manual flip and express "top" as high y. CGContext.draw() renders the image the right way up.

let args = CommandLine.arguments
guard args.count >= 5, let size = Int(args[3]), let scale = Double(args[4]) else {
    FileHandle.standardError.write("usage: compose_icon <in.png> <out.png> <sizePx> <scale> [yShiftUp]\n".data(using: .utf8)!)
    exit(2)
}
let yShiftUp = args.count >= 6 ? (Double(args[5]) ?? 0) : 0
let inPath = args[1], outPath = args[2]

func loadImage(_ path: String) -> CGImage? {
    guard let s = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil) else { return nil }
    return CGImageSourceCreateImageAtIndex(s, 0, nil)
}
guard let src = loadImage(inPath) else {
    FileHandle.standardError.write("cannot load \(inPath)\n".data(using: .utf8)!); exit(1)
}

let cs = CGColorSpaceCreateDeviceRGB()
let bmp = CGImageAlphaInfo.premultipliedLast.rawValue

// Sample colours from the source. Buffer row 0 = bottom (y-up), so a top-fraction fy maps to row (1-fy).
func sample(_ img: CGImage) -> ([CGFloat], [CGFloat], [CGFloat]) {
    let W = 80, H = 80
    var buf = [UInt8](repeating: 0, count: W*H*4)
    let c = CGContext(data: &buf, width: W, height: H, bitsPerComponent: 8,
                      bytesPerRow: W*4, space: cs, bitmapInfo: bmp)!
    c.draw(img, in: CGRect(x: 0, y: 0, width: W, height: H))
    func px(_ fx: Double, _ fyTop: Double) -> [CGFloat] {
        let x = min(W-1, max(0, Int(fx * Double(W))))
        let y = min(H-1, max(0, Int((1.0 - fyTop) * Double(H))))
        let i = (y*W + x)*4
        return [CGFloat(buf[i])/255, CGFloat(buf[i+1])/255, CGFloat(buf[i+2])/255]
    }
    return (px(0.62, 0.03), px(0.90, 0.97), px(0.07, 0.05))  // top sky, bottom-right deep, warm corner
}
let (topC, botC, warmC) = sample(src)

var out = [UInt8](repeating: 0, count: size*size*4)
let ctx = CGContext(data: &out, width: size, height: size, bitsPerComponent: 8,
                    bytesPerRow: size*4, space: cs, bitmapInfo: bmp)!
let S = CGFloat(size)

// Diagonal sky gradient: light/warm top-left -> deep blue bottom-right.
let grad = CGGradient(colorsSpace: cs, colors: [
    CGColor(colorSpace: cs, components: [topC[0], topC[1], topC[2], 1])!,
    CGColor(colorSpace: cs, components: [botC[0], botC[1], botC[2], 1])!,
] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(grad,
    start: CGPoint(x: S*0.20, y: S*0.85), end: CGPoint(x: S*0.82, y: S*0.12),
    options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])

// Warm sun glow, radial from the top-left.
let glow = CGGradient(colorsSpace: cs, colors: [
    CGColor(colorSpace: cs, components: [warmC[0], warmC[1], warmC[2], 0.95])!,
    CGColor(colorSpace: cs, components: [warmC[0], warmC[1], warmC[2], 0.0])!,
] as CFArray, locations: [0, 1])!
ctx.drawRadialGradient(glow,
    startCenter: CGPoint(x: S*0.14, y: S*0.88), startRadius: 0,
    endCenter: CGPoint(x: S*0.14, y: S*0.88), endRadius: S*0.62, options: [])

// Source artwork centred at the requested scale, shifted up by yShiftUp (y-up: +y is up).
let drawn = S * CGFloat(scale)
let off = (S - drawn) / 2
ctx.draw(src, in: CGRect(x: off, y: off + S * CGFloat(yShiftUp), width: drawn, height: drawn))

guard let image = ctx.makeImage(),
      let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: outPath) as CFURL,
                                                 UTType.png.identifier as CFString, 1, nil) else { exit(1) }
CGImageDestinationAddImage(dest, image, nil)
if !CGImageDestinationFinalize(dest) { exit(1) }
print("wrote \(outPath) (\(size)px, scale \(scale), yShiftUp \(yShiftUp))")
