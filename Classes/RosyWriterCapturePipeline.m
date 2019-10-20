
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 The class that creates and manages the AVCaptureSession
 */

#import "RosyWriterCapturePipeline.h"

#import "RosyWriterOpenGLRenderer.h"
#import "RosyWriterCPURenderer.h"
#import "RosyWriterCIFilterRenderer.h"
#import "RosyWriterOpenCVRenderer.h"

#import "MovieRecorder.h"
#import "VideoTimeConverter.h"
#import "InertialRecorder.h"
#import "CameraControlFunctions.h"

#import <CoreMedia/CMBufferQueue.h>
#import <CoreMedia/CMAudioClock.h>
#import <AssetsLibrary/AssetsLibrary.h>
#import <ImageIO/CGImageProperties.h>
#import <Photos/Photos.h> // for PHAsset

/*
 RETAINED_BUFFER_COUNT is the number of pixel buffers we expect to hold on to from the renderer. This value informs the renderer how to size its buffer pool and how many pixel buffers to preallocate (done in the prepareWithOutputDimensions: method). Preallocation helps to lessen the chance of frame drops in our recording, in particular during recording startup. If we try to hold on to more buffers than RETAINED_BUFFER_COUNT then the renderer will fail to allocate new buffers from its pool and we will drop frames.

 A back of the envelope calculation to arrive at a RETAINED_BUFFER_COUNT of '6':
 - The preview path only has the most recent frame, so this makes the movie recording path the long pole.
 - The movie recorder internally does a dispatch_async to avoid blocking the caller when enqueuing to its internal asset writer.
 - Allow 2 frames of latency to cover the dispatch_async and the -[AVAssetWriterInput appendSampleBuffer:] call.
 - Then we allow for the encoder to retain up to 4 frames. Two frames are retained while being encoded/format converted, while the other two are to handle encoder format conversion pipelining and encoder startup latency.

 Really you need to test and measure the latency in your own application pipeline to come up with an appropriate number. 1080p BGRA buffers are quite large, so it's a good idea to keep this number as low as possible.
 */

#define RETAINED_BUFFER_COUNT 6

#define RECORD_AUDIO 0

#define LOG_STATUS_TRANSITIONS 0

typedef NS_ENUM( NSInteger, RosyWriterRecordingStatus )
{
	RosyWriterRecordingStatusIdle = 0,
	RosyWriterRecordingStatusStartingRecording,
	RosyWriterRecordingStatusRecording,
	RosyWriterRecordingStatusStoppingRecording,
}; // internal state machine

NSString *const VIDEO_META_FILENAME = @"movie_metadata.csv";
NSString *const IMU_OUTPUT_FILENAME = @"gyro_accel.csv";

@interface RosyWriterCapturePipeline () <AVCaptureAudioDataOutputSampleBufferDelegate, AVCaptureVideoDataOutputSampleBufferDelegate, MovieRecorderDelegate>
{
	NSMutableArray *_previousSecondTimestamps;

	AVCaptureSession *_captureSession;
	AVCaptureDevice *_videoDevice;
	AVCaptureConnection *_audioConnection;
	AVCaptureConnection *_videoConnection;
	AVCaptureVideoOrientation _videoBufferOrientation;
	BOOL _running;
	BOOL _startCaptureSessionOnEnteringForeground;
	id _applicationWillEnterForegroundNotificationObserver;
	NSDictionary *_videoCompressionSettings;
	NSDictionary *_audioCompressionSettings;
	
	dispatch_queue_t _sessionQueue;
	dispatch_queue_t _videoDataOutputQueue;
	
	id<RosyWriterRenderer> _renderer;
	BOOL _renderingEnabled;
	
	MovieRecorder *_recorder;
	NSURL *_recordingURL;
	RosyWriterRecordingStatus _recordingStatus;
	
	UIBackgroundTaskIdentifier _pipelineRunningTask;
	
	__weak id<RosyWriterCapturePipelineDelegate> _delegate;
	dispatch_queue_t _delegateCallbackQueue;
    
    CMTime adjustExpFinishTime;

    InertialRecorder *_inertialRecorder;
}

// Redeclared readwrite
@property(atomic, readwrite) float videoFrameRate;
@property(atomic, readwrite) float fx;
@property(atomic, readwrite) CMVideoDimensions videoDimensions;
@property (nonatomic, retain) VideoTimeConverter *videoTimeConverter;

@property(atomic, readwrite) BOOL adjustExposureFinished;
// Because we specify __attribute__((NSObject)) ARC will manage the lifetime of the backing ivars even though they are CF types.
@property(nonatomic, strong) __attribute__((NSObject)) CVPixelBufferRef currentPreviewPixelBuffer;
@property(nonatomic, strong) __attribute__((NSObject)) CMFormatDescriptionRef outputVideoFormatDescription;
@property(nonatomic, strong) __attribute__((NSObject)) CMFormatDescriptionRef outputAudioFormatDescription;

@end

@implementation RosyWriterCapturePipeline

