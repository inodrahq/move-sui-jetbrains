/// Doc comment on module.
#[allow(unused_variable, lint(self_transfer))]
module kitchen::sink;

use std::string::{Self, String};
use sui::{coin::{Self, Coin}, balance::Balance, object::{Self as obj, UID}};
use fun sui::coin::value as Coin.val;
public use fun helper as Thing.helper;
use 0x2::transfer;

const EInvalid: u64 = 1;
#[error]
const ENamed: vector<u8> = b"named error";

public struct Thing has key, store {
    id: UID,
    value: u64,
    name: String,
}

public struct Wrapper<phantom T, U: copy + drop>(u64, vector<U>) has copy, drop;
public struct Empty {}
public struct Unit();
public struct Postfix { x: u8 } has drop;

public enum Shape has copy, drop {
    Circle(u64),
    Rect { w: u64, h: u64 },
    Point,
}

fun init(otw: SINK, ctx: &mut TxContext) {
    let _ = otw;
}

entry fun create(ctx: &mut TxContext) {
    let thing = Thing { id: obj::new(ctx), value: 0, name: string::utf8(b"x") };
    transfer::share_object(thing);
}

public(package) fun area(s: &Shape): u64 {
    match (s) {
        Shape::Circle(r) => *r * *r * 3,
        Shape::Rect { w, h } if (*w > 0) => *w * *h,
        Shape::Point | Shape::Circle(0) => 0,
        _ => abort EInvalid,
    }
}

public fun helper(t: &mut Thing, mut by: u64): u64 {
    assert!(by > 0, EInvalid);
    by = by + 1;
    t.value = t.value + by;
    let v = vector[1u8, 2, 3];
    let w = vector<u64>[];
    let total = v.fold!(0u64, |acc, x| acc + (x as u64));
    v.do_ref!(|e| { let _ = *e; });
    let (a, b) = (1, 2);
    let Thing { id: _, value, name: _ } = *t;
    let Wrapper(x, ..) = Wrapper<u8, u8>(1, vector[]);
    let r = &mut t.value;
    *r = *r << 2 >> 1;
    let ge = a >= b && a <= b || a != b;
    let nested: vector<vector<u8>> = vector[];
    let opt: Option<vector<vector<u8>>> = option::none();
    let idx = v[0];
    let f = t.value.max(3);
    'outer: loop {
        let mut i = 0;
        while (i < 10) {
            i = i + 1;
            if (i == 5) continue;
            if (i == 7) break 'outer
        };
        break
    };
    let block_value = 'a: { if (total > 0) return 'a 1; 2 };
    if (ge) { return total } else { abort };
    copy total + move block_value
}

public macro fun twice<$T>($f: |$T| -> $T, $x: $T): $T {
    let x = $x;
    $f($f(x))
}

native fun native_fn<T: drop>(x: T);

#[test]
#[expected_failure(abort_code = kitchen::sink::EInvalid)]
fun test_fail() {
    let s = @0x1;
    let n = @kitchen;
    let h = x"deadbeef";
    let big = 0xFF_FFu256;
    let t = (1u8, 2u16, 3u32, 4u64, 5u128, 6u256);
    let (a, (b, c)) = (1, (2, 3));
    let lam = |mut y: u64| -> u64 { y = y + 1; y };
    abort 1
}

#[test_only]
public struct SINK has drop {}
