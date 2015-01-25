# Allure Cucumber-JVM Adaptor
This adaptor allows to generate allure xml reports after cucumber-jvm Junit test execution.

## Example project
Example project is located at: https://github.com/allure-examples/allure-cucumber-jvm-example

## Usage
Simply add **allure-allure-cucumber-jvm-adaptor** as dependency to your project and add **build** section with adaptor listener: 
```xml
<project>
...
    <dependencies>
        <dependency>
            <groupId>ru.yandex.qatools.allure</groupId>
            <artifactId>allure-cucumber-jvm-adaptor</artifactId>
            <version>1.1</version>
        </dependency>
    </dependencies>
        <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.18</version>
                <configuration>
                    <testFailureIgnore>false</testFailureIgnore>
                    <argLine>
                        -javaagent:${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar
                    </argLine>
                    <properties>
                        <property>
                            <name>listener</name>
                            <value>ru.yandex.qatools.allure.cucumberjvm.AllureRunListener</value>
                        </property>
                    </properties>
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
