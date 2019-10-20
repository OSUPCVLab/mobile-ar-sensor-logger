
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 Real-time movie recorder which is totally non-blocking
 */


#import <Foundation/Foundation.h>

#import <CoreMedia/CMFormatDescription.h>
#import <CoreMedia/CMSampleBuffer.h>

@protocol MovieRecorderDelegate;

@interface MovieRecorder : NSObject

@property(nonatomic, readonly) NSMutableArray *savedFrameTimestamps;
@property(nonatomic, readonly) NSMutableArray *savedFrameIntrinsics;
@property(nonatomic, readonly) NSMutableArray *savedExposureDurations;

- (instancetype)initWithURL:(NSURL *)URL delegate:(id<MovieRecorderDelegate>)delegate callbackQueue:(dispatch_queue_t)queue; // delegate is weak referenced

// Only one audio and video track each are allowed.
- (void)addVideoTrackWithSourceFormatDescription:(CMFormatDescriptionRef)formatDescription transform:(CGAffineTransform)transform settings:(NSDictionary *)videoSettings; // see AVVideoSettings.h for settings keys/values
- (void)addAudioTrackWithSourceFormatDescription:(CMFormatDescriptionRef)formatDescription settings:(NSDictionary *)audioSettings; // see AVAudioSettings.h for settings keys/values

// Asynchronous, might take several hundred milliseconds.
// When finished the delegate's recorderDidFinishPreparing: or recorder:didFailWithError: method will be called.
- (void)prepareToRecord;

// - (void)appendVideoSampleBuffer:(CMSampleBufferRef)sampleBuffer;

- (void)appendVideoPixelBuffer:(CVPixelBufferRef)pixelBuffer withPresentationTime:(CMTime)presentationTime withIntrinsicMat:(NSArray *)intrinsic3x3 withExposureDuration:(int64_t)exposureDuration;

- (void)appendAudioSampleBuffer:(CMSampleBufferRef)sampleBuffer;

// Asynchronous, might take several hundred milliseconds.
// When finished the delegate's recorderDidFinishRecording: or recorder:didFailWithError: method will be called.
- (void)finishRecording;

@end

@protocol MovieRecorderDelegate <NSObject>
@required
- (void)movieRecorderDidFinishPreparing:(MovieRecorder *)recorder;
- (void)movieRecorder:(MovieRecorder *)recorder didFailWithError:(NSError *)error;
- (void)movieRecorderDidFinishRecording:(MovieRecorder *)recorder;
@end
