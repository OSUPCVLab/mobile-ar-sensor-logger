
/*
 Copyright (C) 2016 Apple Inc. All Rights Reserved.
 See LICENSE.txt for this sample’s licensing information
 
 Abstract:
 The OpenGL ES view
 */

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <CoreVideo/CoreVideo.h>

@interface OpenGLPixelBufferView : UIView

- (void)displayPixelBuffer:(CVPixelBufferRef)pixelBuffer;
- (void)flushPixelBufferCache;
- (void)reset;

@end

#if !defined(_STRINGIFY)
#define __STRINGIFY( _x )   # _x
#define _STRINGIFY( _x )   __STRINGIFY( _x )
#endif

static const char * kPassThruVertex = _STRINGIFY(
                                                 
                                                 attribute vec4 position;
                                                 attribute mediump vec4 texturecoordinate;
                                                 varying mediump vec2 coordinate;
                                                 
                                                 void main()
{
    gl_Position = position;
    coordinate = texturecoordinate.xy;
}
                                                 
                                                 );

static const char * kPassThruFragment = _STRINGIFY(
                                                   
                                                   varying highp vec2 coordinate;
                                                   uniform sampler2D videoframe;
                                                   
                                                   void main()
{
    gl_FragColor = texture2D(videoframe, coordinate);
}
                                                   
                                                   );

enum {
    ATTRIB_VERTEX,
    ATTRIB_TEXTUREPOSITON,
    NUM_ATTRIBUTES
};