- (instancetype)initWithDelegate:(id<RosyWriterCapturePipelineDelegate>)delegate callbackQueue:(dispatch_queue_t)queue // delegate is weak referenced
{
	NSParameterAssert( delegate != nil );
	NSParameterAssert( queue != nil );
	
	self = [super init];
	if ( self )
	{
		_previousSecondTimestamps = [[NSMutableArray alloc] init];
		_recordingOrientation = AVCaptureVideoOrientationPortrait;
		
		_recordingURL = [[NSURL alloc] initFileURLWithPath:[NSString pathWithComponents:@[NSTemporaryDirectory(), @"Movie.MP4"]]];
		
		_sessionQueue = dispatch_queue_create( "com.apple.sample.capturepipeline.session", DISPATCH_QUEUE_SERIAL );
		
		// In a multi-threaded producer consumer system it's generally a good idea to make sure that producers do not get starved of CPU time by their consumers.
		// In this app we start with VideoDataOutput frames on a high priority queue, and downstream consumers use default priority queues.
		// Audio uses a default priority queue because we aren't monitoring it live and just want to get it into the movie.
		// AudioDataOutput can tolerate more latency than VideoDataOutput as its buffers aren't allocated out of a fixed size pool.
		_videoDataOutputQueue = dispatch_queue_create( "com.apple.sample.capturepipeline.video", DISPATCH_QUEUE_SERIAL );
		dispatch_set_target_queue( _videoDataOutputQueue, dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_HIGH, 0 ) );
		
// USE_XXX_RENDERER is set in the project's build settings for each target
#if USE_OPENGL_RENDERER
		_renderer = [[RosyWriterOpenGLRenderer alloc] init];
#elif USE_CPU_RENDERER
		_renderer = [[RosyWriterCPURenderer alloc] init];
#elif USE_CIFILTER_RENDERER
		_renderer = [[RosyWriterCIFilterRenderer alloc] init];
#elif USE_OPENCV_RENDERER
		_renderer = [[RosyWriterOpenCVRenderer alloc] init];
#endif
				
		_pipelineRunningTask = UIBackgroundTaskInvalid;
		_delegate = delegate;
		_delegateCallbackQueue = queue;
        _fx = 0.0;
        _autoLocked = FALSE;

        _adjustExposureFinished = TRUE;
        adjustExpFinishTime = CMTimeMake(0, 1);
        _exposureDuration = 0;
        
        _inertialRecorder = [[InertialRecorder alloc] init];
        
        _videoTimeConverter = [[VideoTimeConverter alloc] init];
	}
	return self;
}

- (void)dealloc
{
	[self teardownCaptureSession];
}

#pragma mark Capture Session

- (void)startRunning
{
	dispatch_sync( _sessionQueue, ^{
		[self setupCaptureSession];
		
		if ( _captureSession ) {
			[_captureSession startRunning];
			_running = YES;
		}
	} );
}

- (void)stopRunning
{
	dispatch_sync( _sessionQueue, ^{
		_running = NO;
		
		// the captureSessionDidStopRunning method will stop recording if necessary as well, but we do it here so that the last video and audio samples are better aligned
		[self stopRecording]; // does nothing if we aren't currently recording
		
		[_captureSession stopRunning];
		
		[self captureSessionDidStopRunning];
		
		[self teardownCaptureSession];
	} );
}

- (AVCaptureDevice *)frontCamera {
    NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
    for (AVCaptureDevice *device in devices) {
        if ([device position] == AVCaptureDevicePositionFront) {
            return device;
        }
    }
    return nil;
}

