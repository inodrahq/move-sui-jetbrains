address 0x42 {
module legacy {
    friend 0x42::other;
    use std::vector;

    struct S has copy, drop { x: u64 }

    public(friend) fun f(): u64 { 1 }
    public(script) fun g() {}

    spec module {
        pragma verify = true;
    }

    spec f {
        ensures result == 1;
    }
}
}

script {
    use 0x42::legacy;
    fun main() { let _ = legacy::f(); }
}
