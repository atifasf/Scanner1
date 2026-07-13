import 'dart:math';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;

/// Service for perspective correction and cropping.
/// 
/// Corrects tilted document perspectives, crops the boundary,
/// and outputs a perfectly rectangular image while maintaining the original resolution.
class PerspectiveService {

  /// Applies perspective transform using 4 detected corners.
  /// 
  /// [corners] should contain exactly 4 points in order:
  /// 0: Top-Left, 1: Top-Right, 2: Bottom-Right, 3: Bottom-Left.
  static Future<Uint8List> applyPerspectiveCorrection(
    Uint8List imageBytes, {
    List<Offset>? corners,
  }) async {
    try {
      final srcImage = img.decodeImage(imageBytes);
      if (srcImage == null) return imageBytes;

      final w = srcImage.width;
      final h = srcImage.height;

      // 1. Define or fallback corners if not provided
      final pts = corners ?? [
        Offset(w * 0.08, h * 0.10),  // Top-Left
        Offset(w * 0.92, h * 0.08),  // Top-Right
        Offset(w * 0.88, h * 0.92),  // Bottom-Right
        Offset(w * 0.12, h * 0.90),  // Bottom-Left
      ];

      final p0 = pts[0]; // TL
      final p1 = pts[1]; // TR
      final p2 = pts[2]; // BR
      final p3 = pts[3]; // BL

      // 2. Calculate the target rectangle size to preserve the original resolution
      final topWidth = _distance(p0, p1);
      final bottomWidth = _distance(p3, p2);
      final targetWidth = max(topWidth, bottomWidth).round();

      final leftHeight = _distance(p0, p3);
      final rightHeight = _distance(p1, p2);
      final targetHeight = max(leftHeight, rightHeight).round();

      // Create destination image buffer
      final destImage = img.Image(width: targetWidth, height: targetHeight);

      // 3. Solve for Projective Mapping from unit square to quadrilateral
      // Let's compute the mapping parameters mapping normalized target (u, v) in [0, 1] to source (x, y)
      final x0 = p0.dx; final y0 = p0.dy;
      final x1 = p1.dx; final y1 = p1.dy;
      final x2 = p2.dx; final y2 = p2.dy;
      final x3 = p3.dx; final y3 = p3.dy;

      final dx1 = x1 - x2;
      final dx2 = x3 - x2;
      final dy1 = y1 - y2;
      final dy2 = y3 - y2;

      final sumX = x0 - x1 + x2 - x3;
      final sumY = y0 - y1 + y2 - y3;

      double a, b, c, d, e, f, g, h;

      if (sumX.abs() < 1e-5 && sumY.abs() < 1e-5) {
        // Affine Mapping
        a = x1 - x0;
        b = x2 - x1;
        c = x0;
        d = y1 - y0;
        e = y2 - y1;
        f = y0;
        g = 0.0;
        h = 0.0;
      } else {
        // Perspective Mapping
        final denominator = dx1 * dy2 - dy1 * dx2;
        if (denominator.abs() < 1e-5) {
          return imageBytes;
        }
        g = (sumX * dy2 - sumY * dx2) / denominator;
        h = (dx1 * sumY - dy1 * sumX) / denominator;
        a = x1 - x0 + g * x1;
        b = x3 - x0 + h * x3;
        c = x0;
        d = y1 - y0 + g * y1;
        e = y3 - y0 + h * y3;
        f = y0;
      }

      // 4. Perform Inverse Projective Mapping with Bilinear Interpolation
      for (int yTarget = 0; yTarget < targetHeight; yTarget++) {
        final double v = yTarget / (targetHeight - 1);
        for (int xTarget = 0; xTarget < targetWidth; xTarget++) {
          final double u = xTarget / (targetWidth - 1);

          // Map back to source coordinates (x, y)
          final double denominator = g * u + h * v + 1.0;
          final double srcX = (a * u + b * v + c) / denominator;
          final double srcY = (d * u + e * v + f) / denominator;

          // Check if coordinates lie within the source image bounds
          if (srcX >= 0 && srcX < w - 1 && srcY >= 0 && srcY < h - 1) {
            // Apply Bilinear Interpolation for premium quality and no aliasing
            final int xFloor = srcX.floor();
            final int yFloor = srcY.floor();
            final double dx = srcX - xFloor;
            final double dy = srcY - yFloor;

            final pTL = srcImage.getPixel(xFloor, yFloor);
            final pTR = srcImage.getPixel(xFloor + 1, yFloor);
            final pBL = srcImage.getPixel(xFloor, yFloor + 1);
            final pBR = srcImage.getPixel(xFloor + 1, yFloor + 1);

            // Interpolate each RGB channel independently
            final r = _interpolate(pTL.r, pTR.r, pBL.r, pBR.r, dx, dy);
            final gChan = _interpolate(pTL.g, pTR.g, pBL.g, pBR.g, dx, dy);
            final bChan = _interpolate(pTL.b, pTR.b, pBL.b, pBR.b, dx, dy);

            destImage.setPixel(xTarget, yTarget, img.ColorRgb8(r.round(), gChan.round(), bChan.round()));
          } else {
            // Fallback to white padding if outside bounds
            destImage.setPixel(xTarget, yTarget, img.ColorRgb8(255, 255, 255));
          }
        }
      }

      return Uint8List.fromList(img.encodeJpg(destImage, quality: 95));
    } catch (e) {
      debugPrint("Perspective correction exception: $e");
      return imageBytes;
    }
  }

  /// Calculates the Euclidean distance between two points.
  static double _distance(Offset a, Offset b) {
    return sqrt(pow(a.dx - b.dx, 2) + pow(a.dy - b.dy, 2));
  }

  /// Helper for bilinear interpolation calculation
  static double _interpolate(num tl, num tr, num bl, num br, double dx, double dy) {
    return (1 - dx) * (1 - dy) * tl +
           dx * (1 - dy) * tr +
           (1 - dx) * dy * bl +
           dx * dy * br;
  }
}
