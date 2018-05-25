
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 Real-time movie recorder which is totally non-blocking
 */

#import "MovieRecorder.h"

#import <AVFoundation/AVAssetWriter.h>
#import <AVFoundation/AVAssetWriterInput.h>

#import <AVFoundation/AVMediaFormat.h>
#import <AVFoundation/AVVideoSettings.h>
#import <AVFoundation/AVAudioSettings.h>

#define LOG_STATUS_TRANSITIONS 0

typedef NS_ENUM( NSInteger, MovieRecorderStatus ) {
	MovieRecorderStatusIdle = 0,
	MovieRecorderStatusPreparingToRecord,
	MovieRecorderStatusRecording,
	MovieRecorderStatusFinishingRecordingPart1, // waiting for inflight buffers to be appended
	MovieRecorderStatusFinishingRecordingPart2, // calling finish writing on the asset writer
	MovieRecorderStatusFinished,	// terminal state
	MovieRecorderStatusFailed		// terminal state
}; // internal state machine


@interface MovieRecorder ()
{
	MovieRecorderStatus _status;
	
	dispatch_queue_t _writingQueue;
	
	NSURL *_URL;
	
	AVAssetWriter *_assetWriter;
	BOOL _haveStartedSession;
	
	CMFormatDescriptionRef _audioTrackSourceFormatDescription;
	NSDictionary *_audioTrackSettings;
	AVAssetWriterInput *_audioInput;
	
	CMFormatDescriptionRef _videoTrackSourceFormatDescription;
	CGAffineTransform _videoTrackTransform;
	NSDictionary *_videoTrackSettings;
	AVAssetWriterInput *_videoInput;

	__weak id<MovieRecorderDelegate> _delegate;
	dispatch_queue_t _delegateCallbackQueue;
}
@end

@implementation MovieRecorder

#pragma mark -
#pragma mark API

- (instancetype)initWithURL:(NSURL *)URL delegate:(id<MovieRecorderDelegate>)delegate callbackQueue:(dispatch_queue_t)queue // delegate is weak referenced
{
	NSParameterAssert( delegate != nil );
	NSParameterAssert( queue != nil );
	NSParameterAssert( URL != nil );
	
	self = [super init];
	if ( self )
	{
		_writingQueue = dispatch_queue_create( "com.apple.sample.movierecorder.writing", DISPATCH_QUEUE_SERIAL );
		_videoTrackTransform = CGAffineTransformIdentity;
		_URL = URL;
		_delegate = delegate;
		_delegateCallbackQueue = queue;
	}
	return self;
}

- (void)addVideoTrackWithSourceFormatDescription:(CMFormatDescriptionRef)formatDescription transform:(CGAffineTransform)transform settings:(NSDictionary *)videoSettings
{
	if ( formatDescription == NULL ) {
		@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"NULL format description" userInfo:nil];
		return;			
	}
	
	@synchronized( self )
	{
		if ( _status != MovieRecorderStatusIdle ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Cannot add tracks while not idle" userInfo:nil];
			return;
		}
		
		if ( _videoTrackSourceFormatDescription ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Cannot add more than one video track" userInfo:nil];
			return;
		}
		
		_videoTrackSourceFormatDescription = (CMFormatDescriptionRef)CFRetain( formatDescription );
		_videoTrackTransform = transform;
		_videoTrackSettings = [videoSettings copy];
	}
}

- (void)addAudioTrackWithSourceFormatDescription:(CMFormatDescriptionRef)formatDescription settings:(NSDictionary *)audioSettings
{
	if ( formatDescription == NULL ) {
		@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"NULL format description" userInfo:nil];
		return;			
	}
	
	@synchronized( self )
	{
		if ( _status != MovieRecorderStatusIdle ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Cannot add tracks while not idle" userInfo:nil];
			return;
		}
		
		if ( _audioTrackSourceFormatDescription ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Cannot add more than one audio track" userInfo:nil];
			return;
		}
		
		_audioTrackSourceFormatDescription = (CMFormatDescriptionRef)CFRetain( formatDescription );
		_audioTrackSettings = [audioSettings copy];
	}
}

