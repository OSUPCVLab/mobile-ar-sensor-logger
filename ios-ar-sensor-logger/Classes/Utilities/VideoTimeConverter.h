
#import <Foundation/Foundation.h>
#import <CoreMedia/CMSampleBuffer.h>
#import <CoreMedia/CMSync.h>

@interface VideoTimeConverter : NSObject

@property(nonatomic, retain) __attribute__((NSObject)) CMClockRef sampleBufferClock;

- (void)checkStatus;

- (void)convertSampleBufferTimeToMotionClock:(CMSampleBufferRef)sampleBuffer;

@end

CMTime getAttachmentTime(CMSampleBufferRef mediaSample);

int64_t CMTimeGetNanoseconds(CMTime time);

int64_t CMTimeGetMilliseconds(CMTime time);

NSString *secDoubleToNanoString(double time);