- (AVCaptureDevice *)rearCamera {
    return [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
}

- (void)setupCaptureSession
{
	if ( _captureSession ) {
		return;
	}
	
	_captureSession = [[AVCaptureSession alloc] init];	

	[[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(captureSessionNotification:) name:nil object:_captureSession];
	_applicationWillEnterForegroundNotificationObserver = [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationWillEnterForegroundNotification object:[UIApplication sharedApplication] queue:nil usingBlock:^(NSNotification *note) {
		// Retain self while the capture session is alive by referencing it in this observer block which is tied to the session lifetime
		// Client must stop us running before we can be deallocated
		[self applicationWillEnterForeground];
	}];
	
#if RECORD_AUDIO
	/* Audio */
	AVCaptureDevice *audioDevice = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeAudio];
	AVCaptureDeviceInput *audioIn = [[AVCaptureDeviceInput alloc] initWithDevice:audioDevice error:nil];
	if ( [_captureSession canAddInput:audioIn] ) {
		[_captureSession addInput:audioIn];
	}
	[audioIn release];
	
	AVCaptureAudioDataOutput *audioOut = [[AVCaptureAudioDataOutput alloc] init];
	// Put audio on its own queue to ensure that our video processing doesn't cause us to drop audio
	dispatch_queue_t audioCaptureQueue = dispatch_queue_create( "com.apple.sample.capturepipeline.audio", DISPATCH_QUEUE_SERIAL );
	[audioOut setSampleBufferDelegate:self queue:audioCaptureQueue];
	[audioCaptureQueue release];
	
	if ( [_captureSession canAddOutput:audioOut] ) {
		[_captureSession addOutput:audioOut];
	}
	_audioConnection = [audioOut connectionWithMediaType:AVMediaTypeAudio];
	[audioOut release];
#endif // RECORD_AUDIO
	
	/* Video */
    
    // if the front-facing camera is desired, replace rearCamera with frontCamera
	AVCaptureDevice *videoDevice = [self rearCamera];
	NSError *videoDeviceError = nil;
	AVCaptureDeviceInput *videoIn = [[AVCaptureDeviceInput alloc] initWithDevice:videoDevice error:&videoDeviceError];
	if ( [_captureSession canAddInput:videoIn] ) {
		[_captureSession addInput:videoIn];
        _videoDevice = videoDevice;
        _videoDeviceInput = videoIn;
	} else {
		[self handleNonRecoverableCaptureSessionRuntimeError:videoDeviceError];
		return;
	}
	
	AVCaptureVideoDataOutput *videoOut = [[AVCaptureVideoDataOutput alloc] init];
	videoOut.videoSettings = @{ (id)kCVPixelBufferPixelFormatTypeKey : @(_renderer.inputPixelFormat) };
	[videoOut setSampleBufferDelegate:self queue:_videoDataOutputQueue];
	
	// RosyWriter records videos and we prefer not to have any dropped frames in the video recording.
	// By setting alwaysDiscardsLateVideoFrames to NO we ensure that minor fluctuations in system load or in our processing time for a given frame won't cause framedrops.
	// We do however need to ensure that on average we can process frames in realtime.
	// If we were doing preview only we would probably want to set alwaysDiscardsLateVideoFrames to YES.
	videoOut.alwaysDiscardsLateVideoFrames = NO;
	
	if ( [_captureSession canAddOutput:videoOut] ) {
		[_captureSession addOutput:videoOut];
	}
	_videoConnection = [videoOut connectionWithMediaType:AVMediaTypeVideo];
    if (@available(iOS 11_0, *)) {
        if ( [_videoConnection isCameraIntrinsicMatrixDeliverySupported] ) {
            NSLog(@"camera intrinsic mat delivery supported");
            [_videoConnection setCameraIntrinsicMatrixDeliveryEnabled:true];
            if ([_videoConnection isCameraIntrinsicMatrixDeliveryEnabled] ) {
                NSLog(@"camera intrinsic mat delivery enabled");
            } else {
                NSLog(@"camera intrinsic mat delivery NOT enabled");
            }
        } else {
            NSLog(@"camera intrinsic mat delivery NOT supported");
        }
    } else {
        // Fallback on earlier versions
    }
	int frameRate;
	NSString *sessionPreset = AVCaptureSessionPresetHigh;
	CMTime frameDuration = kCMTimeInvalid;
	// For single core systems like iPhone 4 and iPod Touch 4th Generation we use a lower resolution and framerate to maintain real-time performance.
	if ( [NSProcessInfo processInfo].processorCount == 1 )
	{
		if ( [_captureSession canSetSessionPreset:AVCaptureSessionPreset640x480] ) {
			sessionPreset = AVCaptureSessionPreset640x480;
		}
		frameRate = 15;
	}
	else
	{
//#if ! USE_OPENGL_RENDERER // cap the maximum size whether opengl is used or not
		// When using the CPU renderers or the CoreImage renderer we lower the resolution to 720p so that all devices can maintain real-time performance (this is primarily for A5 based devices like iPhone 4s and iPod Touch 5th Generation).
		if ( [_captureSession canSetSessionPreset:AVCaptureSessionPreset1280x720] ) {
			sessionPreset = AVCaptureSessionPreset1280x720;
		}
//#endif // ! USE_OPENGL_RENDERER

		frameRate = 30;
	}
	
	_captureSession.sessionPreset = sessionPreset;
	
	frameDuration = CMTimeMake( 1, frameRate );

	NSError *error = nil;
	if ( [videoDevice lockForConfiguration:&error] ) {
		videoDevice.activeVideoMaxFrameDuration = frameDuration;
		videoDevice.activeVideoMinFrameDuration = frameDuration;
		[videoDevice unlockForConfiguration];
	}
	else {
		NSLog( @"videoDevice lockForConfiguration returned error %@", error );
	}

	// Get the recommended compression settings after configuring the session/device.
#if RECORD_AUDIO
	_audioCompressionSettings = [[audioOut recommendedAudioSettingsForAssetWriterWithOutputFileType:AVFileTypeQuickTimeMovie] copy];
#endif
	_videoCompressionSettings = [[videoOut recommendedVideoSettingsForAssetWriterWithOutputFileType:AVFileTypeQuickTimeMovie] copy];
	
	_videoBufferOrientation = _videoConnection.videoOrientation;
    CGFloat cropFactor = _videoConnection.videoScaleAndCropFactor;
    NSLog(@"Video scale and crop factor %f", cropFactor);
	return;
}

- (void)teardownCaptureSession
{
	if ( _captureSession )
	{
		[[NSNotificationCenter defaultCenter] removeObserver:self name:nil object:_captureSession];
		
		[[NSNotificationCenter defaultCenter] removeObserver:_applicationWillEnterForegroundNotificationObserver];
		_applicationWillEnterForegroundNotificationObserver = nil;
		
		_captureSession = nil;
		
		_videoCompressionSettings = nil;
		_audioCompressionSettings = nil;
	}
}

- (void)captureSessionNotification:(NSNotification *)notification
{
	dispatch_async( _sessionQueue, ^{
		
		if ( [notification.name isEqualToString:AVCaptureSessionWasInterruptedNotification] )
		{
			NSLog( @"session interrupted" );
			
			[self captureSessionDidStopRunning];
		}
		else if ( [notification.name isEqualToString:AVCaptureSessionInterruptionEndedNotification] )
		{
			NSLog( @"session interruption ended" );
		}
		else if ( [notification.name isEqualToString:AVCaptureSessionRuntimeErrorNotification] )
		{
			[self captureSessionDidStopRunning];
			
			NSError *error = notification.userInfo[AVCaptureSessionErrorKey];
			if ( error.code == AVErrorDeviceIsNotAvailableInBackground )
			{
				NSLog( @"device not available in background" );

				// Since we can't resume running while in the background we need to remember this for next time we come to the foreground
				if ( _running ) {
					_startCaptureSessionOnEnteringForeground = YES;
				}
			}
			else if ( error.code == AVErrorMediaServicesWereReset )
			{
				NSLog( @"media services were reset" );
				[self handleRecoverableCaptureSessionRuntimeError:error];
			}
			else
			{
				[self handleNonRecoverableCaptureSessionRuntimeError:error];
			}
		}
		else if ( [notification.name isEqualToString:AVCaptureSessionDidStartRunningNotification] )
		{
			NSLog( @"session started running" );
		}
		else if ( [notification.name isEqualToString:AVCaptureSessionDidStopRunningNotification] )
		{
			NSLog( @"session stopped running" );
		}
	} );
}

- (void)handleRecoverableCaptureSessionRuntimeError:(NSError *)error
{
	if ( _running ) {
		[_captureSession startRunning];
	}
}

- (void)handleNonRecoverableCaptureSessionRuntimeError:(NSError *)error
{
	NSLog( @"fatal runtime error %@, code %i", error, (int)error.code );
	
	_running = NO;
	[self teardownCaptureSession];
	
	[self invokeDelegateCallbackAsync:^{
		[_delegate capturePipeline:self didStopRunningWithError:error];
	}];
}

- (void)captureSessionDidStopRunning
{
	[self stopRecording]; // a no-op if we aren't recording
	[self teardownVideoPipeline];
}

- (void)applicationWillEnterForeground
{
	NSLog( @"-[%@ %@] called", [self class], NSStringFromSelector(_cmd) );
	
	dispatch_sync( _sessionQueue, ^{
		
		if ( _startCaptureSessionOnEnteringForeground )
		{
			NSLog( @"-[%@ %@] manually restarting session", [self class], NSStringFromSelector(_cmd) );
			
			_startCaptureSessionOnEnteringForeground = NO;
			if ( _running ) {
				[_captureSession startRunning];
			}
		}
	} );
}

#pragma mark Capture Pipeline

- (void)setupVideoPipelineWithInputFormatDescription:(CMFormatDescriptionRef)inputFormatDescription
{
	NSLog( @"-[%@ %@] called", [self class], NSStringFromSelector(_cmd) );
	
    [self videoPipelineWillStartRunning];
	
    [self.videoTimeConverter setSampleBufferClock:_captureSession.masterClock];
    
    [self.videoTimeConverter checkStatus];
    
	self.videoDimensions = CMVideoFormatDescriptionGetDimensions( inputFormatDescription );
    self.fx = [self reportLensFocalLenParams];
	[_renderer prepareForInputWithFormatDescription:inputFormatDescription outputRetainedBufferCountHint:RETAINED_BUFFER_COUNT];
	
	if ( ! _renderer.operatesInPlace && [_renderer respondsToSelector:@selector(outputFormatDescription)] ) {
		self.outputVideoFormatDescription = _renderer.outputFormatDescription;
	}
	else {
		self.outputVideoFormatDescription = inputFormatDescription;
	}
}

// synchronous, blocks until the pipeline is drained, don't call from within the pipeline
- (void)teardownVideoPipeline
{
	// The session is stopped so we are guaranteed that no new buffers are coming through the video data output.
	// There may be inflight buffers on _videoDataOutputQueue however.
	// Synchronize with that queue to guarantee no more buffers are in flight.
	// Once the pipeline is drained we can tear it down safely.

	NSLog( @"-[%@ %@] called", [self class], NSStringFromSelector(_cmd) );
	
	dispatch_sync( _videoDataOutputQueue, ^{
		
		if ( ! self.outputVideoFormatDescription ) {
			return;
		}
		
		self.outputVideoFormatDescription = NULL;
		[_renderer reset];
		self.currentPreviewPixelBuffer = NULL;
		
		NSLog( @"-[%@ %@] finished teardown", [self class], NSStringFromSelector(_cmd) );
		
		[self videoPipelineDidFinishRunning];
	} );
}

- (void)videoPipelineWillStartRunning
{
	NSLog( @"-[%@ %@] called", [self class], NSStringFromSelector(_cmd) );
	
	NSAssert( _pipelineRunningTask == UIBackgroundTaskInvalid, @"should not have a background task active before the video pipeline starts running" );
	
	_pipelineRunningTask = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
		NSLog( @"video capture pipeline background task expired" );
	}];
}

