package ru.yandex.qatools.allure.cucumberjvm;

import org.junit.Assert;

import ru.yandex.qatools.allure.annotations.Attachment;
import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java.ru.Дано;
import cucumber.api.java.ru.Когда;
import cucumber.api.java.ru.То;
import cucumber.api.java.ru.Тогда;

/**
 * @author Viktor Sidochenko viktor.sidochenko@gmail.com
 */
public class Steps {

	String URL_First;
	String URL_Second;
	String URL_concat;
	
    int a;
    int b;
    int c;
    int sum;

    @Дано("^первое число (\\d+)$")
    public void первое_число(int digit) throws Throwable {
        a = digit;
    }

    @Дано("^второе число (\\d+)$")
    public void второе_число(int digit) throws Throwable {
        b = digit;
    }

    @Дано("^третье число (\\d+)$")
    public void третье_число(int digit) throws Throwable {
        c = digit;
    }

    @Когда("^я их складываю$")
    public void я_их_складываю() throws Throwable {
        sum = a + b + c;
    }

    @Тогда("^сумма равна (\\d+)$")
    public void сумма_равна(int result) throws Throwable {
        Assert.assertEquals(result, sum);
    }

    @Дано("^сломанный сценарий$")
    public void сломанный_сценарий() throws Throwable {
        try {
            Object o = 1;
            String fail = (String) o;
        } catch (Exception e) {
            makeAttach(e.getMessage());
            throw e;
        }
    }

    @Когда("^отображается отчет$")
    public void отображается_отчет() throws Throwable {

    }

    @То("^видно исключение$")
    public void видно_исключение() throws Throwable {

    }
    
    @Given("^Anything in given with (.+)$")
    public void anything_in_given_with_dots_This_is_an_example(String text) throws Throwable {
    	
    }

    @When("^whe run the scenario$")
    public void whe_run_the_scenario() throws Throwable {
        
    }

    @Then("^scenario name shuld be complete$")
    public void scenario_name_shuld_be_complete() throws Throwable {
        
    }

    @Given("^An URL (.+)$")
    public void an_URL(String URL) throws Throwable {
    	this.URL_First = URL;
    }

    @Given("^another URL (.+)$")
    public void another_URL(String URL) throws Throwable {
    	this.URL_Second = URL;
    }

    @When("^whe concatenate it$")
    public void whe_concatenate_it() throws Throwable {
    	this.URL_concat = this.URL_First + this.URL_Second;
    }

    @Then("^Result should be (.+)$")
    public void result_should_be(String expected) throws Throwable {
        Assert.assertEquals(expected, URL_concat);
    }
    

   @Attachment
    public String makeAttach(String text) {
        return text;
    }

}
