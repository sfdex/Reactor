#include "fake_jni.hpp"
#include "s26spoof/build_fields.hpp"
#include "s26spoof/profile.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::BuildWriteResult;
using s26spoof::DeviceProfile;
using s26spoof::write_build_fields;

void writer_updates_all_five_fields() {
    FakeJni fake;
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    const BuildWriteResult result = write_build_fields(fake.environment(), profile);

    EXPECT_EQ(result.attempted, static_cast<unsigned char>(5));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(5));
    EXPECT_FALSE(result.exception_cleared);
    EXPECT_STREQ(fake.value_for("MANUFACTURER"), "samsung");
    EXPECT_STREQ(fake.value_for("BRAND"), "samsung");
    EXPECT_STREQ(fake.value_for("MODEL"), "SM-S9480");
    EXPECT_STREQ(fake.value_for("DEVICE"), "s26ultra");
    EXPECT_STREQ(fake.value_for("PRODUCT"), "s26ultrachn");
}

void writer_supports_custom_profile() {
    FakeJni fake;
    const DeviceProfile profile = {
        "Google",
        "google",
        "Pixel 9 Pro",
        "komodo",
        "komodo_beta",
    };
    const BuildWriteResult result = write_build_fields(fake.environment(), profile);

    EXPECT_EQ(result.attempted, static_cast<unsigned char>(5));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(5));
    EXPECT_STREQ(fake.value_for("MANUFACTURER"), "Google");
    EXPECT_STREQ(fake.value_for("BRAND"), "google");
    EXPECT_STREQ(fake.value_for("MODEL"), "Pixel 9 Pro");
    EXPECT_STREQ(fake.value_for("DEVICE"), "komodo");
    EXPECT_STREQ(fake.value_for("PRODUCT"), "komodo_beta");
}

void writer_skips_empty_fields_in_profile() {
    FakeJni fake;
    DeviceProfile sparse{};
    std::strncpy(sparse.model, "SM-S9480", sizeof(sparse.model) - 1);
    const BuildWriteResult result = write_build_fields(fake.environment(), sparse);

    EXPECT_EQ(result.attempted, static_cast<unsigned char>(1));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(1));
    EXPECT_FALSE(result.exception_cleared);
    EXPECT_STREQ(fake.value_for("MODEL"), "SM-S9480");
    EXPECT_NULL(fake.value_for("MANUFACTURER"));
    EXPECT_NULL(fake.value_for("BRAND"));
    EXPECT_NULL(fake.value_for("DEVICE"));
    EXPECT_NULL(fake.value_for("PRODUCT"));
}

void writer_clears_one_field_failure_and_continues() {
    FakeJni fake;
    fake.fail_field("DEVICE");
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    const BuildWriteResult result = write_build_fields(fake.environment(), profile);

    EXPECT_EQ(result.attempted, static_cast<unsigned char>(5));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(4));
    EXPECT_TRUE(result.exception_cleared);
    EXPECT_TRUE(fake.cleared_exception_count() > 0);
    EXPECT_FALSE(fake.has_exception());
    EXPECT_NULL(fake.value_for("DEVICE"));
    EXPECT_STREQ(fake.value_for("PRODUCT"), "s26ultrachn");
}

void writer_handles_null_environment() {
    const DeviceProfile profile = {"samsung", "samsung", "SM-S9480", "s26ultra", "s26ultrachn"};
    const BuildWriteResult result = write_build_fields(nullptr, profile);
    EXPECT_EQ(result.attempted, static_cast<unsigned char>(0));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(0));
    EXPECT_FALSE(result.exception_cleared);
}

void writer_preserves_preexisting_exception() {
    FakeJni fake;
    fake.seed_exception();
    const DeviceProfile profile = {"samsung", "samsung", "SM-S9480", "s26ultra", "s26ultrachn"};
    const BuildWriteResult result = write_build_fields(fake.environment(), profile);

    EXPECT_EQ(result.attempted, static_cast<unsigned char>(0));
    EXPECT_EQ(result.succeeded, static_cast<unsigned char>(0));
    EXPECT_FALSE(result.exception_cleared);
    EXPECT_TRUE(fake.has_exception());
    EXPECT_EQ(fake.cleared_exception_count(), 0);
    EXPECT_NULL(fake.value_for("MODEL"));
}

}  // namespace

int main() {
    writer_updates_all_five_fields();
    writer_supports_custom_profile();
    writer_skips_empty_fields_in_profile();
    writer_clears_one_field_failure_and_continues();
    writer_handles_null_environment();
    writer_preserves_preexisting_exception();
    return test_support::finish("build_fields_test");
}
