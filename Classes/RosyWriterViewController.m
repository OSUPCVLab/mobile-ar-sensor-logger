
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 View controller for camera interface
 */

#import "RosyWriterViewController.h"
#import "RosyWriterViewController+Helper.h"

#import <QuartzCore/QuartzCore.h>
#import "RosyWriterCapturePipeline.h"
#import "OpenGLPixelBufferView.h"

@interface RosyWriterViewController () <RosyWriterCapturePipelineDelegate, UIGestureRecognizerDelegate>
{
	BOOL _addedObservers;
	BOOL _recording;
	UIBackgroundTaskIdentifier _backgroundRecordingID;
	BOOL _allowedToUseGPU;
	
	NSTimer *_labelTimer;
	OpenGLPixelBufferView *_previewView;
	RosyWriterCapturePipeline *_capturePipeline;
}

@property (weak, nonatomic) IBOutlet UIView *preview;

@property(nonatomic, strong) IBOutlet UIBarButtonItem *recordButton;
@property(nonatomic, strong) IBOutlet UILabel *framerateLabel;
@property(nonatomic, strong) IBOutlet UILabel *dimensionsLabel;
@property (weak, nonatomic) IBOutlet UILabel *lockAutoLabel;

@property (strong, nonatomic) AVCaptureDevice *videoCaptureDevice;
@property (strong, nonatomic) CALayer *focusBoxLayer;
@property (strong, nonatomic) CAAnimation *focusBoxAnimation;
@property (strong, nonatomic) AVCaptureVideoPreviewLayer *captureVideoPreviewLayer;
@end

@implementation RosyWriterViewController

