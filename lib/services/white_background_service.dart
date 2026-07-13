import 'dart:math';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;

/// Service for White Background Enhancement.
/// 
/// Converts paper backgrounds to pure white (#FFFFFF), removes shadows, yellowing,
/// and lighting gradients while keeping text crisp and preserving colored stamps and signatures.
class WhiteBackgroundService {

  /// Enhances the scanned image to have a clean, high-contrast white background.
  /// 
  /// - Removes shadows, yellow paper, and ambient lighting artifacts.
  /// - Keeps text dark black.
  /// - Preserves signatures and stamps (uses saturation threshold checks).
  /// - Enhances tables, borders, and hand-drawn lines.
  static Future<Uint8List> enhanceDocument(Uint8List imageBytes) async {
    try {
      final original = img.decodeImage(imageBytes);
      if (original == null) return imageBytes;

      final width = original.width;
      final height = original.height;

      // Calculate a local background blur radius relative to the document size.
      // This is ideal for modeling shadows, yellowing, and gradient variations.
      final radius = (width ~/ 12).clamp(15, 120);
      final background = _fastBoxBlur(original, radius);

      final enhanced = img.Image(width: width, height: height);

      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          final p = original.getPixel(x, y);
          final bg = background.getPixel(x, y);

          // Get normalized background pixel channels
          final rBg = bg.r == 0 ? 1 : bg.r;
          final gBg = bg.g == 0 ? 1 : bg.g;
          final bBg = bg.b == 0 ? 1 : bg.b;

          // Adaptive illumination division: (Original / Background) * 255
          int rNew = ((p.r / rBg) * 255).round().clamp(0, 255);
          int gNew = ((p.g / gBg) * 255).round().clamp(0, 255);
          int bNew = ((p.b / bBg) * 255).round().clamp(0, 255);

          // Calculate perceived brightness (luminance) of the enhanced pixel
          final double luma = 0.299 * rNew + 0.587 * gNew + 0.114 * bNew;

          // Background whitening threshold
          if (luma > 215) {
            rNew = 255;
            gNew = 255;
            bNew = 255;
          } else if (luma < 75) {
            // Enhance contrast of dark elements like text and tables.
            // Check pixel saturation to determine if it is a colored signature/stamp.
            final maxChan = max(rNew, max(gNew, bNew));
            final minChan = min(rNew, min(gNew, bNew));
            final saturation = maxChan - minChan;

            // If it's grayscale-like (low saturation), deepen it to rich black
            if (saturation < 35) {
              rNew = (rNew * 0.70).round().clamp(0, 255);
              gNew = (gNew * 0.70).round().clamp(0, 255);
              bNew = (bNew * 0.70).round().clamp(0, 255);
            }
            // Otherwise, preserve the vibrant colors of stamps (red, blue, purple, etc.)
          }

          enhanced.setPixel(x, y, img.ColorRgb8(rNew, gNew, bNew));
        }
      }

      return Uint8List.fromList(img.encodeJpg(enhanced, quality: 95));
    } catch (e) {
      debugPrint("White background enhancement exception: $e");
      return imageBytes;
    }
  }

  /// Extremely fast horizontal and vertical box blur implementation.
  /// 
  /// Decoupled from dependencies to guarantee maximum speed, safety,
  /// and robust execution on any Flutter environment.
  static img.Image _fastBoxBlur(img.Image src, int radius) {
    final width = src.width;
    final height = src.height;
    final blurred = img.Image(width: width, height: height);

    // Copy original image
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        blurred.setPixel(x, y, src.getPixel(x, y));
      }
    }

    // Pass 1: Horizontal box blur pass
    final temp = img.Image(width: width, height: height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (int k = -radius; k <= radius; k++) {
          final kx = (x + k).clamp(0, width - 1);
          final px = blurred.getPixel(kx, y);
          rSum += px.r.toInt();
          gSum += px.g.toInt();
          bSum += px.b.toInt();
          count++;
        }
        temp.setPixel(x, y, img.ColorRgb8(rSum ~/ count, gSum ~/ count, bSum ~/ count));
      }
    }

    // Pass 2: Vertical box blur pass
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        int rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (int k = -radius; k <= radius; k++) {
          final ky = (y + k).clamp(0, height - 1);
          final px = temp.getPixel(x, ky);
          rSum += px.r.toInt();
          gSum += px.g.toInt();
          bSum += px.b.toInt();
          count++;
        }
        blurred.setPixel(x, y, img.ColorRgb8(rSum ~/ count, gSum ~/ count, bSum ~/ count));
      }
    }

    return blurred;
  }
}