- (void)videoPipelineDidFinishRunning
{
	NSLog( @"-[%@ %@] called", [self class], NSStringFromSelector(_cmd) );
	
	NSAssert( _pipelineRunningTask != UIBackgroundTaskInvalid, @"should have a background task active when the video pipeline finishes running" );
	
	[[UIApplication sharedApplication] endBackgroundTask:_pipelineRunningTask];
	_pipelineRunningTask = UIBackgroundTaskInvalid;
}

- (void)videoPipelineDidRunOutOfBuffers
{
	// We have run out of buffers.
	// Tell the delegate so that it can flush any cached buffers.
	
	[self invokeDelegateCallbackAsync:^{
		[_delegate capturePipelineDidRunOutOfPreviewBuffers:self];
	}];
}

- (void)setRenderingEnabled:(BOOL)renderingEnabled
{
	@synchronized( _renderer ) {
		_renderingEnabled = renderingEnabled;
	}
}

- (BOOL)renderingEnabled
{
	@synchronized( _renderer ) {
		return _renderingEnabled;
	}
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
	CMFormatDescriptionRef formatDescription = CMSampleBufferGetFormatDescription( sampleBuffer );
	
	if ( connection == _videoConnection )
	{
		if ( self.outputVideoFormatDescription == NULL ) {
			// Don't render the first sample buffer.
			// This gives us one frame interval (33ms at 30fps) for setupVideoPipelineWithInputFormatDescription: to complete.
			// Ideally this would be done asynchronously to ensure frames don't back up on slower devices.
			[self setupVideoPipelineWithInputFormatDescription:formatDescription];
		}
		else {
            [self renderVideoSampleBuffer:sampleBuffer fromConnection:connection];
		}
	}
	else if ( connection == _audioConnection )
	{
		self.outputAudioFormatDescription = formatDescription;
		
		@synchronized( self ) {
			if ( _recordingStatus == RosyWriterRecordingStatusRecording ) {
				[_recorder appendAudioSampleBuffer:sampleBuffer];
			}
		}
	}
}


