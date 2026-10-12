module m::macros;

public macro fun insertion_sort_by<$T>($v: &mut vector<$T>, $le: |&$T, &$T| -> bool) {
    let v = $v;
    let n = v.length();
    if (n < 2) return;
    let mut i = 1;
    while (i < n) {
        let mut j = i;
        while (j > 0 && !$le(&v[j - 1], &v[j])) {
            v.swap(j, j - 1);
            j = j - 1;
        };
        i = i + 1;
    }
}
