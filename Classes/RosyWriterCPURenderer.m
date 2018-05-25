
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 The RosyWriter CPU-based effect renderer
 */

#import "RosyWriterCPURenderer.h"

@implementation RosyWriterCPURenderer

#pragma mark RosyWriterRenderer

- (BOOL)operatesInPlace
{
	return YES;
}

- (FourCharCode)inputPixelFormat
{
	return kCVPixelFormatType_32BGRA;
}

- (void)prepareForInputWithFormatDescription:(CMFormatDescriptionRef)inputFormatDescription outputRetainedBufferCountHint:(size_t)outputRetainedBufferCountHint
{
	// nothing to do, we are stateless
}

- (void)reset
{
	// nothing to do, we are stateless
}

- (CVPixelBufferRef)copyRenderedPixelBuffer:(CVPixelBufferRef)pixelBuffer
{	
	const int kBytesPerPixel = 4;
	
	CVPixelBufferLockBaseAddress( pixelBuffer, 0 );
	
	int bufferWidth = (int)CVPixelBufferGetWidth( pixelBuffer );
	int bufferHeight = (int)CVPixelBufferGetHeight( pixelBuffer );
	size_t bytesPerRow = CVPixelBufferGetBytesPerRow( pixelBuffer );
	uint8_t *baseAddress = CVPixelBufferGetBaseAddress( pixelBuffer );
	
	for ( int row = 0; row < bufferHeight; row++ )
	{
		uint8_t *pixel = baseAddress + row * bytesPerRow;
		for ( int column = 0; column < bufferWidth; column++ )
		{
			pixel[1] = 0; // De-green (second pixel in BGRA is green)
			pixel += kBytesPerPixel;
		}
	}
	
	CVPixelBufferUnlockBaseAddress( pixelBuffer, 0 );
	
	return (CVPixelBufferRef)CFRetain( pixelBuffer );
}

@end
