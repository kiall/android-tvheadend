Android TV + Tvheadend Known Issues
===================================

You MUST have a Stream Profile called "tif"
-------------------------------------------

Tvheadend MUST be configured with a Stream Profile with a name of "tif". I use the following
settings:

* Profile name: tif
* Resolution (height): 0
* Video codec: libvpx / VP8
* Video bitrate: 0
* Audio codec: libvorbis
* Audio bitrate: 0
* Subtitle codec: Do not use

You MAY need a Audio Stream Filter to drop Audio Description Tracks
-------------------------------------------------------------------

Some providers send Audio Description tracks as the first track, which we'll select. By adding
a Audio Stream Filter, you can have Tvheadend ignore these tracks.
