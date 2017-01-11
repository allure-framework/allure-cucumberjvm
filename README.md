[![release](http://github-release-version.herokuapp.com/github/allure-framework/allure-cucumber-jvm-adaptor/release.svg?style=flat)](https://github.com/allure-framework/allure-cucumber-jvm-adaptor/releases/latest) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/ru.yandex.qatools.allure/allure-cucumber-jvm-adaptor/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/ru.yandex.qatools.allure/allure-cucumber-jvm-adaptor) [![build](https://img.shields.io/jenkins/s/http/ci.qatools.ru/allure-cucumber-jvm-adaptor_master-deploy.svg?style=flat)](http://ci.qatools.ru/job/allure-cucumber-jvm-adaptor_master-deploy/lastBuild/)


# Allure Cucumber-JVM Adaptor
This adaptor allows to generate allure xml reports after cucumber-jvm tests execution.

## Example project
Example projects is located at: https://github.com/allure-examples/allure-cucumber-jvm-example

## Usage
Simply add **allure-allure-cucumber-jvm-adaptor** as dependency to your project and add **build** section with adaptor plugin: 
```xml
<project>
...
    <dependencies>
        <dependency>
            <groupId>ru.yandex.qatools.allure</groupId>
            <artifactId>allure-cucumber-jvm-adaptor</artifactId>
            <version>1.6.0</version>
        </dependency>
    </dependencies>
        <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.19.1</version>
                <configuration>
                    <testFailureIgnore>false</testFailureIgnore>
                   <argLine>
                        -javaagent:${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar
                        -Dcucumber.options="--plugin ru.yandex.qatools.allure.cucumberjvm.AllureReporter"
                    </argLine>                   
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.aspectj</groupId>
                        <artifactId>aspectjweaver</artifactId>
                        <version>1.7.4</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

Then execute **mvn clean test** goal.
After tests executed allure xml files will be placed in **target/allure-results/** directory
