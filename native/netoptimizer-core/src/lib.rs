//! Pure calculations for measured TCP and DNS samples. No networking or Android APIs.

const MAX_SAMPLES: usize = 4096;

/// Negative samples are failed TCP connections, not packet-loss measurements.
fn tcp_summary(samples: &[i32]) -> [i32; 5] {
    let mut count: i64 = 0;
    let mut sum: i64 = 0;
    let mut differences: i64 = 0;
    let mut previous = None;
    for &sample in samples {
        if sample < 0 {
            continue;
        }
        count += 1;
        sum += i64::from(sample);
        if let Some(last) = previous {
            differences += (i64::from(sample) - i64::from(last)).abs();
        }
        previous = Some(sample);
    }
    let average = if count == 0 { -1 } else { ((sum + count / 2) / count) as i32 };
    let jitter = if count < 2 { -1 } else {
        let intervals = count - 1;
        ((differences + intervals / 2) / intervals) as i32
    };
    let attempts = samples.len() as i64;
    let failures = if attempts == 0 { -1 } else { ((attempts - count) * 100 / attempts) as i32 };
    [attempts as i32, count as i32, average, jitter, failures]
}

/// The median is defined only when the specified number of nonnegative samples exists.
fn median(samples: &[i32], min_success: usize) -> Option<i32> {
    let mut values: Vec<i32> = samples.iter().copied().filter(|v| *v >= 0).collect();
    if values.len() < min_success || values.is_empty() {
        return None;
    }
    values.sort_unstable();
    let middle = values.len() / 2;
    Some(if values.len() % 2 == 1 {
        values[middle]
    } else {
        ((i64::from(values[middle - 1]) + i64::from(values[middle])) / 2) as i32
    })
}

/// The JNI shim supplies a valid array and five-element output for the duration of this call.
/// Neither pointer is retained. Values outside the bounded input contract are rejected.
///
/// # Safety
/// `input` must point to `len` readable integers unless `len` is zero; `output`
/// must point to five writable integers and must not overlap `input`.
#[no_mangle]
pub unsafe extern "C" fn netopt_tcp_summary(input: *const i32, len: u32, output: *mut i32) -> i32 {
    let len = len as usize;
    if output.is_null() || len > MAX_SAMPLES || (len != 0 && input.is_null()) {
        return -1;
    }
    let samples = if len == 0 { &[] } else { std::slice::from_raw_parts(input, len) };
    let result = tcp_summary(samples);
    std::ptr::copy_nonoverlapping(result.as_ptr(), output, result.len());
    0
}

/// Returns -1 when the minimum is not met, or -2 for an invalid FFI contract.
///
/// # Safety
/// `input` must point to `len` readable integers unless `len` is zero.
#[no_mangle]
pub unsafe extern "C" fn netopt_median(input: *const i32, len: u32, minimum: u32) -> i32 {
    let len = len as usize;
    if len > MAX_SAMPLES || minimum == 0 || (len != 0 && input.is_null()) {
        return -2;
    }
    let samples = if len == 0 { &[] } else { std::slice::from_raw_parts(input, len) };
    median(samples, minimum as usize).unwrap_or(-1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn summary_handles_failures_and_rounding() {
        assert_eq!(tcp_summary(&[]), [0, 0, -1, -1, -1]);
        assert_eq!(tcp_summary(&[-1, -2]), [2, 0, -1, -1, 100]);
        assert_eq!(tcp_summary(&[20, -1, -1]), [3, 1, 20, -1, 66]);
        assert_eq!(tcp_summary(&[10, -1, 20, 25]), [4, 3, 18, 8, 25]);
        assert_eq!(tcp_summary(&[0, i32::MAX]), [2, 2, 1_073_741_824, i32::MAX, 0]);
    }

    #[test]
    fn median_filters_failures_and_avoids_overflow() {
        assert_eq!(median(&[], 3), None);
        assert_eq!(median(&[10, -1, 30, 20], 3), Some(20));
        assert_eq!(median(&[1, 2, 3, 4], 3), Some(2));
        assert_eq!(median(&[i32::MAX, i32::MAX - 1], 2), Some(i32::MAX - 1));
    }

    #[test]
    fn ffi_rejects_invalid_pointers_and_limits() {
        let mut result = [0; 5];
        unsafe {
            assert_eq!(netopt_tcp_summary(std::ptr::null(), 0, result.as_mut_ptr()), 0);
            assert_eq!(result, [0, 0, -1, -1, -1]);
            assert_eq!(netopt_tcp_summary(std::ptr::null(), 1, result.as_mut_ptr()), -1);
            assert_eq!(netopt_tcp_summary(std::ptr::null(), 0, std::ptr::null_mut()), -1);
            assert_eq!(netopt_median(std::ptr::null(), (MAX_SAMPLES + 1) as u32, 3), -2);
            assert_eq!(netopt_median(std::ptr::null(), 0, 0), -2);
            assert_eq!(netopt_median(std::ptr::null(), 0, 3), -1);
        }
    }
}