- (void)renderVideoSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
	CVPixelBufferRef renderedPixelBuffer = NULL;
    [_videoTimeConverter convertSampleBufferTimeToMotionClock:sampleBuffer];
    CMTime timestamp = getAttachmentTime(sampleBuffer); // synced timestamp to inertial sensor clock
    // It is purported that the live camera frame is rectified by the look-up-table
    // of lens distortion. see https://forums.developer.apple.com/thread/79806
    
    NSMutableArray *array = nil;
    if (@available(iOS 11.0, *)) {
        CFDataRef intrinsicMatEncoded = CMGetAttachment(sampleBuffer, kCMSampleBufferAttachmentKey_CameraIntrinsicMatrix, NULL);
        NSData *pns = (__bridge NSData *) intrinsicMatEncoded;
        float intrinsicParam;
        array = [[NSMutableArray alloc] initWithCapacity:pns.length];
        for (NSUInteger offset = 0; offset < pns.length; offset += sizeof(float)) {
            [pns getBytes:&intrinsicParam range:NSMakeRange(offset, sizeof(float))];
            [array addObject:[NSNumber numberWithFloat:intrinsicParam]];
        }
        // fx, 0, 0, 0
        // 0, fy, 0, 0
        // cx, cy, 0, 1
        // NSLog(@"fx:%@, fy:%@, cx:%@, cy:%@", array[0], array[5], array[8], array[9]);
        self.fx = [array[0] floatValue];
    } else { // use the computed value for the first frame
        const int arrayLen = 12;
        array = [[NSMutableArray alloc] initWithCapacity:arrayLen];
        for (int index = 0; index < arrayLen; ++index) {
            [array addObject:[NSNumber numberWithFloat:0.0f]];
        }
        [array setObject:[NSNumber numberWithFloat:_fx] atIndexedSubscript:0];
        [array setObject:[NSNumber numberWithFloat:_fx] atIndexedSubscript:5];
        [array setObject:[NSNumber numberWithFloat:_videoDimensions.width/2 - 0.5f]
                atIndexedSubscript:8];
        [array setObject:[NSNumber numberWithFloat:_videoDimensions.height/2 - 0.5f]
                atIndexedSubscript:9];
        [array setObject:[NSNumber numberWithFloat:1.0f] atIndexedSubscript:11];
    }
    
    _exposureDuration = CMTimeGetNanoseconds(_videoDevice.exposureDuration);
	[self calculateFramerateAtTimestamp:timestamp];
    
    // for debug only
    // see https://stackoverflow.com/questions/34924476/avcapturedevice-comparing-samplebuffer-timestamps
//    AVCaptureInputPort *port = [[connection inputPorts] objectAtIndex:0];
//    CMClockRef originalClock = [port clock];
//    CMTime originalPTS = CMSyncConvertTime( timestamp, [_captureSession masterClock], originalClock );
//    bool isDroppedExposureFrame = CMTimeCompare( originalPTS, adjustExpFinishTime ) == 0;
//    if (isDroppedExposureFrame) {
//        NSLog(@"Discovered first frame applied customized exposure at %.3f", CMTimeGetSeconds(timestamp));
//        adjustExpFinishTime = CMTimeMake(0, 1);
//    }
    
    // More info about syncing CoreMotion inertial data and AVCaptureInput sampleBuffer can be found at below links.
    // videosnake 1.0 obj c: https://github.com/alokc83/iOS-Example-Collections/blob/8774c5b24e14cb2cdf79a6e3b13ee38739ad0a45/WWDC_2012_SourceCode/OS%20X/520%20-%20What's%20New%20in%20Camera%20Capture/VideoSnake/Classes/MotionSynchronizer.m
    // videosnake 2.2 obj c: https://github.com/robovm/apple-ios-samples/blob/master/VideoSnake/Classes/Utilities/MotionSynchronizer.m
    // videosnake 2.2 swift: https://github.com/ooper-shlab/VideoSnake2.2-Swift
    
    if ( !_adjustExposureFinished )
        return;
    
	// We must not use the GPU while running in the background.
	// setRenderingEnabled: takes the same lock so the caller can guarantee no GPU usage once the setter returns.
	@synchronized( _renderer )
	{
		if ( _renderingEnabled ) {
			CVPixelBufferRef sourcePixelBuffer = CMSampleBufferGetImageBuffer( sampleBuffer );
			renderedPixelBuffer = [_renderer copyRenderedPixelBuffer:sourcePixelBuffer];
		}
		else {
			return;
		}
	}
	
	if ( renderedPixelBuffer )
	{
		@synchronized( self )
		{
			[self outputPreviewPixelBuffer:renderedPixelBuffer];
			
			if ( _recordingStatus == RosyWriterRecordingStatusRecording ) {
                [_recorder appendVideoPixelBuffer:renderedPixelBuffer withPresentationTime:timestamp withIntrinsicMat:[array copy] withExposureDuration:_exposureDuration];
			}
		}
		
		CFRelease( renderedPixelBuffer );
	}
	else
	{
		[self videoPipelineDidRunOutOfBuffers];
	}
}

// call under @synchronized( self )
- (void)outputPreviewPixelBuffer:(CVPixelBufferRef)previewPixelBuffer
{
	// Keep preview latency low by dropping stale frames that have not been picked up by the delegate yet
	// Note that access to currentPreviewPixelBuffer is protected by the @synchronized lock
	self.currentPreviewPixelBuffer = previewPixelBuffer;
	
	[self invokeDelegateCallbackAsync:^{
		
		CVPixelBufferRef currentPreviewPixelBuffer = NULL;
		@synchronized( self )
		{
			currentPreviewPixelBuffer = self.currentPreviewPixelBuffer;
			if ( currentPreviewPixelBuffer ) {
				CFRetain( currentPreviewPixelBuffer );
				self.currentPreviewPixelBuffer = NULL;
			}
		}
		
		if ( currentPreviewPixelBuffer ) {
			[_delegate capturePipeline:self previewPixelBufferReadyForDisplay:currentPreviewPixelBuffer];
			CFRelease( currentPreviewPixelBuffer );
		}
	}];
}

