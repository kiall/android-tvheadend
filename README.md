# Android TV + TVHeadend Live Channel

* Master [![Build Status](https://jenkins.macinnes.ie/buildStatus/icon?job=android-tvheadend/master)](https://jenkins.macinnes.ie/job/android-tvheadend/job/master/)
* Develop [![Build Status](https://jenkins.macinnes.ie/buildStatus/icon?job=android-tvheadend/develop)](https://jenkins.macinnes.ie/job/android-tvheadend/job/develop/)

Available on Google Play:

1. Download from Google Play: https://play.google.com/store/apps/details?id=ie.macinnes.tvheadend

Sign up for Beta access (more frequent, bleeding edge releases):

1. Join the beta community (required!) https://plus.google.com/communities/102705346691784371187
2. Join the beta on Google Play: https://play.google.com/apps/testing/ie.macinnes.tvheadend
3. Download from Google Play: https://play.google.com/store/apps/details?id=ie.macinnes.tvheadend

IRC Channel:

Join #android-tvheadend on Freenode - not a general support channel, used for chatting with developers around specific bugs etc

![Guide](app/src/main/play/en-GB/listing/tvScreenshots/01-guide.png)
![Playback Overlay](app/src/main/play/en-GB/listing/tvScreenshots/02-playback-overlay.png)
![Guide](app/src/main/play/en-GB/listing/tvScreenshots/03-guide.png)
![Playback](app/src/main/play/en-GB/listing/tvScreenshots/04-playback.png)
![Genres](app/src/main/play/en-GB/listing/tvScreenshots/05-genres.png)

# Build Properties

Build customization can be performed via a `local-tvheadend.properties` file, for example:

	ie.macinnes.tvheadend.acraReportUri=https://crashreport.com/report/tvheadend
	ie.macinnes.tvheadend.keystoreFile=keystore.jks
	ie.macinnes.tvheadend.keystorePassword=MySecretPassword
	ie.macinnes.tvheadend.keyAlias=My TVHeadend Key
	ie.macinnes.tvheadend.keyPassword=MySecretPassword
