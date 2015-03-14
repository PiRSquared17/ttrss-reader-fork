# Introduction #

With Version 0.8 I introduced a new Feature, the ImageCache. This Cache can be configured in the Options and can hold Images from Articles and their attachments. When an Article is shown the URLs of images will be automatically replaced with the local files if available.

This allows for pre-downloading of images while beeing on WIFI.


# Details #

First run of the download method can take a long time, for me it was about 5 to 10 Minutes for 1700 Articles. Afterwards it should run faster, for example it runs about 30 seconds if there are no new files to download here.

The progress indicator in the upper left corner will continue to indicate progress until the caching is finished and a notification with information about the cache-size and time of the caching will appear.

The maximum size of the cache should be set to about 100 MB (set by default) or more. I set-up the cache so it will store images from the last 14 days by default but only the date of last modifying access to the cached image is considered, not the date of the actual article.

Max image size: 6 MB (currently hardcoded, please contact me if you feel this limit is too low)