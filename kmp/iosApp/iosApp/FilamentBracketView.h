#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// UIView subclass that renders a 3D tournament bracket using Google Filament with Metal backend.
@interface FilamentBracketUIView : UIView

- (void)startRendering;
- (void)stopRendering;

@end

NS_ASSUME_NONNULL_END
