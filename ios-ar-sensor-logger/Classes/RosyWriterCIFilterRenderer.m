/*
	Copyright (C) 2016 Apple Inc. All Rights Reserved.
	See LICENSE.txt for this sample’s licensing information
	
	Abstract:
	The RosyWriter CoreImage CIFilter-based effect renderer
 */

#import "RosyWriterCIFilterRenderer.h"

@interface RosyWriterCIFilterRenderer ()
{
	CIContext *_ciContext;
	CIFilter *_rosyFilter;
	CGColorSpaceRef _rgbColorSpace;
	CVPixelBufferPoolRef _bufferPool;
	CFDictionaryRef _bufferPoolAuxAttributes;
	CMFormatDescriptionRef _outputFormatDescription;
}

@end

@implementation RosyWriterCIFilterRenderer

#pragma mark API

- (void)dealloc
{
	[self deleteBuffers];
}

#pragma mark RosyWriterRenderer

- (BOOL)operatesInPlace
{
	return NO;
}

- (FourCharCode)inputPixelFormat
{
	return kCVPixelFormatType_32BGRA;
}

- (void)prepareForInputWithFormatDescription:(CMFormatDescriptionRef)inputFormatDescription outputRetainedBufferCountHint:(size_t)outputRetainedBufferCountHint
{
	// The input and output dimensions are the same. This renderer doesn't do any scaling.
	CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions( inputFormatDescription );
	
	[self deleteBuffers];
	if ( ! [self initializeBuffersWithOutputDimensions:dimensions retainedBufferCountHint:outputRetainedBufferCountHint] ) {
		@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Problem preparing renderer." userInfo:nil];
	}
	
	_rgbColorSpace = CGColorSpaceCreateDeviceRGB();
	EAGLContext *eaglContext = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];
	_ciContext = [CIContext contextWithEAGLContext:eaglContext options:@{ kCIContextWorkingColorSpace : [NSNull null] } ];
	
	_rosyFilter = [CIFilter filterWithName:@"CIColorMatrix"];
	CGFloat greenCoefficients[4] = { 0, 0, 0, 0 };
	[_rosyFilter setValue:[CIVector vectorWithValues:greenCoefficients count:4] forKey:@"inputGVector"];
}

- (void)reset
{
	[self deleteBuffers];
}

- (CVPixelBufferRef)copyRenderedPixelBuffer:(CVPixelBufferRef)pixelBuffer
{
	OSStatus err = noErr;
	CVPixelBufferRef renderedOutputPixelBuffer = NULL;

	CIImage *sourceImage = [[CIImage alloc] initWithCVPixelBuffer:pixelBuffer options:nil];
	
	[_rosyFilter setValue:sourceImage forKey:kCIInputImageKey];
	CIImage *filteredImage = [_rosyFilter valueForKey:kCIOutputImageKey];
	
	err = CVPixelBufferPoolCreatePixelBuffer( kCFAllocatorDefault, _bufferPool, &renderedOutputPixelBuffer );
	if ( err ) {
		NSLog( @"Cannot obtain a pixel buffer from the buffer pool (%d)", (int)err );
		goto bail;
	}
	
	// render the filtered image out to a pixel buffer (no locking needed as CIContext's render method will do that)
	[_ciContext render:filteredImage toCVPixelBuffer:renderedOutputPixelBuffer bounds:[filteredImage extent] colorSpace:_rgbColorSpace];

bail:
	
	return renderedOutputPixelBuffer;
}

- (CMFormatDescriptionRef)outputFormatDescription
{
	return _outputFormatDescription;
}

#pragma mark Internal