- (void)prepareToRecord
{
	@synchronized( self )
	{
		if ( _status != MovieRecorderStatusIdle ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Already prepared, cannot prepare again" userInfo:nil];
			return;
		}
		
		[self transitionToStatus:MovieRecorderStatusPreparingToRecord error:nil];
	}
	
	dispatch_async( dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_LOW, 0 ), ^{
		
		@autoreleasepool
		{
			NSError *error = nil;
			// AVAssetWriter will not write over an existing file.
			[[NSFileManager defaultManager] removeItemAtURL:_URL error:NULL];
			
			_assetWriter = [[AVAssetWriter alloc] initWithURL:_URL fileType:AVFileTypeQuickTimeMovie error:&error];
			
			// Create and add inputs
			if ( ! error && _videoTrackSourceFormatDescription ) {
				[self setupAssetWriterVideoInputWithSourceFormatDescription:_videoTrackSourceFormatDescription transform:_videoTrackTransform settings:_videoTrackSettings error:&error];
			}
			
			if ( ! error && _audioTrackSourceFormatDescription ) {
				[self setupAssetWriterAudioInputWithSourceFormatDescription:_audioTrackSourceFormatDescription settings:_audioTrackSettings error:&error];
			}
			
			if ( ! error ) {
				BOOL success = [_assetWriter startWriting];
				if ( ! success ) {
					error = _assetWriter.error;
				}
			}
			
			@synchronized( self )
			{
				if ( error ) {
					[self transitionToStatus:MovieRecorderStatusFailed error:error];
				}
				else {
					[self transitionToStatus:MovieRecorderStatusRecording error:nil];
				}
			}
		}
	} );
}

- (void)appendVideoSampleBuffer:(CMSampleBufferRef)sampleBuffer
{
	[self appendSampleBuffer:sampleBuffer ofMediaType:AVMediaTypeVideo];
}

- (void)appendVideoPixelBuffer:(CVPixelBufferRef)pixelBuffer withPresentationTime:(CMTime)presentationTime
{
	CMSampleBufferRef sampleBuffer = NULL;
	
	CMSampleTimingInfo timingInfo = {0,};
	timingInfo.duration = kCMTimeInvalid;
	timingInfo.decodeTimeStamp = kCMTimeInvalid;
	timingInfo.presentationTimeStamp = presentationTime;
	
	OSStatus err = CMSampleBufferCreateForImageBuffer( kCFAllocatorDefault, pixelBuffer, true, NULL, NULL, _videoTrackSourceFormatDescription, &timingInfo, &sampleBuffer );
	if ( sampleBuffer ) {
		[self appendSampleBuffer:sampleBuffer ofMediaType:AVMediaTypeVideo];
		CFRelease( sampleBuffer );
	}
	else {
		NSString *exceptionReason = [NSString stringWithFormat:@"sample buffer create failed (%i)", (int)err];
		@throw [NSException exceptionWithName:NSInvalidArgumentException reason:exceptionReason userInfo:nil];
	}
}

- (void)appendAudioSampleBuffer:(CMSampleBufferRef)sampleBuffer
{
	[self appendSampleBuffer:sampleBuffer ofMediaType:AVMediaTypeAudio];
}

- (void)finishRecording
{
	@synchronized( self )
	{
		BOOL shouldFinishRecording = NO;
		switch ( _status )
		{
			case MovieRecorderStatusIdle:
			case MovieRecorderStatusPreparingToRecord:
			case MovieRecorderStatusFinishingRecordingPart1:
			case MovieRecorderStatusFinishingRecordingPart2:
			case MovieRecorderStatusFinished:
				@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Not recording" userInfo:nil];
				break;
			case MovieRecorderStatusFailed:
				// From the client's perspective the movie recorder can asynchronously transition to an error state as the result of an append.
				// Because of this we are lenient when finishRecording is called and we are in an error state.
				NSLog( @"Recording has failed, nothing to do" );
				break;
			case MovieRecorderStatusRecording:
				shouldFinishRecording = YES;
				break;
		}
		
		if ( shouldFinishRecording ) {
			[self transitionToStatus:MovieRecorderStatusFinishingRecordingPart1 error:nil];
		}
		else {
			return;
		}
	}
	
	dispatch_async( _writingQueue, ^{
		
		@autoreleasepool
		{
			@synchronized( self )
			{
				// We may have transitioned to an error state as we appended inflight buffers. In that case there is nothing to do now.
				if ( _status != MovieRecorderStatusFinishingRecordingPart1 ) {
					return;
				}
				
				// It is not safe to call -[AVAssetWriter finishWriting*] concurrently with -[AVAssetWriterInput appendSampleBuffer:]
				// We transition to MovieRecorderStatusFinishingRecordingPart2 while on _writingQueue, which guarantees that no more buffers will be appended.
				[self transitionToStatus:MovieRecorderStatusFinishingRecordingPart2 error:nil];
			}

			[_assetWriter finishWritingWithCompletionHandler:^{
				@synchronized( self )
				{
					NSError *error = _assetWriter.error;
					if ( error ) {
						[self transitionToStatus:MovieRecorderStatusFailed error:error];
					}
					else {
						[self transitionToStatus:MovieRecorderStatusFinished error:nil];
					}
				}
			}];
		}
	} );
}

