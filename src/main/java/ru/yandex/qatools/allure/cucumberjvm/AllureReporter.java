package ru.yandex.qatools.allure.cucumberjvm;

import gherkin.formatter.model.DataTableRow;
import org.apache.commons.lang3.StringUtils;
import ru.yandex.qatools.allure.cucumberjvm.callback.OnFailureCallback;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cucumber.runtime.CucumberException;
import gherkin.I18n;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cucumber.runtime.StepDefinitionMatch;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import ru.yandex.qatools.allure.Allure;
import ru.yandex.qatools.allure.annotations.Description;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Issue;
import ru.yandex.qatools.allure.annotations.Issues;
import ru.yandex.qatools.allure.annotations.Severity;
import ru.yandex.qatools.allure.annotations.Stories;
import ru.yandex.qatools.allure.annotations.TestCaseId;
import ru.yandex.qatools.allure.config.AllureModelUtils;
import ru.yandex.qatools.allure.events.*;
import ru.yandex.qatools.allure.exceptions.AllureException;
import ru.yandex.qatools.allure.model.DescriptionType;
import ru.yandex.qatools.allure.model.SeverityLevel;
import ru.yandex.qatools.allure.utils.AnnotationManager;

/**
 * Allure reporting plugin for cucumber-jvm
 */
public class AllureReporter implements Reporter, Formatter {

    protected static Class<? extends OnFailureCallback> callback;
    protected static Object callbackResult;

    private static final List<String> SCENARIO_OUTLINE_KEYWORDS = Collections.synchronizedList(new ArrayList<String>());

    private static final String FAILED = "failed";
    private static final String SKIPPED = "skipped";
    private static final String PASSED = "passed";

    private static final Log LOG = LogFactory.getLog(AllureReporter.class);

    private static final Allure ALLURE_LIFECYCLE = Allure.LIFECYCLE;

    private static final Pattern SEVERITY_PATTERN = Pattern.compile("@SeverityLevel\\.(.+)");
    private static final Pattern ISSUE_PATTERN = Pattern.compile("@Issue\\(\"+?([^\"]+)\"+?\\)");
    private static final Pattern TEST_CASE_ID_PATTERN = Pattern.compile("@TestCaseId\\(\"+?([^\"]+)\"+?\\)");

    private Feature feature;
    private StepDefinitionMatch match;

    private final LinkedList<Step> gherkinSteps = new LinkedList<>();

    private String uid;
    private String currentStatus;

    //to avoid duplicate names of attachments and messages
    private long counter = 0;

    public AllureReporter() {
        List<I18n> i18nList = I18n.getAll();

        for (I18n i18n : i18nList) {
            SCENARIO_OUTLINE_KEYWORDS.addAll(i18n.keywords("scenario_outline"));
        }
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        //Nothing to do with Allure
    }

    @Override
    public void uri(String uri) {
        //Nothing to do with Allure
    }

    @Override
    public void feature(Feature feature) {
        this.feature = feature;

        uid = UUID.randomUUID().toString();

        TestSuiteStartedEvent event = new TestSuiteStartedEvent(uid, feature.getName());

        Collection<Annotation> annotations = new ArrayList<>();
        annotations.add(getDescriptionAnnotation(feature.getDescription()));
        annotations.add(getFeaturesAnnotation(feature.getName()));

        AnnotationManager am = new AnnotationManager(annotations);
        am.update(event);
        ALLURE_LIFECYCLE.fire(event);
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        //Nothing to do with Allure
    }

