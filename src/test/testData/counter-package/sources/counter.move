/// A shared counter anyone can increment.
module counter::counter;

use sui::event;

const EZeroIncrement: u64 = 0;

public struct Counter has key {
    id: UID,
    value: u64,
    owner: address,
}

public struct Incremented has copy, drop {
    counter: ID,
    value: u64,
}

public fun create(ctx: &mut TxContext) {
    let counter = Counter { id: object::new(ctx), value: 0, owner: ctx.sender() };
    transfer::share_object(counter);
}

public fun increment(counter: &mut Counter, by: u64) {
    assert!(by > 0, EZeroIncrement);
    counter.value = counter.value + by;
    event::emit(Incremented { counter: counter.id.to_inner(), value: counter.value });
}

public fun value(counter: &Counter): u64 { counter.value }

public fun reset(counter: &mut Counter, ctx: &TxContext) {
    assert!(counter.owner == ctx.sender());
    counter.value = 0;
}
