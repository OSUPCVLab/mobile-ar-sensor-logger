
#import "CameraControlFunctions.h"
#import <Photos/Photos.h>

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

NSString *getAssetPath(NSString * assetLocalIdentifier) {
    // see: https://stackoverflow.com/questions/27854937/ios8-photos-framework-how-to-get-the-nameor-filename-of-a-phasset
    //    PHFetchOptions *fetchOptions = [[PHFetchOptions alloc] init];
    //    fetchOptions.sortDescriptors = @[[NSSortDescriptor sortDescriptorWithKey:@"creationDate" ascending:YES]];
    PHFetchResult *fetchResult = [PHAsset fetchAssetsWithLocalIdentifiers:@[assetLocalIdentifier] options:nil];
    PHAsset *asset = nil;
    if (fetchResult != nil && fetchResult.count > 0) {
        // get last photo from Photos
        asset = [fetchResult lastObject];
    }
    __block NSString *path = nil;
    if (asset) {
        // get photo info from this asset
        // for iOS 8
        PHImageRequestOptions * imageRequestOptions = [[PHImageRequestOptions alloc] init];
        imageRequestOptions.synchronous = YES;
        // Warn: Because by default, requestImageDataForAsset method executes asynchronously, the following way to pass out path will not work.
        [[PHImageManager defaultManager]
         requestImageDataForAsset:asset
         options:imageRequestOptions
         resultHandler:^(NSData *imageData, NSString *dataUTI,
                         UIImageOrientation orientation,
                         NSDictionary *info)
         {
            NSURL * fileURL = [info objectForKey:@"PHImageFileURLKey"];
            if (fileURL) {
                // path looks like this -
                // file:///var/mobile/Media/DCIM/###APPLE/IMG_####.JPG
                path = [[NSFileManager defaultManager] displayNameAtPath:[fileURL path]];
                NSLog(@"PHImageFile path %@", path);
            }
        }];
        // for iOS 9+
        // https://stackoverflow.com/questions/32687403/phasset-get-original-file-name/32706194
        // this path looks like "Movie.MP4"
        NSArray *resources = [PHAssetResource assetResourcesForAsset:asset];
        path = ((PHAssetResource*)resources[0]).originalFilename;
    }
    return path;
}