- (void)dealloc
{
	if ( _audioTrackSourceFormatDescription ) {
		CFRelease( _audioTrackSourceFormatDescription );
	}
	
	if ( _videoTrackSourceFormatDescription ) {
		CFRelease( _videoTrackSourceFormatDescription );
	}
}

#pragma mark -
#pragma mark Internal

- (void)appendSampleBuffer:(CMSampleBufferRef)sampleBuffer ofMediaType:(NSString *)mediaType
{
	if ( sampleBuffer == NULL ) {
		@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"NULL sample buffer" userInfo:nil];
		return;			
	}
	
	@synchronized( self ) {
		if ( _status < MovieRecorderStatusRecording ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Not ready to record yet" userInfo:nil];
			return;	
		}
	}
	
	CFRetain( sampleBuffer );
	dispatch_async( _writingQueue, ^{
		
		@autoreleasepool
		{
			@synchronized( self )
			{
				// From the client's perspective the movie recorder can asynchronously transition to an error state as the result of an append.
				// Because of this we are lenient when samples are appended and we are no longer recording.
				// Instead of throwing an exception we just release the sample buffers and return.
				if ( _status > MovieRecorderStatusFinishingRecordingPart1 ) {
					CFRelease( sampleBuffer );
					return;
				}
			}
			
			if ( ! _haveStartedSession ) {
				[_assetWriter startSessionAtSourceTime:CMSampleBufferGetPresentationTimeStamp(sampleBuffer)];
				_haveStartedSession = YES;
			}
			
			AVAssetWriterInput *input = ( mediaType == AVMediaTypeVideo ) ? _videoInput : _audioInput;
			
			if ( input.readyForMoreMediaData )
			{
				BOOL success = [input appendSampleBuffer:sampleBuffer];
				if ( ! success ) {
					NSError *error = _assetWriter.error;
					@synchronized( self ) {
						[self transitionToStatus:MovieRecorderStatusFailed error:error];
					}
				}
			}
			else
			{
				NSLog( @"%@ input not ready for more media data, dropping buffer", mediaType );
			}
			CFRelease( sampleBuffer );
		}
	} );
}

// call under @synchonized( self )
- (void)transitionToStatus:(MovieRecorderStatus)newStatus error:(NSError *)error
{
	BOOL shouldNotifyDelegate = NO;
	
#if LOG_STATUS_TRANSITIONS
	NSLog( @"MovieRecorder state transition: %@->%@", [self stringForStatus:_status], [self stringForStatus:newStatus] );
#endif
	
	if ( newStatus != _status )
	{
		// terminal states
		if ( ( newStatus == MovieRecorderStatusFinished ) || ( newStatus == MovieRecorderStatusFailed ) )
		{
			shouldNotifyDelegate = YES;
			// make sure there are no more sample buffers in flight before we tear down the asset writer and inputs
			
			dispatch_async( _writingQueue, ^{
				[self teardownAssetWriterAndInputs];
				if ( newStatus == MovieRecorderStatusFailed ) {
					[[NSFileManager defaultManager] removeItemAtURL:_URL error:NULL];
				}
			} );

#if LOG_STATUS_TRANSITIONS
			if ( error ) {
				NSLog( @"MovieRecorder error: %@, code: %i", error, (int)error.code );
			}
#endif
		}
		else if ( newStatus == MovieRecorderStatusRecording )
		{
			shouldNotifyDelegate = YES;
		}
		
		_status = newStatus;
	}

	if ( shouldNotifyDelegate )
	{
		dispatch_async( _delegateCallbackQueue, ^{
			@autoreleasepool
			{
				switch ( newStatus )
				{
					case MovieRecorderStatusRecording:
						[_delegate movieRecorderDidFinishPreparing:self];
						break;
					case MovieRecorderStatusFinished:
						[_delegate movieRecorderDidFinishRecording:self];
						break;
					case MovieRecorderStatusFailed:
						[_delegate movieRecorder:self didFailWithError:error];
						break;
					default:
						NSAssert1( NO, @"Unexpected recording status (%i) for delegate callback", (int)newStatus );
						break;
				}
			}
		} );
	}
}

#if LOG_STATUS_TRANSITIONS

- (NSString *)stringForStatus:(MovieRecorderStatus)status
{
	NSString *statusString = nil;
	
	switch ( status )
	{
		case MovieRecorderStatusIdle:
			statusString = @"Idle";
			break;
		case MovieRecorderStatusPreparingToRecord:
			statusString = @"PreparingToRecord";
			break;
		case MovieRecorderStatusRecording:
			statusString = @"Recording";
			break;
		case MovieRecorderStatusFinishingRecordingPart1:
			statusString = @"FinishingRecordingPart1";
			break;
		case MovieRecorderStatusFinishingRecordingPart2:
			statusString = @"FinishingRecordingPart2";
			break;
		case MovieRecorderStatusFinished:
			statusString = @"Finished";
			break;
		case MovieRecorderStatusFailed:
			statusString = @"Failed";
			break;
		default:
			statusString = @"Unknown";
			break;
	}
	return statusString;
	
}

