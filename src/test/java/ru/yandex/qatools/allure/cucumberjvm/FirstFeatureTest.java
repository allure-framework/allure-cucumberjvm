package ru.yandex.qatools.allure.cucumberjvm;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(features = {"src/test/resources/"}, tags = "@first")
public class FirstFeatureTest {
    
}

