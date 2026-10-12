#[test_only]
module counter::counter_tests;

use counter::counter::{Self, Counter};
use sui::test_scenario;

#[test]
fun test_increment() {
    let owner = @0xA;
    let mut scenario = test_scenario::begin(owner);
    counter::create(scenario.ctx());
    scenario.next_tx(owner);
    let mut c = scenario.take_shared<Counter>();
    c.increment(5);
    assert!(c.value() == 5);
    test_scenario::return_shared(c);
    scenario.end();
}

#[test, expected_failure(abort_code = counter::EZeroIncrement)]
fun test_zero_increment_fails() {
    let owner = @0xA;
    let mut scenario = test_scenario::begin(owner);
    counter::create(scenario.ctx());
    scenario.next_tx(owner);
    let mut c = scenario.take_shared<Counter>();
    c.increment(0);
    test_scenario::return_shared(c);
    scenario.end();
}
