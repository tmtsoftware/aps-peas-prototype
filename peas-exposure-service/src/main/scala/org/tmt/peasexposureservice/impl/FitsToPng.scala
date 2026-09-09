package org.tmt.peasexposureservice.impl

import nom.tam.fits.{Fits, ImageHDU}

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}
import javax.imageio.ImageIO
import scala.util.Try

/**
 * Reads a FITS image and renders it as a downsampled, linearly-scaled
 * grayscale PNG.
 *
 * Deliberately the simplest thing that produces a recognizable preview:
 *  - Reads HDU 0 only (the primary image HDU). Multi-extension FITS files
 *    (e.g. a separate HDU per detector) aren't handled.
 *    TODO(Scott): confirm which HDU(s) the real exposure files actually use
 *    once the DMS exposure-store format is nailed down -- may need to loop
 *    over HDUs or take an HDU index as a parameter.
 *  - Scaling is plain linear min/max -> 0..255, no astronomical stretch
 *    (zscale/asinh/etc. as DS9-style viewers use).
 *    TODO(Scott): confirm whether this "low res" preview needs a proper
 *    stretch to be usable, or whether linear is fine for the prototype.
 *  - Downsampling is block-max pooling, not smooth interpolation or
 *    flux-conserving binning. Point-like features (a Hartmann/lenslet spot
 *    pattern is exactly this) are only a few pixels wide -- averaging or
 *    bilinear-interpolating them into a much smaller output blends each
 *    spot's bright core with its surrounding dim skirts and black
 *    background, crushing peak values that were near the data's max down
 *    to a fraction of it (confirmed on a real exposure: a peak of ~500
 *    averaged down to ~157 over a 16x16 source block, i.e. ~80/255 grey --
 *    visually near-black at small preview sizes). Taking the max of each
 *    block instead preserves each spot's true brightness. Trade-off: for a
 *    large smoothly-varying source (as opposed to sparse point sources)
 *    max-pooling will look blockier / more aliased than a smooth scale.
 *  - Default output size is 1024px (not 512). At 512, a ~33px-wide spot in
 *    an 8192px source only occupies ~2 output pixels after max-pooling --
 *    not enough room for its Gaussian falloff to be visible as a gradient,
 *    so every spot looks like a single hard on/off dot. 1024 gives each
 *    spot roughly twice the pixels, which helps some but doesn't fully
 *    solve it -- spot width relative to source size is the limiting
 *    factor, not the downsample target alone.
 *  - A small cosmetic halo is added after max-pooling: the max-pooled
 *    image is blurred, then blended back in via an elementwise max against
 *    the un-blurred version, so every dot gets a soft visible falloff
 *    without diluting the true peak pixel itself (a plain blur alone would
 *    re-introduce the same dilution max-pooling was added to fix -- tested
 *    that first: it dropped a peak of 255 down to 106).
 */
object FitsToPng {

  val DefaultMaxDimensionPx = 1024

  def convert(fitsFile: File, maxDimensionPx: Int = DefaultMaxDimensionPx): Try[Array[Byte]] =
    Try {
      val fits = new Fits(fitsFile)
      try {
        val hdu = fits.getHDU(0) match {
          case imageHdu: ImageHDU => imageHdu
          case other =>
            throw new IllegalArgumentException(
              s"HDU 0 of ${fitsFile.getName} is not an image HDU (got ${other.getClass.getSimpleName})"
            )
        }

        val pixels = toDoubleGrid(hdu.getKernel)
        val height = pixels.length
        val width  = if (height > 0) pixels(0).length else 0
        if (width == 0 || height == 0)
          throw new IllegalArgumentException(s"${fitsFile.getName}: empty image data")

        val (dataMin, dataMax) = minMax(pixels)
        val grayscale = toGrayscaleSamples(pixels, width, height, dataMin, dataMax)
        val (pooled, pooledWidth, pooledHeight) = downsampleMaxPool(grayscale, width, height, maxDimensionPx)
        val softened = addHalo(pooled, pooledWidth, pooledHeight)
        val image = toBufferedImage(softened, pooledWidth, pooledHeight)

        val out = new ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        out.toByteArray
      } finally {
        fits.close()
      }
    }

  // ImageHDU#getKernel's actual runtime array type depends on BITPIX
  // (Array[Array[Short]], Array[Array[Int]], Array[Array[Float]],
  // Array[Array[Double]], ...). Normalise up front so the rest of the
  // pipeline doesn't care about BITPIX.
  private def toDoubleGrid(kernel: Any): Array[Array[Double]] = kernel match {
    case a: Array[Array[Double]] => a
    case a: Array[Array[Float]]  => a.map(_.map(_.toDouble))
    case a: Array[Array[Int]]    => a.map(_.map(_.toDouble))
    case a: Array[Array[Short]]  => a.map(_.map(_.toDouble))
    case other =>
      throw new IllegalArgumentException(s"Unsupported FITS pixel array type: ${other.getClass.getName}")
  }

  private def minMax(pixels: Array[Array[Double]]): (Double, Double) = {
    var lo = Double.MaxValue
    var hi = Double.MinValue
    var y  = 0
    while (y < pixels.length) {
      var x = 0
      val row = pixels(y)
      while (x < row.length) {
        val v = row(x)
        if (v < lo) lo = v
        if (v > hi) hi = v
        x += 1
      }
      y += 1
    }
    (lo, hi)
  }

