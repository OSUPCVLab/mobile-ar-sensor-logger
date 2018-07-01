
#import "InertialRecorder.h"

#import <CoreMotion/CoreMotion.h>

const double GRAVITY = 9.80; // cf. https://developer.apple.com/documentation/coremotion/getting_raw_accelerometer_events
const double RATE = 100; // fps for inertial data
@interface InertialRecorder ()
{
    
}
@property CMMotionManager* motionManager;
@property NSOperationQueue* queue;
@property NSTimer* timer;

@property NSMutableArray* rawAccelGyroData;

@property BOOL interpolateAccel; // interpolate accelerometer data for gyro timestamps
@property NSString *timeStartImu;

@end

@implementation InertialRecorder

- (instancetype)init {
    self = [super init];
    if ( self )
    {
        _isRecording = false;
        _motionManager = [[CMMotionManager alloc] init];
        if (!_motionManager.isDeviceMotionAvailable) {
            NSLog(@"Device does not support motion capture."); }
        _filePath = nil;
        _interpolateAccel = TRUE;

    }
    return self;
}

- (NSMutableArray *) removeDuplicates:(NSArray *)array {
    // cf. https://stackoverflow.com/questions/1025674/the-best-way-to-remove-duplicate-values-from-nsmutablearray-in-objective-c
    NSMutableArray *mutableArray = [array mutableCopy];
    NSInteger index = [array count] - 1;
    for (id object in [array reverseObjectEnumerator]) {
        if ([mutableArray indexOfObject:object inRange:NSMakeRange(0, index)] != NSNotFound) {
            [mutableArray removeObjectAtIndex:index];
        }
        index--;
    }
    return mutableArray;
}

- (NSMutableString*)interpolate:(NSMutableArray*) accelGyroData startTime:(NSString *) startTime {
    
    NSMutableArray *gyroArray = [[NSMutableArray alloc] init];
    NSMutableArray *accelArray = [[NSMutableArray alloc] init];
    
    for (int i=0;i<[accelGyroData count];i++) {
        NodeWrapper * nw =[accelGyroData objectAtIndex:i];
        if (nw.time <= 0)
            continue;
        if (nw.isGyro)
            [gyroArray addObject:nw];
        else
            [accelArray addObject:nw];
    }
    
    // sort
    NSArray *sortedArrayGyro = [gyroArray sortedArrayUsingSelector:@selector(compare:)];
    NSArray *sortedArrayAccel = [accelArray sortedArrayUsingSelector:@selector(compare:)];
    
    // remove duplicates
    NSMutableArray *mutableGyroCopy = [self removeDuplicates:sortedArrayGyro];
    NSMutableArray *mutableAccelCopy = [self removeDuplicates:sortedArrayAccel];
    
    // interpolate
    NSMutableString * mainString = [[NSMutableString alloc]initWithString:@""];
    
    int accelIndex = 0;
    [mainString appendFormat:@"#Recording starts at %@\n", startTime];
    [mainString appendFormat:@"Timestamp[sec], gx[rad/s], gy[rad/s], gz[rad/s], ax[m/s^2], ay[m/s^2], az[m/s^2]\n"];
    // though mutableGyroCopy and mutableAccelCopy are mutable, they remains constant.
    for (int gyroIndex = 0; gyroIndex < [mutableGyroCopy count]; ++gyroIndex) {
        NodeWrapper * nwg = [mutableGyroCopy objectAtIndex:gyroIndex];
        NodeWrapper * nwa = [mutableAccelCopy objectAtIndex:accelIndex];
        if (nwg.time < nwa.time) {
            continue;
        } else if (nwg.time == nwa.time) {
            [mainString appendFormat:@"%.7f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f\n", nwg.time, nwg.x, nwg.y, nwg.z, nwa.x, nwa.y, nwa.z];
        } else {
            int lowerIndex = accelIndex;
            int upperIndex = accelIndex + 1;
            for (int iterIndex = accelIndex + 1; iterIndex < [mutableAccelCopy count]; ++iterIndex) {
                NodeWrapper * nwa1 = [mutableAccelCopy objectAtIndex:iterIndex];
                if (nwa1.time < nwg.time) {
                    lowerIndex = iterIndex;
                } else if (nwa1.time > nwg.time) {
                    upperIndex = iterIndex;
                    break;
                } else {
                    lowerIndex = iterIndex;
                    upperIndex = iterIndex;
                    break;
                }
            }
            
            if (upperIndex >= [mutableAccelCopy count])
                break;
            
            if (upperIndex == lowerIndex) {
                NodeWrapper * nwa1 = [mutableAccelCopy objectAtIndex:upperIndex];
                [mainString appendFormat:@"%.7f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f\n", nwg.time, nwg.x, nwg.y, nwg.z, nwa1.x, nwa1.y, nwa1.z];
            } else if (upperIndex == lowerIndex + 1) {
                NodeWrapper * nwa = [mutableAccelCopy objectAtIndex:lowerIndex];
                NodeWrapper * nwa1 = [mutableAccelCopy objectAtIndex:upperIndex];
                double ratio = (nwg.time - nwa.time) / (nwa1.time - nwa.time);
                double interpax = nwa.x + (nwa1.x - nwa.x) * ratio;
                double interpay = nwa.y + (nwa1.y - nwa.y) * ratio;
                double interpaz = nwa.z + (nwa1.z - nwa.z) * ratio;
                
                [mainString appendFormat:@"%.7f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f\n", nwg.time, nwg.x, nwg.y, nwg.z, interpax, interpay, interpaz];
            } else {
                NSLog(@"Impossible lower and upper bound %d %d for gyro timestamp %.5f", lowerIndex, upperIndex, nwg.time);
            }
            accelIndex = lowerIndex;
        }
    }
    if ([gyroArray count])
        [gyroArray removeAllObjects];
    if ([accelArray count])
        [accelArray removeAllObjects];
    return mainString;
}

