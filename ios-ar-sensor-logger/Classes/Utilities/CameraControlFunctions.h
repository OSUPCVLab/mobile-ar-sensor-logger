#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CMTime.h>

void computeExpectedExposureTimeAndIso(AVCaptureDeviceFormat *format,
                                       CMTime *oldDuration, float oldISO,
                                       CMTime *expectedDuration, float *expectedISO);

/**
 Warn: This function does not return meaningful path at the moment.
 */
NSString *getAssetPath(NSString * assetLocalIdentifier);
