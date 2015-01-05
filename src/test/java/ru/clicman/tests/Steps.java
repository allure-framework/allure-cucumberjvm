/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.clicman.tests;

import cucumber.api.java.ru.*;
import java.util.Arrays;
import org.junit.Assert;
import ru.yandex.qatools.allure.annotations.Attachment;

/**
 *
 * @author sidochenko
 */
public class Steps {

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
            this.createAttachment(Arrays.toString(e.getStackTrace()));
            throw e;
        }
    }

    @Когда("^отображается отчет$")
    public void отображается_отчет() throws Throwable {

    }

    @То("^видно исключение$")
    public void видно_исключение() throws Throwable {

    }

    @Attachment()
    private byte[] createAttachment(String att) {
        String content = att;
        return content.getBytes();
    }

}