- (void)dealloc
{
	if ( _addedObservers )
	{
		[[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidEnterBackgroundNotification object:[UIApplication sharedApplication]];
		[[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationWillEnterForegroundNotification object:[UIApplication sharedApplication]];
		[[NSNotificationCenter defaultCenter] removeObserver:self name:UIDeviceOrientationDidChangeNotification object:[UIDevice currentDevice]];
		[[UIDevice currentDevice] endGeneratingDeviceOrientationNotifications];
	}
}

#pragma mark - View lifecycle

- (void)applicationDidEnterBackground
{
	// Avoid using the GPU in the background
	_allowedToUseGPU = NO;
	_capturePipeline.renderingEnabled = NO;

	[_capturePipeline stopRecording]; // a no-op if we aren't recording
	
	 // We reset the OpenGLPixelBufferView to ensure all resources have been cleared when going to the background.
	[_previewView reset];
}

- (void)applicationWillEnterForeground
{
	_allowedToUseGPU = YES;
	_capturePipeline.renderingEnabled = YES;
}

- (void)viewDidLoad
{
	_capturePipeline = [[RosyWriterCapturePipeline alloc] initWithDelegate:self callbackQueue:dispatch_get_main_queue()];
	
	[[NSNotificationCenter defaultCenter] addObserver:self
											 selector:@selector(applicationDidEnterBackground)
												 name:UIApplicationDidEnterBackgroundNotification
											   object:[UIApplication sharedApplication]];
	[[NSNotificationCenter defaultCenter] addObserver:self
											 selector:@selector(applicationWillEnterForeground)
												 name:UIApplicationWillEnterForegroundNotification
											   object:[UIApplication sharedApplication]];
	[[NSNotificationCenter defaultCenter] addObserver:self
											 selector:@selector(deviceOrientationDidChange)
												 name:UIDeviceOrientationDidChangeNotification
											   object:[UIDevice currentDevice]];
	
	// Keep track of changes to the device orientation so we can update the capture pipeline
	[[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
	
	_addedObservers = YES;
	
	// the willEnterForeground and didEnterBackground notifications are subsequently used to update _allowedToUseGPU
	_allowedToUseGPU = ( [UIApplication sharedApplication].applicationState != UIApplicationStateBackground );
	_capturePipeline.renderingEnabled = _allowedToUseGPU;
    
    
    // preview layer
    CGRect bounds = self.preview.layer.bounds;
    _captureVideoPreviewLayer = [[AVCaptureVideoPreviewLayer alloc] init];
    _captureVideoPreviewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
    _captureVideoPreviewLayer.bounds = bounds;
    _captureVideoPreviewLayer.position = CGPointMake(CGRectGetMidX(bounds), CGRectGetMidY(bounds));
    // [self.preview.layer addSublayer:_captureVideoPreviewLayer];
    
    // regardless captureVideoPreviewLayer is addSublayer to preview.layer, it's observed that self.preview.frame.size == captureVideoPreviewLayer.frame.size
    NSLog(@"previewlayer frame size %.3f %.3f super preview size %.3f %.3f", _captureVideoPreviewLayer.frame.size.width, _captureVideoPreviewLayer.frame.size.height,
          self.preview.frame.size.width, self.preview.frame.size.height);
    
    AVCaptureDevicePosition devicePosition = AVCaptureDevicePositionBack;
    if(devicePosition == AVCaptureDevicePositionUnspecified) {
        self.videoCaptureDevice = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    } else {
        self.videoCaptureDevice = [self cameraWithPosition:devicePosition];
    }
    
    // reference: https://stackoverflow.com/questions/11355671/how-do-i-implement-the-uitapgesturerecognizer-into-my-application
    UITapGestureRecognizer *tapGestureRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleTapFrom:)];
    tapGestureRecognizer.numberOfTouchesRequired = 1;
    tapGestureRecognizer.numberOfTapsRequired = 1;
    [self.preview addGestureRecognizer:tapGestureRecognizer];
    tapGestureRecognizer.delegate = self;
    _tapToFocus = YES;
    // add focus box to view
    [self addDefaultFocusBox];
    
    [super viewDidLoad];
}

// Find a camera with the specified AVCaptureDevicePosition, returning nil if one is not found
- (AVCaptureDevice *)cameraWithPosition:(AVCaptureDevicePosition) position
{
    NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
    for (AVCaptureDevice *device in devices) {
        if (device.position == position) return device;
    }
    return nil;
}


- (void)showFocusBox:(CGPoint)point
{
    if(self.focusBoxLayer) {
        // clear animations
        [self.focusBoxLayer removeAllAnimations];
        
        // move layer to the touch point
        [CATransaction begin];
        [CATransaction setValue: (id) kCFBooleanTrue forKey: kCATransactionDisableActions];
        self.focusBoxLayer.position = point;
        [CATransaction commit];
    }
    
    if(self.focusBoxAnimation) {
        // run the animation
        [self.focusBoxLayer addAnimation:self.focusBoxAnimation forKey:@"animateOpacity"];
    }
}

- (void)alterFocusBox:(CALayer *)layer animation:(CAAnimation *)animation
{
    self.focusBoxLayer = layer;
    self.focusBoxAnimation = animation;
}


- (void)addDefaultFocusBox
{
    CALayer *focusBox = [[CALayer alloc] init];
    focusBox.cornerRadius = 5.0f;
    focusBox.bounds = CGRectMake(0.0f, 0.0f, 70, 60);
    focusBox.borderWidth = 3.0f;
    focusBox.borderColor = [[UIColor yellowColor] CGColor];
    focusBox.opacity = 0.0f;
    [self.view.layer addSublayer:focusBox];
    
    CABasicAnimation *focusBoxAnimation = [CABasicAnimation animationWithKeyPath:@"opacity"];
    focusBoxAnimation.duration = 0.75;
    focusBoxAnimation.autoreverses = NO;
    focusBoxAnimation.repeatCount = 0.0;
    focusBoxAnimation.fromValue = [NSNumber numberWithFloat:1.0];
    focusBoxAnimation.toValue = [NSNumber numberWithFloat:0.0];
    if (_capturePipeline.autoLocked) {
        [self.lockAutoLabel setText:@"AE/AF locked"];
        [self.lockAutoLabel setHidden:FALSE];
    } else {
        [self.lockAutoLabel setText:@"AE/AF"];
        [self.lockAutoLabel setHidden:FALSE];
    }
    [self alterFocusBox:focusBox animation:focusBoxAnimation];
}


- (void) handleTapFrom: (UITapGestureRecognizer *)gestureRecognizer
{
    NSLog(@"tap recognizer sending taps");
    //Code to handle the gesture
    if(!self.tapToFocus) {
        return;
    }
    
    if (gestureRecognizer.state == UIGestureRecognizerStateEnded) {
        CGPoint touchedPoint = [gestureRecognizer locationInView:gestureRecognizer.view];
        
        CGPoint pointOfInterest = [self convertToPointOfInterestFromViewCoordinates:touchedPoint                                                                   previewLayer:self.captureVideoPreviewLayer                                                                 ports:_capturePipeline.videoDeviceInput.ports];
        [_capturePipeline focusAtPoint:pointOfInterest];
        if (_capturePipeline.autoLocked) {
            [self.lockAutoLabel setText:@"AE/AF locked"];
            [self.lockAutoLabel setHidden:FALSE];
        } else {
            [self.lockAutoLabel setText:@"AE/AF"];
            [self.lockAutoLabel setHidden:FALSE];
        }
        
        [self showFocusBox:touchedPoint];
        
    }
    
}

- (void)viewWillAppear:(BOOL)animated
{
	[super viewWillAppear:animated];
	
	[_capturePipeline startRunning];
	
	_labelTimer = [NSTimer scheduledTimerWithTimeInterval:0.5 target:self selector:@selector(updateLabels) userInfo:nil repeats:YES];
}

- (void)viewDidDisappear:(BOOL)animated
{
	[super viewDidDisappear:animated];
	
	[_labelTimer invalidate];
	_labelTimer = nil;
	
	[_capturePipeline stopRunning];
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations
{
	return UIInterfaceOrientationMaskPortrait;
}

- (BOOL)prefersStatusBarHidden
{
	return YES;
}

#pragma mark - UI

- (IBAction)toggleRecording:(id)sender
{
	if ( _recording )
	{
		[_capturePipeline stopRecording];
	}
	else
	{
		// Disable the idle timer while recording
		[UIApplication sharedApplication].idleTimerDisabled = YES;
		
		// Make sure we have time to finish saving the movie if the app is backgrounded during recording
		if ( [[UIDevice currentDevice] isMultitaskingSupported] ) {
			_backgroundRecordingID = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{}];
		}
		
		self.recordButton.enabled = NO; // re-enabled once recording has finished starting
		self.recordButton.title = @"Stop";
		
		[_capturePipeline startRecording];
		
		_recording = YES;
	}
}

- (void)recordingStopped
{
	_recording = NO;
	self.recordButton.enabled = YES;
	self.recordButton.title = @"Record";
	
	[UIApplication sharedApplication].idleTimerDisabled = NO;
	
	[[UIApplication sharedApplication] endBackgroundTask:_backgroundRecordingID];
	_backgroundRecordingID = UIBackgroundTaskInvalid;
}

- (void)setupPreviewView
{
	// Set up GL view
	_previewView = [[OpenGLPixelBufferView alloc] initWithFrame:CGRectZero];
	_previewView.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
	
	UIInterfaceOrientation currentInterfaceOrientation = [UIApplication sharedApplication].statusBarOrientation;
	_previewView.transform = [_capturePipeline transformFromVideoBufferOrientationToOrientation:(AVCaptureVideoOrientation)currentInterfaceOrientation withAutoMirroring:YES]; // Front camera preview should be mirrored

	[self.view insertSubview:_previewView atIndex:0];
	CGRect bounds = CGRectZero;
	bounds.size = [self.view convertRect:self.view.bounds toView:_previewView].size;
	_previewView.bounds = bounds;
	_previewView.center = CGPointMake( self.view.bounds.size.width/2.0, self.view.bounds.size.height/2.0 );
}

- (void)deviceOrientationDidChange
{
	UIDeviceOrientation deviceOrientation = [UIDevice currentDevice].orientation;
	
	// Update the recording orientation if the device changes to portrait or landscape orientation (but not face up/down)
	if ( UIDeviceOrientationIsPortrait( deviceOrientation ) || UIDeviceOrientationIsLandscape( deviceOrientation ) )
	{
		_capturePipeline.recordingOrientation = (AVCaptureVideoOrientation)deviceOrientation;
	}
}

- (void)updateLabels
{	
	NSString *frameRateString = [NSString stringWithFormat:@"%d FPS %.3f", (int)roundf( _capturePipeline.videoFrameRate ), _capturePipeline.fx];
	self.framerateLabel.text = frameRateString;
	
	NSString *dimensionsString = [NSString stringWithFormat:@"%d x %d", _capturePipeline.videoDimensions.width, _capturePipeline.videoDimensions.height];
	self.dimensionsLabel.text = dimensionsString;
}

- (void)showError:(NSError *)error
{
	UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:error.localizedDescription
														message:error.localizedFailureReason
													   delegate:nil
											  cancelButtonTitle:@"OK"
											  otherButtonTitles:nil];
	[alertView show];
}

#pragma mark - RosyWriterCapturePipelineDelegate

- (void)capturePipeline:(RosyWriterCapturePipeline *)capturePipeline didStopRunningWithError:(NSError *)error
{
	[self showError:error];
	
	self.recordButton.enabled = NO;
}

// Preview
- (void)capturePipeline:(RosyWriterCapturePipeline *)capturePipeline previewPixelBufferReadyForDisplay:(CVPixelBufferRef)previewPixelBuffer
{
	if ( ! _allowedToUseGPU ) {
		return;
	}
	
	if ( ! _previewView ) {
		[self setupPreviewView];
	}
	
	[_previewView displayPixelBuffer:previewPixelBuffer];
}

- (void)capturePipelineDidRunOutOfPreviewBuffers:(RosyWriterCapturePipeline *)capturePipeline
{
	if ( _allowedToUseGPU ) {
		[_previewView flushPixelBufferCache];
	}
}

// Recording
- (void)capturePipelineRecordingDidStart:(RosyWriterCapturePipeline *)capturePipeline
{
	self.recordButton.enabled = YES;
}

- (void)capturePipelineRecordingWillStop:(RosyWriterCapturePipeline *)capturePipeline
{
	// Disable record button until we are ready to start another recording
	self.recordButton.enabled = NO;
	self.recordButton.title = @"Record";
}

- (void)capturePipelineRecordingDidStop:(RosyWriterCapturePipeline *)capturePipeline
{
	[self recordingStopped];
}

- (void)capturePipeline:(RosyWriterCapturePipeline *)capturePipeline recordingDidFailWithError:(NSError *)error
{
	[self recordingStopped];
	[self showError:error];
}

@end
