# Tripper Pair (optional prototype)

SwiftUI/CoreBluetooth helper for initiating the Android bridge’s BLE pairing.
It is an alternative to the one-time LightBlue step, not a media/call relay and
not needed for the current tested setup. It does not bypass iOS pairing or
notification-sharing consent.

An unsigned iPhoneOS build succeeded during development. Signing/device
installation was not completed; the owner chose to keep LightBlue. No provisioned
IPA, developer account, signing team or private certificate is included.

Open `TripperPair.xcodeproj` in Xcode, set your own unique bundle identifier and
signing team, select an iPhone and build/run. Bluetooth permission is requested on
the iPhone. This path remains unverified on a physical phone. To check compilation
without signing:

```sh
xcodebuild -project iphone-companion/TripperPair.xcodeproj -scheme TripperPair \
  -sdk iphoneos -configuration Debug CODE_SIGNING_ALLOWED=NO \
  -derivedDataPath output/ios-pairing-build build
```

`generate_project.py` recreates project/plist files using Python’s stdlib. Avoid
committing Xcode user state, provisioning profiles or signing identities.
