package ru.yandex.qatools.allure.cucumberjvm;

import gherkin.formatter.model.Scenario;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import ru.yandex.qatools.allure.Allure;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;
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

/**
 * @author Viktor Sidochenko viktor.sidochenko@gmail.com
 */
public class AllureRunListener extends RunListener {

    private Allure lifecycle = Allure.LIFECYCLE;

    private final Map<String, String> suites = new HashMap<>();

    /**
     * All tests object
     */
    private Description parentDescription;

    @Override
    public void testRunStarted(Description description) {
        parentDescription = description;
    }

    /**
     * Find feature and story for given scenario
     *
     * @param scenarioName
     * @return array of {"<FEATURE_NAME>", "<STORY_NAME>"}
     * @throws IllegalAccessException
     */
    String[] findFeatureByScenarioName(String scenarioName) throws IllegalAccessException {
        ArrayList<Description> features = parentDescription.getChildren().get(0).getChildren();
        //Feature cycle
        for (Description feature : features) {
            //Story cycle
            for (Description story : feature.getChildren()) {
                Object scenarioType = FieldUtils.readField(story, "fUniqueId", true);

                //Scenario
                if (scenarioType instanceof Scenario) {
                    if (story.getDisplayName().equals(scenarioName)) {
                        return new String[]{feature.getDisplayName(), scenarioName};
                    }

                    //Scenario Outline
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
        //TODO: change method return type to smth better
        return new String[]{"Undefined Feature", scenarioName};
    }

    public void testSuiteStarted(Description description, String suiteName) throws IllegalAccessException {

        String[] annotationParams = findFeatureByScenarioName(description.getDisplayName());

        //Create feature and story annotations. Remove unnecessary words from it
        Features feature = getFeaturesAnnotation(new String[]{annotationParams[0].split(":")[1].trim()});
        Stories story = getStoriesAnnotation(new String[]{annotationParams[1].split(":")[1].trim()});

        //If it`s Scenario Outline, add example string to story name
        if (description.getDisplayName().startsWith("|")
                || description.getDisplayName().endsWith("|")) {
            story = getStoriesAnnotation(new String[]{annotationParams[1].split(":")[1].trim()
                + " " + description.getDisplayName()});
        }

        String uid = generateSuiteUid(suiteName);
        TestSuiteStartedEvent event = new TestSuiteStartedEvent(uid, story.value()[0]);

        //Add feature and story annotations
        Collection<Annotation> annotations = new ArrayList<>();
        for (Annotation annotation : description.getAnnotations()) {
            annotations.add(annotation);
        }
        annotations.add(story);
        annotations.add(feature);
        AnnotationManager am = new AnnotationManager(annotations);
        am.update(event);

        event.withLabels(AllureModelUtils.createTestFrameworkLabel("JUnit"));

        getLifecycle().fire(event);
    }

    /**
     * Creates Story annotation object
     *
     * @param value story names array
     * @return Story annotation object
     */
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

    /**
     * Creates Feature annotation object
     *
     * @param value feature names array
     * @return Feature annotation object
     */
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
            String methodName = extractMethodName(description);
            TestCaseStartedEvent event = new TestCaseStartedEvent(getSuiteUid(description), methodName);
            AnnotationManager am = new AnnotationManager(description.getAnnotations());
            am.update(event);
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
    public void testFinished(Description description) throws IllegalAccessException {
        if (description.isSuite()) {
            testSuiteFinished(getSuiteUid(description));
        } else {
            getLifecycle().fire(new TestCaseFinishedEvent());
        }
    }

    public void testSuiteFinished(String uid) {
        getLifecycle().fire(new TestSuiteFinishedEvent(uid));
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
        if (!description.isSuite()) {
            suiteName = extractClassName(description);
        }
        if (!getSuites().containsKey(suiteName)) {
            //Fix NPE
            Description suiteDescription = Description.createSuiteDescription(suiteName);
            testSuiteStarted(suiteDescription, suiteName);
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

    private String extractClassName(Description description) {
        String displayName = description.getDisplayName();
        Pattern pattern = Pattern.compile("\\(\\|(.*)\\|\\)");
        Matcher matcher = pattern.matcher(displayName);
        if (matcher.find()) {
            return "|" + matcher.group(1) + "|";
        }
        return description.getClassName();
    }

    private String extractMethodName(Description description) {
        String displayName = description.getDisplayName();
        Pattern pattern = Pattern.compile("^(.*)\\(\\|");
        Matcher matcher = pattern.matcher(displayName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return description.getMethodName();
    }
}
