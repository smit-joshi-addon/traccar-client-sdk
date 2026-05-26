#import "TraccarClientAutoInit.h"
#import <UIKit/UIKit.h>
#import <TraccarClientSDK/TraccarClientSDK.h>

@interface TraccarClientAutoInit : NSObject
@end

@implementation TraccarClientAutoInit

+ (void)load {
    [[NSNotificationCenter defaultCenter]
        addObserverForName:UIApplicationDidFinishLaunchingNotification
        object:nil
        queue:[NSOperationQueue mainQueue]
        usingBlock:^(NSNotification *note) {
            if (note.userInfo[UIApplicationLaunchOptionsLocationKey] != nil) {
                [TraccarClientSDKTracker.shared resume];
            }
        }];
}

@end
