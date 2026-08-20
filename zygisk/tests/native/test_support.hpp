#pragma once

#include <cstdio>
#include <cstring>

namespace test_support {

inline int failures = 0;

inline void fail(const char *file, int line, const char *expression) {
    std::fprintf(stderr, "%s:%d: expectation failed: %s\n", file, line, expression);
    ++failures;
}

inline void expect_strings(const char *actual, const char *expected,
                           const char *expression, const char *file, int line) {
    if (actual == nullptr || expected == nullptr) {
        if (actual != expected) fail(file, line, expression);
        return;
    }
    if (std::strcmp(actual, expected) != 0) fail(file, line, expression);
}

inline int finish(const char *suite) {
    if (failures != 0) {
        std::fprintf(stderr, "%s: FAIL (%d expectations)\n", suite, failures);
        return 1;
    }
    std::printf("%s: PASS\n", suite);
    return 0;
}

}  // namespace test_support

#define EXPECT_TRUE(value) \
    do { if (!(value)) test_support::fail(__FILE__, __LINE__, #value); } while (false)
#define EXPECT_FALSE(value) EXPECT_TRUE(!(value))
#define EXPECT_NULL(value) EXPECT_TRUE((value) == nullptr)
#define EXPECT_EQ(actual, expected) \
    do { \
        const auto test_actual = (actual); \
        const auto test_expected = (expected); \
        if (test_actual != test_expected) { \
            test_support::fail(__FILE__, __LINE__, #actual " == " #expected); \
        } \
    } while (false)
#define EXPECT_STREQ(actual, expected) \
    test_support::expect_strings((actual), (expected), #actual " == " #expected, \
                                 __FILE__, __LINE__)
