package com.example

import spock.lang.Specification

class CalculatorSpec extends Specification {
    def "adding two numbers works"() {
        given:
        def calculator = new Calculator()

        when:
        def result = calculator.add(2, 3)

        then:
        result == 5
    }
}
