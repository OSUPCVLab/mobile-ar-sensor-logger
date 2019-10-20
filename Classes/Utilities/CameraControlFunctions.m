
#import "CameraControlFunctions.h"

const int64_t kDesiredExposureTimeMillisec = 5;

void computeExpectedExposureTimeAndIso(AVCaptureDeviceFormat *format,
                                       CMTime *oldDuration, float oldISO,
                                       CMTime *expectedDuration, float *expectedISO) {
    // eg., for iphone 6S format.minExposureDuration 1e-2 ms
    // format.maxExposureDuration 333.3 ms format.minISO 23 format.maxISO 736
    CMTime desiredDuration = CMTimeMake(kDesiredExposureTimeMillisec, 1000);
    float ratio = (float)(CMTimeGetSeconds(*oldDuration)/CMTimeGetSeconds(desiredDuration));
    NSLog(@"Present exposure duration %.5f ms and ISO %.5f",
          CMTimeGetSeconds(*oldDuration)*1000, oldISO);
    
    if (CMTIME_COMPARE_INLINE(desiredDuration, >, *oldDuration)) {
        *expectedDuration = *oldDuration;
        *expectedISO = oldISO;
    } else {
        *expectedDuration = desiredDuration;
        *expectedISO = oldISO * ratio;
        if (*expectedISO > format.maxISO)
            *expectedISO = format.maxISO;
        else if (*expectedISO < format.minISO)
            *expectedISO = format.minISO;
    }
    NSLog(@"Camera old exposure duration %.5f and ISO %.3f,"
          " desired exposure duration %.5f and ISO %.3f and ratio %.3f",
          CMTimeGetSeconds(*oldDuration), oldISO,
          CMTimeGetSeconds(*expectedDuration), *expectedISO, ratio);
}
