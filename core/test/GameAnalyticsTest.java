import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.graphics.GL20;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by Benjamin Schulte on 05.05.2018.
 */
public class GameAnalyticsTest {

    @BeforeClass
    public static void init() {
        // Note that we don't need to implement any of the listener's methods
        Gdx.app = new HeadlessApplication(new ApplicationListener() {
            @Override
            public void create() {
            }

            @Override
            public void resize(int width, int height) {
            }

            @Override
            public void render() {
            }

            @Override
            public void pause() {
            }

            @Override
            public void resume() {
            }

            @Override
            public void dispose() {
            }
        });

        // Use Mockito to mock the OpenGL methods since we are running headlessly
        Gdx.gl20 = Mockito.mock(GL20.class);
        Gdx.gl = Gdx.gl20;

        Gdx.app.setLogLevel(Application.LOG_DEBUG);
    }

    @Test
    public void testGameAnalytics() throws InterruptedException {
        GameAnalytics ga = new GameAnalytics();

        ga.setPlatform(GameAnalytics.Platform.Windows);
        ga.setPlatformVersionString("10");

        ga.initSession();

        Thread.sleep(500); // give the HTTP request some time

        //Design events
        ga.submitDesignEvent("kill:robot:blue");
        ga.submitDesignEvent("Tutorial:Step1:Finished", 100f);

        //Business event
        //CreateGenericBusinessEvent("Sales:GoodiesPackage", 99, "USD", 1);
        //Business event for Google
        //CreateGenericBusinessEventGoogle("Sales", 99, "USD", 1, "Tier 1", "+Receipt+", "+signature+");

        //Progression events
        ga.submitProgressionEvent(GameAnalytics.ProgressionStatus.Start, "World1", "", "");
        ga.submitProgressionEvent(GameAnalytics.ProgressionStatus.Fail, "World1", "Level2", "");
        ga.submitProgressionEvent(GameAnalytics.ProgressionStatus.Complete, "World2", "Level1", "Arena2", 200);

        //Resource events
        //CreateResourceEvent(GameAnalytics.ResourceFlowType.Sink, "gold", "Weapon", "Frostmourne", 10000);
        //CreateResourceEvent(GameAnalytics.ResourceFlowType.Source, "silver", "Consumable", "Mana potion", 5);

        //Error event
        //CreateErrorEvent(GameAnalytics.ErrorType.info, "The instrumentation runs smooth!");

        //Send all events in queue
        ga.flushQueue();

        //Finally close session by sending session_end event
        ga.closeSession();

        Thread.sleep(1500); // give the HTTP request some time

    }
}