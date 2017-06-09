Flickr Uploader
===============
This is a fork off of <https://github.com/rafali/flickr-uploader>. The
difference is mostly that this project comes with build instructions and that
a number of crashes have been fixed. Yes, I asked @rafali before doing this.

The goal of this project is to help Flickr users automatically backup the photos from their Android phone.
It works like [Google+ Instant Upload](http://support.google.com/plus/answer/2910392?hl=en) or [Dropbox Camera Upload](https://blog.dropbox.com/2012/02/your-photos-simplified-part-1/).

The main benefit of this app over Google+ Instant Upload is the fact that you can upload ONE FREAKING TERABYTE of photos&videos in **high resolution** for free!

Installation
------------
You can install it from Google Play Store:

https://play.google.com/store/apps/details?id=com.gmail.walles.johan.flickruploader2

Development
===========
You can fork this project or use the source code for any project. It is licensed
under the GPL v2. Just make sure to republish the source code as the GPL v2
demand.

It uses a few open source libraries:
- [flickrj-android](https://code.google.com/p/flickrj-android/) : a modified version of the old java flickr lib optimized for Android and Google App Engine
- [Android-BitmapCache](https://github.com/chrisbanes/Android-BitmapCache) : takes care of bitmaps caching/recycling for you
- [Android-ViewPagerIndicator](https://github.com/JakeWharton/Android-ViewPagerIndicator) : displays a nice tab UI above a ViewPager
- [AndroidAnnotation](https://github.com/excilys/androidannotations) : simplifies Android development with annotations to define code scope (UI thread, background thread…)
- [google-collections](https://code.google.com/p/google-collections/) : simplifies use of lists, maps and multimaps
- [Sprinkles](https://github.com/emilsjolander/sprinkles) : simplifies the use of SQLite database on Android


Follow me on Twitter: https://twitter.com/rafali

Building
--------
1. [Get Flickr API credentials](https://www.flickr.com/services/apps/create/apply)
1. Create `FlickrUploaderAndroid/flickrUploader/src/main/res/values/flickr-api-strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="flickr_api_key">...</string>
    <string name="flickr_api_secret">...</string>
</resources>
```
1. Create `FlickrUploaderAndroid/flickrUploader/fabric.properties`
with one line: "`apiKey=0`" (or follow the [official Crashlytics
instructions](https://docs.fabric.io/android/fabric/settings/working-in-teams.html#android-projects))
1. In Android Studio, start the `flickrUploader` launch configuration.

Releasing
---------
1. Put your signing info in `~/.gradle/gradle.properties` as described at
<http://stackoverflow.com/a/21020469/473672>
1. `cd FlickrUploaderAndroid`
1. Do `git tag` and decide on what the new version number should be
1. `git tag N.X.Y` for the new version
1. `./gradlew build`
1. Upload `flickrUploader/build/outputs/apk/flickrUploader-release.apk` to
Google Play
1. `git push --tags`
