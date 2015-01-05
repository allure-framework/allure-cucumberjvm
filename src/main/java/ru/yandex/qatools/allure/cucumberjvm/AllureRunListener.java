package ru.yandex.qatools.allure.cucumberjvm;

import gherkin.formatter.model.Scenario;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import ru.yandex.qatools.allure.Allure;
import ru.yandex.qatools.allure.config.AllureModelUtils;
import ru.yandex.qatools.allure.events.ClearStepStorageEvent;
import ru.yandex.qatools.allure.events.TestCaseCanceledEvent;
import ru.yandex.qatools.allure.events.TestCaseFailureEvent;
import ru.yandex.qatools.allure.events.TestCaseFinishedEvent;
import ru.yandex.qatools.allure.events.TestCasePendingEvent;
import ru.yandex.qatools.allure.events.TestCaseStartedEvent;
import ru.yandex.qatools.allure.events.TestSuiteFinishedEvent;
import ru.yandex.qatools.allure.events.TestSuiteStartedEvent;
import ru.yandex.qatools.allure.utils.AnnotationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

/**
 * @author Viktor Sidochenko viktor.sidochenko@gmail.com
 */
public class AllureRunListener extends RunListener {

    private Allure lifecycle = Allure.LIFECYCLE;

    private final Map<String, String> suites = new HashMap<>();

    private Description parentDescription;

    @Override
    public void testRunStarted(Description description) {
        if (description == null) {
            // If you don't pass junit provider - surefire (<= 2.17) pass null instead of description
            return;
        }

        parentDescription = description;

    }

    String[] findFeatureByScenarioName(String scenarioName) throws IllegalAccessException {
        ArrayList<Description> features = parentDescription.getChildren().get(0).getChildren();
        //Перебор фич
        for (Description feature : features) {
            //Перебор сценариев
            for (Description story : feature.getChildren()) {
                Object scenarioType = FieldUtils.readField(story, "fUniqueId", true);

                //Работа со сценарием
                if (scenarioType instanceof Scenario) {
                    if (story.getDisplayName().equals(scenarioName)) {
                        return new String[]{feature.getDisplayName(), scenarioName};
                    }

                    // Работа с outline
                } else {
                    ArrayList<Description> examples = story.getChildren().get(0).getChildren();
                    // we need to go deeper :)
                    for (Description example : examples) {
                        if (example.getDisplayName().equals(scenarioName)) {
                            return new String[]{feature.getDisplayName(), story.getDisplayName()};
                        }
                    }
                }
            }
        }
        //TODO: изменить тип на более приемлимый
        return new String[]{"Undefined Feature", scenarioName};
    }

    public void testSuiteStarted(Description description) throws IllegalAccessException {
        String uid = generateSuiteUid(description.getClassName());

        TestSuiteStartedEvent event = new TestSuiteStartedEvent(uid, description.getClassName());

        String[] annotationParams = findFeatureByScenarioName(description.getDisplayName());

        Features feature = getFeaturesAnnotation(new String[]{annotationParams[0].split(":")[1].trim()});
        Stories story = getStoriesAnnotation(new String[]{annotationParams[1].split(":")[1].trim()});

        if (description.getDisplayName().startsWith("|")
                || description.getDisplayName().endsWith("|")) {
            story = getStoriesAnnotation(new String[]{annotationParams[1].split(":")[1].trim()
                + " " + description.getDisplayName()});
        }
        
        //Add feature and story annotations
        Collection<Annotation> annotations = new ArrayList<>();
        for (Annotation annotation:description.getAnnotations()) {
            annotations.add(annotation);
        }
        annotations.add(story);
        annotations.add(feature);
        AnnotationManager am = new AnnotationManager(annotations);
        am.update(event);

        event.withLabels(AllureModelUtils.createTestFrameworkLabel("JUnit"));

        getLifecycle().fire(event);
    }

    Stories getStoriesAnnotation(final String[] value) {
        Stories stories = new Stories() {

            @Override
            public String[] value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Stories.class;
            }
        };
        return stories;
    }

    Features getFeaturesAnnotation(final String[] value) {
        Features features = new Features() {

            @Override
            public String[] value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Features.class;
            }
        };
        return features;
    }

    @Override
    public void testStarted(Description description) throws IllegalAccessException {

        if (description.isTest()) {
            TestCaseStartedEvent event = new TestCaseStartedEvent(getSuiteUid(description), description.getMethodName());
            AnnotationManager am = new AnnotationManager(description.getAnnotations());
            am.update(event);
            fireClearStepStorage();
            getLifecycle().fire(event);
        }
    }

    @Override
    public void testFailure(Failure failure) {
        // Produces additional failure step for all test case  
        fireTestCaseFailure(failure.getException());

    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        testFailure(failure);
    }

    @Override
    public void testIgnored(Description description) throws IllegalAccessException {
        startFakeTestCase(description);
        getLifecycle().fire(new TestCasePendingEvent().withMessage(getIgnoredMessage(description)));
        finishFakeTestCase();
    }

    @Override
    public void testFinished(Description description) {
        getLifecycle().fire(new TestCaseFinishedEvent());
    }

    public void testSuiteFinished(String uid) {
        getLifecycle().fire(new TestSuiteFinishedEvent(uid));
    }

    @Override
    public void testRunFinished(Result result) {
        for (String uid : getSuites().values()) {
            testSuiteFinished(uid);
        }
    }

    public String generateSuiteUid(String suiteName) {
        String uid = UUID.randomUUID().toString();
        synchronized (getSuites()) {
            getSuites().put(suiteName, uid);
        }
        return uid;
    }

    public String getSuiteUid(Description description) throws IllegalAccessException {
        String suiteName = description.getClassName();
        if (!getSuites().containsKey(suiteName)) {
            //Fix NPE
            Description suiteDescription = Description.createSuiteDescription(suiteName);
            testSuiteStarted(suiteDescription);
        }
        return getSuites().get(suiteName);
    }

    public String
            getIgnoredMessage(Description description) {
        Ignore ignore = description.getAnnotation(Ignore.class
        );
        return ignore
                == null || ignore.value()
                .isEmpty() ? "Test ignored (without reason)!" : ignore.value();
    }

    public void startFakeTestCase(Description description) throws IllegalAccessException {
        String uid = getSuiteUid(description);

        String name = description.isTest() ? description.getMethodName() : description.getClassName();
        TestCaseStartedEvent event = new TestCaseStartedEvent(uid, name);
        AnnotationManager am = new AnnotationManager(description.getAnnotations());
        am.update(event);

        fireClearStepStorage();
        getLifecycle().fire(event);
    }

    public void finishFakeTestCase() {
        getLifecycle().fire(new TestCaseFinishedEvent());
    }

    public void fireTestCaseFailure(Throwable throwable) {
        if (throwable instanceof AssumptionViolatedException) {
            getLifecycle().fire(new TestCaseCanceledEvent().withThrowable(throwable));
        } else {
            getLifecycle().fire(new TestCaseFailureEvent().withThrowable(throwable));
        }
    }

    public void fireClearStepStorage() {
        getLifecycle().fire(new ClearStepStorageEvent());
    }

    public Allure getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Allure lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Map<String, String> getSuites() {
        return suites;
    }
}
