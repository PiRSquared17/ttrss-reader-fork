# Supported Devices #
Any Android-Devices running on API-Level >= 8 (Froyo, Android 2.2) should work.

# Language and Translations #
Currently the app is available in english, german and french but the german and french translations are not entirely up-to-date. If you want to contribute and speak a language not fully covered yet you may want to take a look at the default texts (english): [strings.xml](http://code.google.com/p/ttrss-reader-fork/source/browse/ttrss-reader/res/values/strings.xml)

# Environment #

## Android Studio ##
Check out the project from the maven repository with Android Studio. All the dependencies should be resolved automatically.

If you want to build and sign the application with a special keystore you can define your own credentials in the file signingconfig.gradle in the project-root. There is an example in signingconfig.gradle.example which uses keystore-files located one folder above the project-root.

## Eclipse (deprecated) ##
To setup my development environment I use the following tools and projects:
  * Eclipse
  * Apache Ant (optional)
  * Mercurial
  * Project: Locale-API (http://www.twofortyfouram.com/developer.html)
  * Project: ActionBarSherlock (http://github.com/JakeWharton/ActionBarSherlock/)

You need to set the "Android Private Libraries" to be exported for the library projects since Google pushed a new version of the eclipse-plugin if you want to build and deploy from eclipse. To do so for every library-project right-click and select "preferences", then "Java Build Path" and the register "Order and Export". There you need to check "Android Private Libraries".

The projects need to be checked out alongside the content of our repository:
> hg clone https://code.google.com/p/ttrss-reader-fork/

If they are not located in the same folder you need to edit the project.properties accordingly. If you want to use Ant you may want to create the files ant.properties ("key.alias", "key.alias.password", ...) and local.properties ("sdk.dir=...").

# Development #
If you want to contribute code...
  * ...it should be formatted (settings for EclipseFormatter are included in the project).
  * ...it should contain at least some comments explaining what you are doing if it non-trivial.
  * ...you can do this easily by [cloning](http://code.google.com/p/ttrss-reader-fork/source/checkout) the repository and sending me a link to the revision containing your changes.

# Building from commandline #

To build the code from the command line the following steps are necessary:

  * install the [ADK](http://developer.android.com/sdk/installing/bundle.html) (you might have to configure targets too ... the android site explains things)
  * set up a tree for holding all the projects:
```
mkdir build
cd build
```
  * check out this project:
```
hg clone https://code.google.com/p/ttrss-reader-fork/
```
  * download the [Locale-API](http://www.twofortyfouram.com/developer/display.zip) project:
```
wget http://www.twofortyfouram.com/developer/display.zip
unzip display
mv display/locale-api ./
rm -rf display*
```
  * check out the [ActionBarSherlock](http://github.com/JakeWharton/ActionBarSherlock/) project:
```
git clone git://github.com/JakeWharton/ActionBarSherlock.git
```
  * build the Locale-API project:
```
cd locale-api
android update lib-project -p . --target 1
ant debug
cd ..
```
  * build the ActionBarSherlock project:
```
cd ActionBarSherlock
ln -sfT actionbarsherlock library
cd actionbarsherlock
android update lib-project -p . --target 1
ant debug
cd ../..
```
  * finally build this project:
```
cd ttrss-reader-fork/ttrss-reader
android update project -p . --target 1
ant debug
```

This will produce an .apk in the ttrss-reader-fork/ttrss-reader/bin/  which you can install on your device. This is the debug build, so if you want the release (aka fast) build, go into each of the three dirs as above but run ant release this time instead of ant debug.

(Thanks to vapier for the description!)


**To be continued...**