#pragma mark Recording

- (void)startRecording
{
    [self resetOutputFolder];
	@synchronized( self )
	{
		if ( _recordingStatus != RosyWriterRecordingStatusIdle ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Already recording" userInfo:nil];
			return;
		}
		
		[self transitionToRecordingStatus:RosyWriterRecordingStatusStartingRecording error:nil];
	}
	
    [_inertialRecorder switchRecording];
    
	dispatch_queue_t callbackQueue = dispatch_queue_create( "com.apple.sample.capturepipeline.recordercallback", DISPATCH_QUEUE_SERIAL ); // guarantee ordering of callbacks with a serial queue
	MovieRecorder *recorder = [[MovieRecorder alloc] initWithURL:_recordingURL delegate:self callbackQueue:callbackQueue];
	
#if RECORD_AUDIO
	[recorder addAudioTrackWithSourceFormatDescription:self.outputAudioFormatDescription settings:_audioCompressionSettings];
#endif // RECORD_AUDIO
	
	CGAffineTransform videoTransform = [self transformFromVideoBufferOrientationToOrientation:self.recordingOrientation withAutoMirroring:NO]; // Front camera recording shouldn't be mirrored

	[recorder addVideoTrackWithSourceFormatDescription:self.outputVideoFormatDescription transform:videoTransform settings:_videoCompressionSettings];
	_recorder = recorder;
	
	[recorder prepareToRecord]; // asynchronous, will call us back with recorderDidFinishPreparing: or recorder:didFailWithError: when done
}

- (void)stopRecording
{
	@synchronized( self )
	{
		if ( _recordingStatus != RosyWriterRecordingStatusRecording ) {
			return;
		}
		
		[self transitionToRecordingStatus:RosyWriterRecordingStatusStoppingRecording error:nil];
	}
	
    [_inertialRecorder switchRecording];
    
	[_recorder finishRecording]; // asynchronous, will call us back with recorderDidFinishRecording: or recorder:didFailWithError: when done
}

#pragma mark MovieRecorder Delegate

- (void)movieRecorderDidFinishPreparing:(MovieRecorder *)recorder
{
	@synchronized( self )
	{
		if ( _recordingStatus != RosyWriterRecordingStatusStartingRecording ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Expected to be in StartingRecording state" userInfo:nil];
			return;
		}
		
		[self transitionToRecordingStatus:RosyWriterRecordingStatusRecording error:nil];
	}
}

- (void)movieRecorder:(MovieRecorder *)recorder didFailWithError:(NSError *)error
{
	@synchronized( self )
	{
		_recorder = nil;
		[self transitionToRecordingStatus:RosyWriterRecordingStatusIdle error:error];
	}
}

- (void)movieRecorderDidFinishRecording:(MovieRecorder *)recorder
{
	@synchronized( self )
	{
		if ( _recordingStatus != RosyWriterRecordingStatusStoppingRecording ) {
			@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Expected to be in StoppingRecording state" userInfo:nil];
			return;
		}
		
		// No state transition, we are still in the process of stopping.
		// We will be stopped once we save to the assets library.
	}
	
    NSMutableArray *savedFrameTimestamps = _recorder.savedFrameTimestamps;
    NSMutableArray *savedFrameIntrinsics = _recorder.savedFrameIntrinsics;
    NSMutableArray *savedExposureDurations = _recorder.savedExposureDurations;
    __block NSURL *savedAssetURL;
	_recorder = nil;
	
	ALAssetsLibrary *library = [[ALAssetsLibrary alloc] init];
    
    // unfortunately, the assetURL returned from the asynchronous function is often null
	[library writeVideoAtPathToSavedPhotosAlbum:_recordingURL completionBlock:^(NSURL *assetURL, NSError *error) {
        savedAssetURL = assetURL;
        
		[[NSFileManager defaultManager] removeItemAtURL:_recordingURL error:NULL];
		
 		@synchronized( self )
		{
			if ( _recordingStatus != RosyWriterRecordingStatusStoppingRecording ) {
				@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"Expected to be in StoppingRecording state" userInfo:nil];
				return;
			}
			[self transitionToRecordingStatus:RosyWriterRecordingStatusIdle error:error];
		}
	}];
    
    NSString *videoDataFilepath = savedAssetURL.absoluteString;
    // In older ios, _savedFrameIntrinsics can have 0 count
    NSLog(@"Video at %@ of URL %@ finished recording with %lu timestamps and %lu intrinsic mats and %lu exposure durations", videoDataFilepath, savedAssetURL, (unsigned long)[savedFrameTimestamps count],
        (unsigned long)[savedFrameIntrinsics count], (unsigned long)[savedExposureDurations count]);
    NSMutableString *mainString = [[NSMutableString alloc]initWithString:@"Timestamp[nanosec], fx[px], fy[px], cx[px], cy[px], exposure duration[nanosec]\n"];
    bool hasIntrinsics = false;
    if ([savedFrameIntrinsics count] > 0) {
        hasIntrinsics = true;
    }
    for(unsigned long i=0; i<(unsigned long)[savedFrameTimestamps count]; i++) {
        NSNumber *nn = [savedFrameTimestamps objectAtIndex:i];
        NSNumber *ed = [savedExposureDurations objectAtIndex:i];
        if (hasIntrinsics) {
            NSArray *intrinsic3x3 = [savedFrameIntrinsics objectAtIndex:i];
            [mainString appendFormat:@"%lld, %@, %@, %@, %@, %lld\n", [nn longLongValue], [intrinsic3x3 objectAtIndex:0], [intrinsic3x3 objectAtIndex:5], [intrinsic3x3 objectAtIndex:8], [intrinsic3x3 objectAtIndex:9], [ed longLongValue]];
        } else {
            [mainString appendFormat:@"%lld, %.2f, %.2f, %.2f, %.2f, %lld\n", [nn longLongValue], 1.0, 1.0, 0.5, 0.5, [ed longLongValue]];
        }
    }
    
    NSData *settingsData = [mainString dataUsingEncoding: NSUTF8StringEncoding allowLossyConversion:false];
    // to show these documents in Files app, edit info.plist as suggested in
    // https://www.bignerdranch.com/blog/working-with-the-files-app-in-ios-11/
    if ([settingsData writeToURL:_metadataFileURL atomically:YES]) {
        NSLog(@"Written video metadata to %@", _metadataFileURL);
    }
    else {
        NSLog(@"Failed to record video metadata to %@", _metadataFileURL);
    }
}

