# gdx-gameanalytics

gameanalytics.com REST API client implementation for libGDX

Checkout gameanalytics.com for information about their service.

## Why you should use this implementation instead of official implementations?

This implementation works without changes on Android, Desktop, iOS/RoboVM and GWT. The official Android implementation needs Play
Services to work, which are not available on Amazon devices and bloat your apk file.

Besides, this implementation does not send any identifiable user data. For returning user recognition, a random UUID specific for your game is generated and used.

## Installation

This project is published to the Sonatype Maven repository. You can integrate the lib into your project by just adding the dependencies to your `build.gradle` file.

Define the version of this API right after the gdxVersion:

    gdxVersion = '1.9.6' //or another gdx version you use
    gaVersion = '1.0.0'

Then add the needed dependencies to each subproject:

Core:

    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-core:$gaVersion"
    
        
Desktop:
    
    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-desktop:$gaVersion"

Android:

    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-android:$gaVersion"

HTML:

    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-core:$gaVersion:sources"
    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-html:$gaVersion"
    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-html:$gaVersion:sources"

iOS-RoboVM:

    compile "de.golfgl.gdxgameanalytics:gdx-gameanalytics-ios:$gaVersion"


### Building from source
To build from source, clone or download this repository, then open it in Android Studio. Perform the following command to compile and upload the library in your local repository:

    gradlew clean uploadArchives -PLOCAL=true

See `build.gradle` file for current version to use in your dependencies.

## GWT project setup
If you want to use GameAnalytics with a GWT project, you need some more steps. Add the following line to your gwt.xml:

    <inherits name="de.golfgl.gdxgameanalytics.gdxgameanalytics"/>

You also need to add a crypto JS lib to your project. Add the following to your index.html within the body tags:

     <script src="sjcl.js"></script>

Of course, you also need to get a copy of the lib from https://github.com/bitwiseshiftleft/sjcl/


## Usage

Initialize GameAnalytics client in your game's create() method:

    protected void initGameAnalytics(Preferences lbPrefs) {
        gameAnalytics = new GameAnalytics();
        gameAnalytics.setPlatformVersionString("1");

        gameAnalytics.setGameBuildNumber(GAME_DEVMODE ? "debug" : String.valueOf(GAME_VERSIONNUMBER));

        gameAnalytics.setPrefs(Gdx.app.getPreferences(...));
        gameAnalytics.setGameKey(GA_APP_KEY);
        gameAnalytics.setGameSecretKey(GA_SECRET_KEY);
        gameAnalytics.startSession();
    }

Don't forget to add calls to `closeSession()` and `startSession()` in your game's `pause()` and `resume()` methods.

On Android, you can use the convinience class `AndroidGameAnalytics` which sets up platform, version and device information for you. If you want to use GA for crash reporting,
call its `registerUncaughtExceptionHandler()` method. The same applies to iOS/RoboVM with `IosGameAnalytics` class.

Submit events with the public `submit...` methods.

## News & Community

You can get help on the [libgdx discord](https://discord.gg/6pgDK9F).

# License

The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
