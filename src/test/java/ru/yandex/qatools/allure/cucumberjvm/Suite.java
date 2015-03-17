/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.yandex.qatools.allure.cucumberjvm;

import org.junit.runner.RunWith;

/**
 *
 * @author sidochenko
 */
@RunWith(org.junit.runners.Suite.class)
@org.junit.runners.Suite.SuiteClasses({FirstFeatureTest.class, SecondFeatureTest.class})
public class Suite {

}