    @Override
    public void examples(Examples examples) {
        //Nothing to do with Allure
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {

        //to avoid duplicate steps in case of Scenario Outline
        if (SCENARIO_OUTLINE_KEYWORDS.contains(scenario.getKeyword())) {
            synchronized (gherkinSteps) {
                gherkinSteps.clear();
            }
        }

        currentStatus = PASSED;

        TestCaseStartedEvent event = new TestCaseStartedEvent(uid, scenario.getName());
        event.setTitle(scenario.getName());

        Collection<Annotation> annotations = new ArrayList<>();

        SeverityLevel level = getSeverityLevel(scenario);

        if (level != null) {
            annotations.add(getSeverityAnnotation(level));
        }

        Issues issues = getIssuesAnnotation(scenario);
        if (issues != null) {
            annotations.add(issues);
        }

        TestCaseId testCaseId = getTestCaseIdAnnotation(scenario);
        if (testCaseId != null) {
            annotations.add(testCaseId);
        }

        annotations.add(getFeaturesAnnotation(feature.getName()));
        annotations.add(getStoriesAnnotation(scenario.getName()));
        annotations.add(getDescriptionAnnotation(scenario.getDescription()));

        AnnotationManager am = new AnnotationManager(annotations);
        am.update(event);

        event.withLabels(AllureModelUtils.createTestFrameworkLabel("CucumberJVM"));

        ALLURE_LIFECYCLE.fire(event);
    }

    @Override
    public void background(Background background) {
        //Nothing to do with Allure
    }

    @Override
    public void scenario(Scenario scenario) {
        //Nothing to do with Allure
    }

    @Override
    public void step(Step step) {
        synchronized (gherkinSteps) {
            gherkinSteps.add(step);
        }
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        synchronized (gherkinSteps) {
            while (gherkinSteps.peek() != null) {
                fireCanceledStep(gherkinSteps.remove());
            }
        }
        ALLURE_LIFECYCLE.fire(new TestCaseFinishedEvent());
    }

    @Override
    public void done() {
        //Nothing to do with Allure
    }

    @Override
    public void close() {
        //Nothing to do with Allure
    }

    @Override
    public void eof() {
        ALLURE_LIFECYCLE.fire(new TestSuiteFinishedEvent(uid));
        uid = null;
    }

    @Override
    public void before(Match match, Result result) {
        //Nothing to do with Allure
    }

    @Override
    public void result(Result result) {
        if (match != null) {
            if (FAILED.equals(result.getStatus())) {

                this.excuteFailureCallback();

                ALLURE_LIFECYCLE.fire(new StepFailureEvent().withThrowable(result.getError()));
                ALLURE_LIFECYCLE.fire(new TestCaseFailureEvent().withThrowable(result.getError()));
                currentStatus = FAILED;
            } else if (SKIPPED.equals(result.getStatus())) {
                ALLURE_LIFECYCLE.fire(new StepCanceledEvent());
                if (PASSED.equals(currentStatus)) {
                    //not to change FAILED status to CANCELED in the report
                    ALLURE_LIFECYCLE.fire(new TestCasePendingEvent());
                    currentStatus = SKIPPED;
                }
            }
            ALLURE_LIFECYCLE.fire(new StepFinishedEvent());
            match = null;
        }
    }

    @Override
    public void after(Match match, Result result) {
        //Nothing to do with Allure
    }

    @Override
    public void match(Match match) {

        if (match instanceof StepDefinitionMatch) {
            this.match = (StepDefinitionMatch) match;

            Step step = extractStep(this.match);
            synchronized (gherkinSteps) {
                while (gherkinSteps.peek() != null && !isEqualSteps(step, gherkinSteps.peek())) {
                    fireCanceledStep(gherkinSteps.remove());
                }

                if (isEqualSteps(step, gherkinSteps.peek())) {
                    gherkinSteps.remove();
                }
            }
            String name = this.getStepName(step);
            ALLURE_LIFECYCLE.fire(new StepStartedEvent(name).withTitle(name));
            createDataTableAttachment(step.getRows());
        }
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        ALLURE_LIFECYCLE.fire(new MakeAttachmentEvent(data, "attachment" + counter++, mimeType));
    }

    @Override
    public void write(String text) {
        ALLURE_LIFECYCLE.fire(new MakeAttachmentEvent(text.getBytes(), "message" + counter++, "text/plain"));
    }

    private void createDataTableAttachment(final List<DataTableRow> dataTableRows) {
        if (dataTableRows != null && !dataTableRows.isEmpty()) {
            final StringBuilder dataTableCsv = new StringBuilder();
            for (DataTableRow row : dataTableRows) {
                dataTableCsv.append(StringUtils.join(row.getCells().toArray(), "\t"));
                dataTableCsv.append('\n');
            }
            ALLURE_LIFECYCLE.fire(new MakeAttachmentEvent(dataTableCsv.toString().getBytes(),
                    "Data table", "text/tab-separated-values"));
        }
    }

    private Step extractStep(StepDefinitionMatch match) {
        try {
            Field step = match.getClass().getDeclaredField("step");
            step.setAccessible(true);
            return (Step) step.get(match);
        } catch (ReflectiveOperationException e) {
            //shouldn't ever happen
            LOG.error(e.getMessage(), e);
            throw new CucumberException(e);
        }
    }

    private boolean isEqualSteps(Step step, Step gherkinStep) {
        return Objects.equals(step.getLine(), gherkinStep.getLine());
    }

    private SeverityLevel getSeverityLevel(Scenario scenario) {
        SeverityLevel level = null;
        List<SeverityLevel> severityLevels = Arrays.asList(
                SeverityLevel.BLOCKER,
                SeverityLevel.CRITICAL,
                SeverityLevel.NORMAL,
                SeverityLevel.MINOR,
                SeverityLevel.TRIVIAL);
        for (Tag tag : scenario.getTags()) {
            Matcher matcher = SEVERITY_PATTERN.matcher(tag.getName());
            if (matcher.matches()) {
                SeverityLevel levelTmp;
                String levelString = matcher.group(1);
                try {
                    levelTmp = SeverityLevel.fromValue(levelString.toLowerCase());
                } catch (IllegalArgumentException e) {
                    LOG.warn(String.format("Unexpected Severity level [%s]. SeverityLevel.NORMAL will be used instead", levelString), e);
                    levelTmp = SeverityLevel.NORMAL;
                }

                if (level == null || severityLevels.indexOf(levelTmp) < severityLevels.indexOf(level)) {
                    level = levelTmp;
                }
            }
        }
        return level;
    }

    private void fireCanceledStep(Step unimplementedStep) {
        String name = getStepName(unimplementedStep);
        ALLURE_LIFECYCLE.fire(new StepStartedEvent(name).withTitle(name));
        ALLURE_LIFECYCLE.fire(new StepCanceledEvent());
        ALLURE_LIFECYCLE.fire(new StepFinishedEvent());
        //not to change FAILED status to CANCELED in the report
        ALLURE_LIFECYCLE.fire(new TestCasePendingEvent() {
            @Override
            protected String getMessage() {
                return "Unimplemented steps were found";
            }
        });
        currentStatus = SKIPPED;
    }

    public String getStepName(Step step) {
        return step.getKeyword() + step.getName();

    }

    private Description getDescriptionAnnotation(final String description) {
        return new Description() {
            @Override
            public String value() {
                return description;
            }

            @Override
            public DescriptionType type() {
                return DescriptionType.TEXT;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Description.class;
            }
        };
    }

    private Features getFeaturesAnnotation(final String value) {
        return new Features() {

            @Override
            public String[] value() {
                return new String[]{value};
            }

            @Override
            public Class<Features> annotationType() {
                return Features.class;
            }
        };
    }

    private Stories getStoriesAnnotation(final String value) {
        return new Stories() {

            @Override
            public String[] value() {
                return new String[]{value};
            }

            @Override
            public Class<Stories> annotationType() {
                return Stories.class;
            }
        };
    }

    private Severity getSeverityAnnotation(final SeverityLevel value) {
        return new Severity() {

            @Override
            public SeverityLevel value() {
                return value;
            }

            @Override
            public Class<Severity> annotationType() {
                return Severity.class;
            }
        };
    }

    private Issues getIssuesAnnotation(Scenario scenario) {
        List<String> issues = new ArrayList<>();
        for (Tag tag : scenario.getTags()) {
            Matcher matcher = ISSUE_PATTERN.matcher(tag.getName());
            if (matcher.matches()) {
                issues.add(matcher.group(1));
            }
        }
        return !issues.isEmpty() ? getIssuesAnnotation(issues) : null;
    }

    private Issues getIssuesAnnotation(List<String> issues) {
        final Issue[] values = createIssuesArray(issues);
        return new Issues() {
            @Override
            public Issue[] value() {
                return values;
            }

            @Override
            public Class<Issues> annotationType() {
                return Issues.class;
            }
        };
    }

    private Issue[] createIssuesArray(List<String> issues) {
        ArrayList<Issue> values = new ArrayList<>();
        for (final String issue : issues) {
            values.add(new Issue() {
                @Override
                public Class<Issue> annotationType() {
                    return Issue.class;
                }

                @Override
                public String value() {
                    return issue;
                }
            });
        }

        return values.toArray(new Issue[values.size()]);
    }

    private TestCaseId getTestCaseIdAnnotation(Scenario scenario) {
        for (Tag tag : scenario.getTags()) {
            Matcher matcher = TEST_CASE_ID_PATTERN.matcher(tag.getName());
            if (matcher.matches()) {
                final String testCaseId = matcher.group(1);
                return new TestCaseId() {
                    @Override
                    public String value() {
                        return testCaseId;
                    }

                    @Override
                    public Class<TestCaseId> annotationType() {
                        return TestCaseId.class;
                    }
                };
            }
        }

        return null;
    }

    /**
     * Apply callback which will called on test failure It should be a class
     * which implements {@link OnFailureCallback}. the call() method can return
     * result which will be available via
     * AllureReporter.getFailureCallbackResult() method.
     *
     * @param callbackClass
     */
    public static void applyFailureCallback(Class<? extends OnFailureCallback> callbackClass) {
        callback = callbackClass;
    }

    /**
     * Returns failure callback result or null
     *
     * @param <T> Any type to return
     * @return Returns failure callback result or null
     */
    public static <T> T getFailureCallbackResult() {
        return (T) callbackResult;
    }

    private void excuteFailureCallback() {
        if (callback != null) {
            try {
                callbackResult = callback.newInstance().call();
            } catch (InstantiationException | IllegalAccessException ex) {
                throw new AllureException("Could not initialize callback", ex);
            }
        }
    }
}