  private def toGrayscaleSamples(
      pixels: Array[Array[Double]],
      width: Int,
      height: Int,
      dataMin: Double,
      dataMax: Double
  ): Array[Array[Int]] = {
    val range = if (dataMax > dataMin) dataMax - dataMin else 1.0
    Array.tabulate(height, width) { (y, x) =>
      val normalized = (pixels(y)(x) - dataMin) / range
      math.min(255, math.max(0, math.round(normalized * 255.0).toInt))
    }
  }

  // FITS row 0 is conventionally the bottom of the image; flip vertically
  // so the PNG displays right-side-up in a standard <img> element.
  private def toBufferedImage(gray: Array[Array[Int]], width: Int, height: Int): BufferedImage = {
    val image  = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val raster = image.getRaster
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        raster.setSample(x, height - 1 - y, 0, gray(y)(x))
        x += 1
      }
      y += 1
    }
    image
  }

  // Reduces width x height down to at most maxDimensionPx on the longer side by
  // taking the MAX sample in each source block, not the average -- see the
  // class doc for why. Block size is derived the same way the old smooth-scale
  // version computed its scale factor, so output dimensions are unchanged.
  private def downsampleMaxPool(
      gray: Array[Array[Int]],
      width: Int,
      height: Int,
      maxDimensionPx: Int
  ): (Array[Array[Int]], Int, Int) = {
    val largest = math.max(width, height)
    if (largest <= maxDimensionPx) (gray, width, height)
    else {
      val scale     = maxDimensionPx.toDouble / largest
      val newWidth  = math.max(1, math.round(width * scale).toInt)
      val newHeight = math.max(1, math.round(height * scale).toInt)

      val pooled = Array.tabulate(newHeight, newWidth) { (outY, outX) =>
        // Each output pixel's source block, computed from scale so the last
        // block absorbs any rounding remainder rather than reading past the
        // source array's bounds.
        val srcXStart = (outX / scale).toInt
        val srcXEnd   = math.min(width, ((outX + 1) / scale).toInt.max(srcXStart + 1))
        val srcYStart = (outY / scale).toInt
        val srcYEnd   = math.min(height, ((outY + 1) / scale).toInt.max(srcYStart + 1))

        var maxVal = 0
        var y = srcYStart
        while (y < srcYEnd) {
          var x = srcXStart
          val row = gray(y)
          while (x < srcXEnd) {
            if (row(x) > maxVal) maxVal = row(x)
            x += 1
          }
          y += 1
        }
        maxVal
      }
      (pooled, newWidth, newHeight)
    }
  }

  // Small fixed 5-tap binomial kernel (~ Gaussian, sigma ~1) -- purely
  // cosmetic softening applied after max-pooling. See class doc: this
  // gives each max-pooled dot a visible halo instead of a single hard
  // on/off pixel, without touching which pixel carries the true peak
  // value (max-pooling already ran; this just adds a halo around it).
  private val BlurKernel: Array[Double] = {
    val raw = Array(1.0, 4.0, 6.0, 4.0, 1.0)
    val sum = raw.sum
    raw.map(_ / sum)
  }

  // A plain blur is itself an averaging operation -- run alone, it would
  // dilute the exact peak pixel max-pooling just worked to preserve (a
  // single bright pixel surrounded by black loses most of its value to a
  // normalized kernel). Taking the elementwise max of the original
  // max-pooled value and the blurred value fixes that: at the peak pixel
  // itself, max(original, smaller-blurred-value) stays the original: no
  // dilution. At surrounding pixels, which were originally black,
  // max(0, blurred-value) picks up the blur's falloff, i.e. a halo. Net
  // effect: true peak untouched, cosmetic gradient added around it.
  private def addHalo(gray: Array[Array[Int]], width: Int, height: Int): Array[Array[Int]] = {
    val blurred = blur(gray, width, height)
    Array.tabulate(height, width) { (y, x) => math.max(gray(y)(x), blurred(y)(x)) }
  }

  private def blur(gray: Array[Array[Int]], width: Int, height: Int): Array[Array[Int]] = {
    val radius = BlurKernel.length / 2

    // Separable: horizontal pass, then vertical pass on its result. Edge
    // pixels clamp to the nearest in-bounds sample rather than wrapping or
    // padding with black, so spots near the frame edge don't get artificially
    // dimmed by blurring in nonexistent dark neighbors.
    val horiz = Array.tabulate(height, width) { (y, x) =>
      var acc = 0.0
      var k   = 0
      while (k < BlurKernel.length) {
        val sx = math.min(width - 1, math.max(0, x + k - radius))
        acc += gray(y)(sx) * BlurKernel(k)
        k += 1
      }
      acc
    }

    Array.tabulate(height, width) { (y, x) =>
      var acc = 0.0
      var k   = 0
      while (k < BlurKernel.length) {
        val sy = math.min(height - 1, math.max(0, y + k - radius))
        acc += horiz(sy)(x) * BlurKernel(k)
        k += 1
      }
      math.min(255, math.max(0, math.round(acc).toInt))
    }
  }
}