- (BOOL)initializeBuffersWithOutputDimensions:(CMVideoDimensions)outputDimensions retainedBufferCountHint:(size_t)clientRetainedBufferCountHint
{
	BOOL success = YES;
	
	size_t maxRetainedBufferCount = clientRetainedBufferCountHint;
	_bufferPool = createPixelBufferPool( outputDimensions.width, outputDimensions.height, kCVPixelFormatType_32BGRA, (int32_t)maxRetainedBufferCount );
	if ( ! _bufferPool ) {
		NSLog( @"Problem initializing a buffer pool." );
		success = NO;
		goto bail;
	}
	
	_bufferPoolAuxAttributes = createPixelBufferPoolAuxAttributes( (int32_t)maxRetainedBufferCount );
	preallocatePixelBuffersInPool( _bufferPool, _bufferPoolAuxAttributes );
	
	CMFormatDescriptionRef outputFormatDescription = NULL;
	CVPixelBufferRef testPixelBuffer = NULL;
	CVPixelBufferPoolCreatePixelBufferWithAuxAttributes( kCFAllocatorDefault, _bufferPool, _bufferPoolAuxAttributes, &testPixelBuffer );
	if ( ! testPixelBuffer ) {
		NSLog( @"Problem creating a pixel buffer." );
		success = NO;
		goto bail;
	}
	CMVideoFormatDescriptionCreateForImageBuffer( kCFAllocatorDefault, testPixelBuffer, &outputFormatDescription );
	_outputFormatDescription = outputFormatDescription;
	CFRelease( testPixelBuffer );
	
bail:
	if ( ! success ) {
		[self deleteBuffers];
	}
	return success;
}

- (void)deleteBuffers
{
	if ( _bufferPool ) {
		CFRelease( _bufferPool );
		_bufferPool = NULL;
	}
	if ( _bufferPoolAuxAttributes ) {
		CFRelease( _bufferPoolAuxAttributes );
		_bufferPoolAuxAttributes = NULL;
	}
	if ( _outputFormatDescription ) {
		CFRelease( _outputFormatDescription );
		_outputFormatDescription = NULL;
	}
	if ( _rgbColorSpace ) {
		CFRelease( _rgbColorSpace );
		_rgbColorSpace = NULL;
	}
	
	_ciContext = nil;
	_rosyFilter = nil;
}

static CVPixelBufferPoolRef createPixelBufferPool( int32_t width, int32_t height, OSType pixelFormat, int32_t maxBufferCount )
{
	CVPixelBufferPoolRef outputPool = NULL;
	
	NSDictionary *sourcePixelBufferOptions = @{ (id)kCVPixelBufferPixelFormatTypeKey : @(pixelFormat),
												(id)kCVPixelBufferWidthKey : @(width),
												(id)kCVPixelBufferHeightKey : @(height),
												(id)kCVPixelFormatOpenGLESCompatibility : @(YES),
												(id)kCVPixelBufferIOSurfacePropertiesKey : @{ /*empty dictionary*/ } };
	
	NSDictionary *pixelBufferPoolOptions = @{ (id)kCVPixelBufferPoolMinimumBufferCountKey : @(maxBufferCount) };

	CVPixelBufferPoolCreate( kCFAllocatorDefault, (__bridge CFDictionaryRef)pixelBufferPoolOptions, (__bridge CFDictionaryRef)sourcePixelBufferOptions, &outputPool );

	return outputPool;
}

static CFDictionaryRef createPixelBufferPoolAuxAttributes( int32_t maxBufferCount )
{
	// CVPixelBufferPoolCreatePixelBufferWithAuxAttributes() will return kCVReturnWouldExceedAllocationThreshold if we have already vended the max number of buffers
	NSDictionary *auxAttributes = [[NSDictionary alloc] initWithObjectsAndKeys:@(maxBufferCount), (id)kCVPixelBufferPoolAllocationThresholdKey, nil];
	return CFBridgingRetain( auxAttributes );
}

static void preallocatePixelBuffersInPool( CVPixelBufferPoolRef pool, CFDictionaryRef auxAttributes )
{
	// Preallocate buffers in the pool, since this is for real-time display/capture
	NSMutableArray *pixelBuffers = [[NSMutableArray alloc] init];
	while ( 1 )
	{
		CVPixelBufferRef pixelBuffer = NULL;
		OSStatus err = CVPixelBufferPoolCreatePixelBufferWithAuxAttributes( kCFAllocatorDefault, pool, auxAttributes, &pixelBuffer );
		
		if ( err == kCVReturnWouldExceedAllocationThreshold ) {
			break;
		}
		assert( err == noErr );
		
		[pixelBuffers addObject:CFBridgingRelease( pixelBuffer )];
	}
	[pixelBuffers removeAllObjects];
}

@end
