
#import <Foundation/Foundation.h>
#import <CoreMedia/CMSampleBuffer.h>
#import <CoreMedia/CMSync.h>


// Conversion of time between different clocks.
// see: https://github.com/robovm/apple-ios-samples/blob/master/UsingAVFoundationAPIstorecordamoviewithlocationmetadata/AVCaptureLocation/AAPLCaptureManager.m
// Clocks   NSDate  sampleBufferClock=captureSession.masterClock    motionClock=CMClockGetHostTimeClock()

@interface VideoTimeConverter : NSObject

@property(nonatomic, retain) __attribute__((NSObject)) CMClockRef sampleBufferClock;

- (void)checkStatus;

// captureSession.masterClock to HostTimeClock
- (void)convertSampleBufferTimeToMotionClock:(CMSampleBufferRef)sampleBuffer;

// NSDate to captureSession.masterClock
- (CMTime)movieTimeForLocationTime:(NSDate *)date;

@end

CMTime getAttachmentTime(CMSampleBufferRef mediaSample);

int64_t CMTimeGetNanoseconds(CMTime time);

int64_t CMTimeGetMilliseconds(CMTime time);

NSString *secDoubleToNanoString(double time);

// NSDate to HostTimeClock
CMTime CMTimeForNSDate(NSDate *date);

NSString *NSDateToString(NSDate *date);