- (void)switchRecording {
    
    if (_isRecording) {
        _isRecording = false;
        [_motionManager stopGyroUpdates];
        [_motionManager stopAccelerometerUpdates];
        
        NSMutableString * mainString = [[NSMutableString alloc]initWithString:@""];
        if (!_interpolateAccel) { // No interpolation
            [mainString appendFormat:@"#Recording starts at %@\n", _timeStartImu];
            [mainString appendFormat:@"Timestamp[sec], x, y, z[(a:m/s^2)/(g:rad/s)], isGyro?\n"];
            for(int i=0;i<[_rawAccelGyroData count];i++ ) {
                NodeWrapper * nw =[_rawAccelGyroData objectAtIndex:i];
                [mainString appendFormat:@"%.7f, %.5f, %.5f, %.5f, %d\n", nw.time, nw.x, nw.y, nw.z, nw.isGyro];
            }
        } else { // linearly interpolate acceleration offline, for online interpolation cf. vins mobile
            mainString = [self interpolate:_rawAccelGyroData startTime:_timeStartImu];
        }
        if ([_rawAccelGyroData count])
            [_rawAccelGyroData removeAllObjects];
        
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,  NSUserDomainMask, YES);
        NSString *documentsDirectoryPath = [paths objectAtIndex:0];
        
        NSString *filename = [NSString stringWithFormat:@"raw_accel_gyro.csv"];
        _filePath = [documentsDirectoryPath  stringByAppendingPathComponent:filename];
        //        NSLog(@"Data will be saved to full path %@ of filename %@", _filePath, filename);
        NSData* settingsData;
        settingsData = [mainString dataUsingEncoding: NSUTF8StringEncoding allowLossyConversion:false];
        
        if ([settingsData writeToFile:_filePath atomically:YES]) {
            NSLog(@"Written inertial data to %@", _filePath);
        }
        else {
            NSLog(@"Failed to record inertial data at %@", _filePath);
        }
        
        NSLog(@"Stopped recording inertial data!");
    } else {
        _isRecording = true;
        NSLog(@"Start recording inertial data!");
        _rawAccelGyroData = [[NSMutableArray alloc] init];
        // reference: Basic sensors in ios Objective c
        // reference: https://stackoverflow.com/questions/37908854/motion-manager-not-working swift
        _motionManager.gyroUpdateInterval = 1.0/RATE;
        _motionManager.accelerometerUpdateInterval = 1.0/RATE;
        
        NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
        [dateFormatter setDateFormat:@"EEE_MM_dd_yyyy_HH_mm_ss"];
        _timeStartImu = [dateFormatter stringFromDate:[NSDate date]];
        
        if (_motionManager.gyroAvailable && _motionManager.accelerometerAvailable) {
            _queue = [NSOperationQueue currentQueue];
            [_motionManager startGyroUpdatesToQueue:_queue withHandler: ^ (CMGyroData *gyroData, NSError *error) {
                CMRotationRate rotate = gyroData.rotationRate;
                
                NodeWrapper* nw = [[NodeWrapper alloc] init];
                nw.isGyro = true;
                nw.time = gyroData.timestamp;
                nw.x = rotate.x;
                nw.y = rotate.y;
                nw.z = rotate.z;
                [self->_rawAccelGyroData addObject:nw];
            }];
            [_motionManager startAccelerometerUpdatesToQueue:_queue withHandler: ^ (CMAccelerometerData *accelData, NSError *error) {
                CMAcceleration accel = accelData.acceleration;
                
                NodeWrapper* nw = [[NodeWrapper alloc] init];
                nw.isGyro = false;
                //The time stamp is the amount of time in seconds since the device booted.
                nw.time = accelData.timestamp;
                nw.x = - accel.x * GRAVITY;
                nw.y = - accel.y * GRAVITY;
                nw.z = - accel.z * GRAVITY;
                
                [self->_rawAccelGyroData addObject:nw];
            }];
        } else {
            NSLog(@"Gyroscope or accelerometer not available");
        }
    }
}

@end


@implementation NodeWrapper
- (NSComparisonResult)compare:(NodeWrapper *)otherObject {
    return [@(self.time) compare:@(otherObject.time)]; // @ converts double to NSNumber
}
@end
