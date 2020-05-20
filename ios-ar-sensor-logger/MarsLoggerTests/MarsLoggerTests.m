//
//  MarsLoggerTests.m
//  MarsLoggerTests
//
//  Created by zxc on 2019/12/13.
//

#import <XCTest/XCTest.h>
#import "VideoTimeConverter.h"

@interface MarsLoggerTests : XCTestCase

@end

@implementation MarsLoggerTests

- (void)setUp {
    // Put setup code here. This method is called before the invocation of each test method in the class.
}

- (void)tearDown {
    // Put teardown code here. This method is called after the invocation of each test method in the class.
}

- (void)testsecDoubleToNanoString {
    // This is an example of a functional test case.
    // Use XCTAssert and related functions to verify your tests produce the correct results.
    double time1 = 8523.974328432;
    NSString *time1s = @"8523974328432";
    NSString *res1 = secDoubleToNanoString(time1);
    NSString *warn1 = [NSString stringWithFormat:@"expected %@ return %@", time1s, res1];
    XCTAssertTrue([time1s isEqualToString:res1], @"%@", warn1);

    double time2 = 8523.004328432;
    NSString *time2s = @"8523004328432";
    NSString *res2 = secDoubleToNanoString(time2);
    NSString *warn2 = [NSString stringWithFormat:@"expected %@ return %@", time2s, res2];
    XCTAssertTrue([time2s isEqualToString:res2], @"%@", warn2);
}

- (void)testPerformanceExample {
    // This is an example of a performance test case.
    [self measureBlock:^{
        // Put the code you want to measure the time of here.
    }];
}

@end
