#include <cstddef>
#include <sys/types.h>

#include "s26spoof/maps.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::MapIdentity;
using s26spoof::parse_map_identity;
using s26spoof::remember_unique_map;

void parser_extracts_executable_file_identity() {
    MapIdentity parsed{};
    EXPECT_TRUE(parse_map_identity(
        "7a1000-7b2000 r-xp 00000000 fd:01 1234 /system/lib64/libc.so",
        &parsed));
    EXPECT_TRUE(parsed.executable);
    EXPECT_EQ(parsed.inode, static_cast<ino_t>(1234));

    MapIdentity read_only{};
    EXPECT_TRUE(parse_map_identity(
        "7b2000-7b3000 r--p 00001000 fd:01 1234 /system/lib64/libc.so",
        &read_only));
    EXPECT_FALSE(read_only.executable);
    EXPECT_EQ(read_only.device, parsed.device);
}

void parser_rejects_malformed_or_anonymous_entries() {
    MapIdentity parsed{};
    EXPECT_FALSE(parse_map_identity(nullptr, &parsed));
    EXPECT_FALSE(parse_map_identity("not a maps line", &parsed));
    EXPECT_FALSE(parse_map_identity(
        "7a1000-7b2000 r-xp 00000000 00:00 0 [anon:dalvik]", &parsed));
    EXPECT_FALSE(parse_map_identity(
        "7a1000-7b2000 r-xp 00000000 fd:01 1234 /system/lib64/libc.so",
        nullptr));
}

void unique_tracker_catches_duplicate_and_capacity_bugs() {
    MapIdentity values[2] = {};
    std::size_t count = 0;
    const MapIdentity first{static_cast<dev_t>(10), static_cast<ino_t>(20), true};
    const MapIdentity duplicate{static_cast<dev_t>(10), static_cast<ino_t>(20), false};
    const MapIdentity second{static_cast<dev_t>(10), static_cast<ino_t>(21), true};
    const MapIdentity third{static_cast<dev_t>(11), static_cast<ino_t>(22), true};

    EXPECT_TRUE(remember_unique_map(first, values, &count, 2));
    EXPECT_EQ(count, static_cast<std::size_t>(1));
    EXPECT_FALSE(remember_unique_map(duplicate, values, &count, 2));
    EXPECT_EQ(count, static_cast<std::size_t>(1));
    EXPECT_TRUE(remember_unique_map(second, values, &count, 2));
    EXPECT_EQ(count, static_cast<std::size_t>(2));
    EXPECT_FALSE(remember_unique_map(third, values, &count, 2));
    EXPECT_EQ(count, static_cast<std::size_t>(2));

    EXPECT_FALSE(remember_unique_map(first, nullptr, &count, 2));
    EXPECT_FALSE(remember_unique_map(first, values, nullptr, 2));
}

}  // namespace

int main() {
    parser_extracts_executable_file_identity();
    parser_rejects_malformed_or_anonymous_entries();
    unique_tracker_catches_duplicate_and_capacity_bugs();
    return test_support::finish("maps_test");
}
