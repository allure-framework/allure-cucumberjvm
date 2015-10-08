package ru.yandex.qatools.allure.cucumberjvm;

import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
import ru.yandex.qatools.allure.annotations.Title;
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
     * <p>
     * Find features level<p>
     * JUnit`s test {@link Description} is multilevel object with liquid
     * hierarchy.<br>
     * This method recursively query
     * {@link #getTestEntityType(org.junit.runner.Description)} method until it
     * matches {@link Feature} type and when returns list of {@link Feature}
     * descriptions
     *
     * @param description {@link Description} Description to start search where
     * @return {@link List<Description>} features description list
     * @throws IllegalAccessException
     */
    private List<Description> findFeaturesLevel(List<Description> description)
            throws IllegalAccessException {
        if (description.isEmpty()) {
            return new ArrayList<>();
        }
        Object entityType = getTestEntityType(description.get(0));
        if (entityType instanceof Feature) {
            return description;
        } else {
            return findFeaturesLevel(description.get(0).getChildren());
        }

    }

    /**
     * Get Description unique object
     *
     * @param description See {@link Description}
     * @return {@link Object} what represents by uniqueId on {@link Description}
     * creation as an arbitrary object used to define its type.<br>
     * It can be instance of {@link String}, {@link Feature}, {@link Scenario}
     * or {@link ScenarioOutline}.<br>
     * In case of {@link String} object it could be Suite, TestClass or an
     * empty, regardless to level of {@link #parentDescription}
     * @throws IllegalAccessException
     */
    private Object getTestEntityType(Description description) throws IllegalAccessException {
        return FieldUtils.readField(description, "fUniqueId", true);
    }

    /**
     * <p>
     * Find Test classes level<p>
     * JUnit`s test {@link Description} is multilevel object with liquid
     * hierarchy.<br>
     * This method recursively query
     * {@link #getTestEntityType(org.junit.runner.Description)} method until it
     * matches {@link Feature} type and when returns parent of this object as
     * list of test classes descriptions
     *
     *
     * @param description {@link Description} Description to start search where
     * @return {@link List<Description>} test classes description list
     * @throws IllegalAccessException
     */
    public List<Description> findTestClassesLevel(List<Description> description) throws IllegalAccessException {
        if (description.isEmpty()) {
            return new ArrayList<>();
        }
        Object possibleClass = getTestEntityType(description.get(0));
        if (possibleClass instanceof String && !((String) possibleClass).isEmpty()) {
            if (!description.get(0).getChildren().isEmpty()) {
                Object possibleFeature = getTestEntityType(description.get(0).getChildren().get(0));
                if (possibleFeature instanceof Feature) {
                    return description;
                } else {
                    return findTestClassesLevel(description.get(0).getChildren());
                }
            } else {
                //No scenarios in feature
                return description;
            }

        } else {
            return findTestClassesLevel(description.get(0).getChildren());
        }

    }

    /**
     * Find feature and story for given scenario
     *
     * @param scenarioName
     * @return {@link String[]} of ["<FEATURE_NAME>", "<STORY_NAME>"]s
     * @throws IllegalAccessException
     */
    private String[] findFeatureByScenarioName(String scenarioName) throws IllegalAccessException {
        List<Description> testClasses = findTestClassesLevel(parentDescription.getChildren());

        for (Description testClass : testClasses) {

            List<Description> features = findFeaturesLevel(testClass.getChildren());
            //Feature cycle
            for (Description feature : features) {
                //Story cycle
                for (Description story : feature.getChildren()) {
                    Object scenarioType = getTestEntityType(story);

                    //Scenario
                    if (scenarioType instanceof Scenario
                            && story.getDisplayName().equals(scenarioName)) {
                        return new String[]{feature.getDisplayName(), scenarioName};

                        //Scenario Outline
                    } else if (scenarioType instanceof ScenarioOutline) {
                        List<Description> examples = story.getChildren().get(0).getChildren();
                        // we need to go deeper :)
                        for (Description example : examples) {
                            if (example.getDisplayName().equals(scenarioName)) {
                                return new String[]{feature.getDisplayName(), story.getDisplayName()};
                            }
                        }
                    }
                }
            }
        }
        return new String[]{"Feature: Undefined Feature", scenarioName};
    }
    
    public void testSuiteStarted(Description description, String suiteName) throws IllegalAccessException {

        String[] annotationParams = findFeatureByScenarioName(suiteName);
        
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

        Title title = getTitleAnnotation(story.value()[0]);
        
        //Add feature and story annotations
        Collection<Annotation> annotations = new ArrayList<>();
        for (Annotation annotation : description.getAnnotations()) {
            annotations.add(annotation);
        }
        annotations.add(title);
        annotations.add(story);
        annotations.add(feature);
        AnnotationManager am = new AnnotationManager(annotations);
        am.update(event);

        event.withLabels(AllureModelUtils.createTestFrameworkLabel("CucumberJVM"));

        getLifecycle().fire(event);
    }

    /**
     * Creates Story annotation object
     *
     * @param value story names array
     * @return Story annotation object
     */
    Stories getStoriesAnnotation(final String[] value) {
        return new Stories() {

            @Override
            public String[] value() {
                return value;
            }

            @Override
            public Class<Stories> annotationType() {
                return Stories.class;
            }
        };
    }

    /**
     * Creates Title annotation object
     * 
     * @param value title sting
     * @return Title annotation object
     */
    Title getTitleAnnotation(final String value) {
    	return new Title() {
			
			@Override
			public Class<Title> annotationType() {
				return Title.class;
			}
			
			@Override
			public String value() {
				return value;
			}
		};
    }
    
    /**
     * Creates Feature annotation object
     *
     * @param value feature names array
     * @return Feature annotation object
     */
    Features getFeaturesAnnotation(final String[] value) {
        return new Features() {

            @Override
            public String[] value() {
                return value;
            }

            @Override
            public Class<Features> annotationType() {
                return Features.class;
            }
        };
    }

    @Override
    public void testStarted(Description description) throws IllegalAccessException {

        if (description.isTest()) {
            String methodName = extractMethodName(description);
            TestCaseStartedEvent event = new TestCaseStartedEvent(getSuiteUid(description), methodName);
            
            Collection<Annotation> annotations = new ArrayList<>();
            for (Annotation annotation : description.getAnnotations()) {
                annotations.add(annotation);
            }
            
            Title title = getTitleAnnotation(description.getDisplayName());
            annotations.add(title);
            
            AnnotationManager am = new AnnotationManager(annotations);
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
