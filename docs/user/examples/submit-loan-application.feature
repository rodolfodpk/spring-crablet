Feature: Submit Loan Application

  @loan @vertical-slice
  Scenario: Submit a new loan application
    Given a customer submits a loan application
    When the request is accepted
    Then the system records LoanApplicationSubmitted
    And pending applications includes the new application
