Feature: ecctool run-repair

  Scenario: Run local repair for keyspace test2 and table table2
    Given we have access to ecctool
    When we run local repair for keyspace test2 and table table2
    Then the repair output should contain a valid header
    And the repair output should contain a valid repair row for test2.table2
    And the repair output should not contain more rows
    And the output should contain summary

  Scenario: Run cluster-wide repair for keyspace test2 and table table2
    Given we have access to ecctool
    When we run repair for keyspace test2 and table table2
    Then the repair output should contain a valid header
    And the repair output should contain a valid repair row for test2.table2
    And the repair output should contain a valid repair row for test2.table2
    And the repair output should not contain more rows
    And the output should contain summary