#endif // LOG_STATUS_TRANSITIONS

- (BOOL)setupAssetWriterAudioInputWithSourceFormatDescription:(CMFormatDescriptionRef)audioFormatDescription settings:(NSDictionary *)audioSettings error:(NSError **)errorOut
{
	if ( ! audioSettings ) {
		NSLog( @"No audio settings provided, using default settings" );
		audioSettings = @{ AVFormatIDKey : @(kAudioFormatMPEG4AAC) };
	}
	
	if ( [_assetWriter canApplyOutputSettings:audioSettings forMediaType:AVMediaTypeAudio] )
	{
		_audioInput = [[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeAudio outputSettings:audioSettings sourceFormatHint:audioFormatDescription];
		_audioInput.expectsMediaDataInRealTime = YES;
		
		if ( [_assetWriter canAddInput:_audioInput] )
		{
			[_assetWriter addInput:_audioInput];
		}
		else
		{
			if ( errorOut ) {
				*errorOut = [[self class] cannotSetupInputError];
			}
			return NO;
		}
	}
	else
	{
		if ( errorOut ) {
			*errorOut = [[self class] cannotSetupInputError];
		}
		return NO;
	}
	
	return YES;
}

- (BOOL)setupAssetWriterVideoInputWithSourceFormatDescription:(CMFormatDescriptionRef)videoFormatDescription transform:(CGAffineTransform)transform settings:(NSDictionary *)videoSettings error:(NSError **)errorOut
{
	if ( ! videoSettings )
	{
		float bitsPerPixel;
		CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions( videoFormatDescription );
		int numPixels = dimensions.width * dimensions.height;
		int bitsPerSecond;
	
		NSLog( @"No video settings provided, using default settings" );
		
		// Assume that lower-than-SD resolutions are intended for streaming, and use a lower bitrate
		if ( numPixels < ( 640 * 480 ) ) {
			bitsPerPixel = 4.05; // This bitrate approximately matches the quality produced by AVCaptureSessionPresetMedium or Low.
		}
		else {
			bitsPerPixel = 10.1; // This bitrate approximately matches the quality produced by AVCaptureSessionPresetHigh.
		}
		
		bitsPerSecond = numPixels * bitsPerPixel;
		
		NSDictionary *compressionProperties = @{ AVVideoAverageBitRateKey : @(bitsPerSecond), 
												 AVVideoExpectedSourceFrameRateKey : @(30),
												 AVVideoMaxKeyFrameIntervalKey : @(30) };
		
		videoSettings = @{ AVVideoCodecKey : AVVideoCodecH264,
						   AVVideoWidthKey : @(dimensions.width),
						   AVVideoHeightKey : @(dimensions.height),
						   AVVideoCompressionPropertiesKey : compressionProperties };
	}
	
	if ( [_assetWriter canApplyOutputSettings:videoSettings forMediaType:AVMediaTypeVideo] )
	{
		_videoInput = [[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeVideo outputSettings:videoSettings sourceFormatHint:videoFormatDescription];
		_videoInput.expectsMediaDataInRealTime = YES;
		_videoInput.transform = transform;
		
		if ( [_assetWriter canAddInput:_videoInput] )
		{
			[_assetWriter addInput:_videoInput];
		}
		else
		{
			if ( errorOut ) {
				*errorOut = [[self class] cannotSetupInputError];
			}
			return NO;
		}
	}
	else
	{
		if ( errorOut ) {
			*errorOut = [[self class] cannotSetupInputError];
		}
		return NO;
	}
	
	return YES;
}

+ (NSError *)cannotSetupInputError
{
	NSString *localizedDescription = NSLocalizedString( @"Recording cannot be started", nil );
	NSString *localizedFailureReason = NSLocalizedString( @"Cannot setup asset writer input.", nil );
	NSDictionary *errorDict = @{ NSLocalizedDescriptionKey : localizedDescription,
								 NSLocalizedFailureReasonErrorKey : localizedFailureReason };
	return [NSError errorWithDomain:@"com.apple.dts.samplecode" code:0 userInfo:errorDict];
}

- (void)teardownAssetWriterAndInputs
{
	_videoInput = nil;
	_audioInput = nil;
	_assetWriter = nil;
}

@end
