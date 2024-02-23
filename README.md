## Android video recorder with time & locale for ID tags

---

This is an Android app that records detected ID tags as video clips with time & local info.  Such ID tags generally involve the using of RFID, QR code, or other tag tracking technology.  Overlaying of the time and locale info in the video recording is achieved by using Android [CameraX](https://developer.android.com/media/camera/camerax), [TextClock](https://developer.android.com/reference/android/widget/TextClock), Location/[Geocoder](https://developer.android.com/reference/android/location/Geocoder), and integrating with a lightweight screen recording library, [HBRecorder](https://github.com/HBiSoft/HBRecorder).

Depending on the kind of tag tracking technology being targeted, specific reader hardware and SDK might be needed.  For generality, this app only includes brief commented pseudo code for the hardware dependent ID tag SDK and simulates ID tag detections by means of an on-screen button.  Users who would like to incorporate specific ID tag reader functionality (e.g. a RFID reader with Android SDK) may substitute the simulation related code and pseudo code comments with method calls from the associated reader SDK installed as an Android module.

In case barcode/QR code is used as the ID tags, there is no need for reader hardware.  Software alone (e.g. Google's CameraX [ML Kit](https://developer.android.com/media/camera/camerax/mlkitanalyzer) API) would suffice.  

For more details, please visit this [Genuine Blog](https://blog.genuine.com/2024/02/android-video-recorder-for-id-tags/).

---