#pragma mark Recording State Machine

// call under @synchonized( self )
- (void)transitionToRecordingStatus:(RosyWriterRecordingStatus)newStatus error:(NSError *)error
{
	RosyWriterRecordingStatus oldStatus = _recordingStatus;
	_recordingStatus = newStatus;
	
#if LOG_STATUS_TRANSITIONS
	NSLog( @"RosyWriterCapturePipeline recording state transition: %@->%@", [self stringForRecordingStatus:oldStatus], [self stringForRecordingStatus:newStatus] );
#endif
	
	if ( newStatus != oldStatus )
	{
		dispatch_block_t delegateCallbackBlock = nil;
		
		if ( error && ( newStatus == RosyWriterRecordingStatusIdle ) )
		{
			delegateCallbackBlock = ^{ [_delegate capturePipeline:self recordingDidFailWithError:error]; };
		}
		else
		{
			if ( ( oldStatus == RosyWriterRecordingStatusStartingRecording ) && ( newStatus == RosyWriterRecordingStatusRecording ) ) {
				delegateCallbackBlock = ^{ [_delegate capturePipelineRecordingDidStart:self]; };
			}
			else if ( ( oldStatus == RosyWriterRecordingStatusRecording ) && ( newStatus == RosyWriterRecordingStatusStoppingRecording ) ) {
				delegateCallbackBlock = ^{ [_delegate capturePipelineRecordingWillStop:self]; };
			}
			else if ( ( oldStatus == RosyWriterRecordingStatusStoppingRecording ) && ( newStatus == RosyWriterRecordingStatusIdle ) ) {
				delegateCallbackBlock = ^{ [_delegate capturePipelineRecordingDidStop:self]; };
			}
		}
		
		if ( delegateCallbackBlock )
		{
			[self invokeDelegateCallbackAsync:delegateCallbackBlock];
		}
	}
}

#if LOG_STATUS_TRANSITIONS

- (NSString *)stringForRecordingStatus:(RosyWriterRecordingStatus)status
{
	NSString *statusString = nil;
	
	switch ( status )
	{
		case RosyWriterRecordingStatusIdle:
			statusString = @"Idle";
			break;
		case RosyWriterRecordingStatusStartingRecording:
			statusString = @"StartingRecording";
			break;
		case RosyWriterRecordingStatusRecording:
			statusString = @"Recording";
			break;
		case RosyWriterRecordingStatusStoppingRecording:
			statusString = @"StoppingRecording";
			break;
		default:
			statusString = @"Unknown";
			break;
	}
	return statusString;
}

#endif // LOG_STATUS_TRANSITIONS

#pragma mark Utilities

- (void)invokeDelegateCallbackAsync:(dispatch_block_t)callbackBlock
{
	dispatch_async( _delegateCallbackQueue, ^{
		@autoreleasepool {
			callbackBlock();
		}
	} );
}

// Auto mirroring: Front camera is mirrored; back camera isn't 
- (CGAffineTransform)transformFromVideoBufferOrientationToOrientation:(AVCaptureVideoOrientation)orientation withAutoMirroring:(BOOL)mirror
{
	CGAffineTransform transform = CGAffineTransformIdentity;
		
	// Calculate offsets from an arbitrary reference orientation (portrait)
	CGFloat orientationAngleOffset = angleOffsetFromPortraitOrientationToOrientation( orientation );
	CGFloat videoOrientationAngleOffset = angleOffsetFromPortraitOrientationToOrientation( _videoBufferOrientation );
	
	// Find the difference in angle between the desired orientation and the video orientation
	CGFloat angleOffset = orientationAngleOffset - videoOrientationAngleOffset;
	transform = CGAffineTransformMakeRotation( angleOffset );

	if ( _videoDevice.position == AVCaptureDevicePositionFront )
	{
		if ( mirror ) {
			transform = CGAffineTransformScale( transform, -1, 1 );
		}
		else {
			if ( UIInterfaceOrientationIsPortrait( (UIInterfaceOrientation)orientation ) ) {
				transform = CGAffineTransformRotate( transform, M_PI );
			}
		}
	}
	
	return transform;
}

static CGFloat angleOffsetFromPortraitOrientationToOrientation(AVCaptureVideoOrientation orientation)
{
	CGFloat angle = 0.0;
	
	switch ( orientation )
	{
		case AVCaptureVideoOrientationPortrait:
			angle = 0.0;
			break;
		case AVCaptureVideoOrientationPortraitUpsideDown:
			angle = M_PI;
			break;
		case AVCaptureVideoOrientationLandscapeRight:
			angle = -M_PI_2;
			break;
		case AVCaptureVideoOrientationLandscapeLeft:
			angle = M_PI_2;
			break;
		default:
			break;
	}
	
	return angle;
}

