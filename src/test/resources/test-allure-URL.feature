#language: en
@third
Feature: Text allure 3

  Scenario: Scenario
    Given Anything in given with dots. This is an example
    When whe run the scenario
    Then scenario name shuld be complete

  Scenario: Data Table
    Given data table:
      | id | user    | password |
      | 1  | clicman | 12345    |
      | 2  | goofy   | 12345    |
      | 3  | dolly   | 12345    |

  Scenario: Data Table2
    Given data table:
      | id | user  | password |
      | 4  | Henry | 12345    |
      | 5  | Bobby | 12345    |
      | 6  | Linda | 12345    |

  Scenario Outline: Scenario Structure
    Given An URL <URL_First>
    And another URL <URL_Second>
    When whe concatenate it
    Then Result should be <URL_Result>

    Examples:
      | URL_First              | URL_Second             | URL_Result                                 |
      | http://www.google.es   | http://www.leda-mc.com | http://www.google.eshttp://www.leda-mc.com |
      | http://www.leda-mc.com | http://www.google.es   | http://www.leda-mc.comhttp://www.google.es |
