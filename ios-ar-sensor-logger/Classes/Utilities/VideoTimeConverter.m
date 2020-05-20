
#import "VideoTimeConverter.h"
#import <CoreMotion/CoreMotion.h>

CFStringRef const VIDEOSNAKE_REMAPPED_PTS = CFSTR("RemappedPTS");
const int32_t kSecToNanos = 1000000000;

@interface VideoTimeConverter () {

}

@property(nonatomic, retain) __attribute__((NSObject)) CMClockRef motionClock;

@end

@implementation VideoTimeConverter

- (id)init
{
    self = [super init];
    if (self != nil) {
        _motionClock = CMClockGetHostTimeClock();
        if (_motionClock)
            CFRetain(_motionClock);
    }
    
    return self;
}

- (void)dealloc
{
    if (_sampleBufferClock)
        CFRelease(_sampleBufferClock);
    if (_motionClock)
        CFRelease(_motionClock);
}

- (void)checkStatus
{
    if ( self.sampleBufferClock == NULL ) {
        @throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"No sample buffer clock. Please set one before calling start." userInfo:nil];
        return;
    }
}

- (void)convertSampleBufferTimeToMotionClock:(CMSampleBufferRef)sampleBuffer
{
    CMTime originalPTS = CMSampleBufferGetPresentationTimeStamp(sampleBuffer);
    CMTime remappedPTS = originalPTS;
    if ( self.sampleBufferClock && self.motionClock ) {
        if ( !CFEqual(self.sampleBufferClock, self.motionClock) ) {
            remappedPTS = CMSyncConvertTime(originalPTS, self.sampleBufferClock, self.motionClock);
        }
    }
    // Attach the remapped timestamp to the buffer for use in -sync
    CFDictionaryRef remappedPTSDict = CMTimeCopyAsDictionary(remappedPTS, kCFAllocatorDefault);
    CMSetAttachment(sampleBuffer, VIDEOSNAKE_REMAPPED_PTS, remappedPTSDict, kCMAttachmentMode_ShouldPropagate);
    
    CFRelease(remappedPTSDict);
}

- (CMTime)movieTimeForLocationTime:(NSDate *)date
{
    CMTime locationTime = CMTimeForNSDate(date);
    CMTime locationMovieTime = CMSyncConvertTime(locationTime, CMClockGetHostTimeClock(), self.sampleBufferClock);
    return locationMovieTime;
}

@end

CMTime getAttachmentTime(CMSampleBufferRef mediaSample)
{
    CFDictionaryRef mediaTimeDict = CMGetAttachment(mediaSample, VIDEOSNAKE_REMAPPED_PTS, NULL);
    CMTime mediaTime = (mediaTimeDict) ? CMTimeMakeFromDictionary(mediaTimeDict) : CMSampleBufferGetPresentationTimeStamp(mediaSample);
    return mediaTime;
}

int64_t CMTimeGetNanoseconds(CMTime time) {
    CMTime timenano = CMTimeConvertScale(time, kSecToNanos, kCMTimeRoundingMethod_Default);
    return timenano.value;
}

int64_t CMTimeGetMilliseconds(CMTime time) {
    CMTime timenano = CMTimeConvertScale(time, 1000, kCMTimeRoundingMethod_Default);
    return timenano.value;
}

NSString *secDoubleToNanoString(double time) {
    double integral;
    double fractional = modf(time, &integral);
    fractional *= kSecToNanos;
    return [NSString stringWithFormat:@"%.0f%09.0f", integral, fractional];
}

CMTime CMTimeForNSDate(NSDate *date) {
    CMTime now = CMClockGetTime(CMClockGetHostTimeClock());
    NSTimeInterval elapsed = -(date.timeIntervalSinceNow); // this will be a negative number if date was in the past (it should be).
    CMTime eventTime = CMTimeSubtract(now, CMTimeMake(elapsed * now.timescale, now.timescale));
    return eventTime;
}

NSString *NSDateToString(NSDate *date) {
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat:@"yyyy_MM_dd_HH_mm_ss.SSS"];
    //Optionally for time zone conversions
//    [formatter setTimeZone:[NSTimeZone timeZoneWithName:@"..."]];
    return [dateFormatter stringFromDate:date];
}
