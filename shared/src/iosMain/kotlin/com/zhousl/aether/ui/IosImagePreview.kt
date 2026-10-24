@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.zhousl.aether.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.value
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.*
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

// ImageIO downsamples during decoding, before allocating the full camera image.
// All callers use this off the UI thread, including attachment thumbnails.
internal suspend fun decodeIosImagePreview(bytes: ByteArray, maxDimension: Int = 2048): ImageBitmap =
    withContext(Dispatchers.Default) {
        require(bytes.isNotEmpty() && maxDimension > 0)
        val data = bytes.usePinned {
            CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret(), bytes.size.toLong())
        } ?: error("Unable to read image data.")
        try {
            val source = CGImageSourceCreateWithData(data, null) ?: error("Unsupported image format.")
            try {
                memScoped {
                    val dimension = alloc<IntVar> { value = maxDimension }
                    val number = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, dimension.ptr)
                        ?: error("Unable to prepare image preview.")
                    val options = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
                        ?: error("Unable to prepare image preview.")
                    try {
                        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                        CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
                        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
                        val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
                            ?: error("Unable to decode image preview.")
                        try {
                            val png = UIImagePNGRepresentation(UIImage.imageWithCGImage(image))
                                ?: error("Unable to encode image preview.")
                            val preview = png.bytes?.reinterpret<ByteVar>()?.readBytes(png.length.toInt())
                                ?: error("Unable to read image preview.")
                            preview.decodeToImageBitmap()
                        } finally {
                            CGImageRelease(image)
                        }
                    } finally {
                        CFRelease(options)
                        CFRelease(number)
                    }
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(data)
        }
    }
