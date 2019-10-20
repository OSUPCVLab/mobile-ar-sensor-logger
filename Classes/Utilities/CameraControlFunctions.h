#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CMTime.h>

void computeExpectedExposureTimeAndIso(AVCaptureDeviceFormat *format,
                                       CMTime *oldDuration, float oldISO,
                                       CMTime *expectedDuration, float *expectedISO);