- (void)calculateFramerateAtTimestamp:(CMTime)timestamp
{
	[_previousSecondTimestamps addObject:[NSValue valueWithCMTime:timestamp]];
	
	CMTime oneSecond = CMTimeMake( 1, 1 );
	CMTime oneSecondAgo = CMTimeSubtract( timestamp, oneSecond );
	
	while( CMTIME_COMPARE_INLINE( [_previousSecondTimestamps[0] CMTimeValue], <, oneSecondAgo ) ) {
		[_previousSecondTimestamps removeObjectAtIndex:0];
	}
	
	if ( [_previousSecondTimestamps count] > 1 )
	{
		const Float64 duration = CMTimeGetSeconds( CMTimeSubtract( [[_previousSecondTimestamps lastObject] CMTimeValue], [_previousSecondTimestamps[0] CMTimeValue] ) );
		const float newRate = (float)( [_previousSecondTimestamps count] - 1 ) / duration;
		self.videoFrameRate = newRate;
	}
}

- (float)reportLensFocalLenParams
{
    AVCaptureDevice *device = _videoDevice;
    float lensPos = [device lensPosition];
    CGFloat videoZoom = [device videoZoomFactor];
    float videoHFov = device.activeFormat.videoFieldOfView;
    videoHFov *= M_PI/180.0;
    int w = self.videoDimensions.width;
    // On devices iOS 11+ the computed focal length in pixels is greater than
    // the value from the intrinsic matrix. Eg.,
    // when w=1280, focalLen=1139.7, focalIntrinsic=1090
    float focalLen = (w/2) / tan(videoHFov/2);
    NSLog(@"lensPos %.4f videoZoom %f HFOV %.4f w %d focalLen %.4f",
          lensPos, videoZoom, videoHFov, w, focalLen);
    return focalLen;
}

- (void)focusAtPoint:(CGPoint)point
{
    AVCaptureDevice *device = _videoDevice;
    BOOL autoFocusLocked = FALSE;
    if (device.isFocusPointOfInterestSupported && [device isFocusModeSupported:AVCaptureFocusModeAutoFocus]) {
        NSError *error;
        if ([device lockForConfiguration:&error]) {
            device.focusPointOfInterest = point;
            device.focusMode = AVCaptureFocusModeAutoFocus;
            [device unlockForConfiguration];
            NSLog(@"Camera focused at %.3f, %.3f", point.x, point.y);
            autoFocusLocked = TRUE;
        } else {
            NSLog(@"Camera error in locking autofocus: %@", error);
        }
    }
    
    if (device.isExposurePointOfInterestSupported && [device isExposureModeSupported:AVCaptureExposureModeAutoExpose]) {
        NSError* error;
        AVCaptureDeviceFormat *format = [device activeFormat];
        CMTime oldDuration = device.exposureDuration;
        float oldISO = device.ISO;
        CMTime expectedDuration;
        float expectedISO;
        computeExpectedExposureTimeAndIso(format, &oldDuration, oldISO, &expectedDuration, &expectedISO);
        
        if ([device lockForConfiguration:&error]) {
            if (/* DISABLES CODE */ (1)) {
                // method 1: fix both exposureDuration and ISO at specified values
                // see https://stackoverflow.com/questions/40604334/correct-iso-value-for-avfoundation-camera
                _adjustExposureFinished = FALSE;
                [device setExposureModeCustomWithDuration:expectedDuration ISO:expectedISO completionHandler:^(CMTime syncTime) {
                    self->adjustExpFinishTime = syncTime;
                    self->_exposureDuration = CMTimeGetNanoseconds(device.exposureDuration);
                    self->_adjustExposureFinished = TRUE;
                }];
            } else {
                // method 2: fix both exposureDuration and ISO at a value adjusted by the auto exposure algorithm
                device.exposurePointOfInterest = point;
                device.exposureMode = AVCaptureExposureModeAutoExpose;
            }
            [device unlockForConfiguration];
            _autoLocked = autoFocusLocked;
        } else {
            NSLog(@"Camera error in locking autoexposure: %@", error);
            _autoLocked = FALSE;
        }
    }
}
- (void)unlockFocusAndExposure {
    AVCaptureDevice *device = _videoDevice;
    BOOL autoFocusEnabled = FALSE;
    if ([device isFocusModeSupported:AVCaptureFocusModeContinuousAutoFocus]) {
        NSError *error;
        if ([device lockForConfiguration:&error]) {
            device.focusMode = AVCaptureFocusModeContinuousAutoFocus;
            [device unlockForConfiguration];
            autoFocusEnabled = TRUE;
            NSLog(@"Camera auto focus enabled");
        } else {
            NSLog(@"Camera error in lockForConfiguration for focus: %@", error);
        }
    }
    
    if ([device isExposureModeSupported:AVCaptureExposureModeContinuousAutoExposure]) {
        NSError *error;
        
        if ([device lockForConfiguration:&error]) {
            device.exposureMode = AVCaptureExposureModeContinuousAutoExposure;
            [device unlockForConfiguration];
            
            _autoLocked = !autoFocusEnabled;
            NSLog(@"Camera auto exposure enabled");
        } else {
            NSLog(@"Camera error in lockForConfiguration for exposure: %@", error);
        }
    }
}

- (NSURL *)getInertialFileURL {
    return _inertialRecorder.fileURL;
}

- (void)resetOutputFolder {
    NSURL *outputFolderURL = createOutputFolderURL();
    NSURL *inertialFileURL = [outputFolderURL URLByAppendingPathComponent:IMU_OUTPUT_FILENAME isDirectory:NO];
    [_inertialRecorder setFileURL:inertialFileURL];
    _metadataFileURL = [outputFolderURL URLByAppendingPathComponent:VIDEO_META_FILENAME isDirectory:NO];
}

@end
