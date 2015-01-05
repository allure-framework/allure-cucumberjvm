/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.clicman.tests;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

/**
 *
 * @author sidochenko
 */
@RunWith(Cucumber.class)
@CucumberOptions(features = {"src/test/resources/"}, tags = "~@wip")
public class CucumberJvmTest {
    
}

