#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/input.h>
#include <linux/uhid.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

static int uhid_fd = -1;

/* The exact mouse descriptor used by the supplied, working DeX Touchpad APK. */
static const uint8_t mouse_report_desc[] = {
    0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01,
    0xA1, 0x00, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
    0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01,
    0x81, 0x02, 0x95, 0x01, 0x75, 0x05, 0x81, 0x03,
    0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
    0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03,
    0x81, 0x06, 0xC0, 0xC0
};

static int write_uhid_event(const struct uhid_event *event) {
    if (uhid_fd < 0) return 0;
    return write(uhid_fd, event, sizeof(*event)) >= 0;
}

JNIEXPORT jboolean JNICALL
Java_com_minidex_app_input_adb_PrivilegedMouseService_nativeCreateMouse(
        JNIEnv *env, jobject instance) {
    (void)env;
    (void)instance;
    if (uhid_fd >= 0) return JNI_TRUE;
    uhid_fd = open("/dev/uhid", O_RDWR | O_CLOEXEC);
    if (uhid_fd < 0) return JNI_FALSE;

    /* Use the legacy UHID_CREATE event just like the proven APK. */
    struct uhid_event event;
    memset(&event, 0, sizeof(event));
    event.type = UHID_CREATE;
    strncpy((char *)event.u.create.name, "MiniDex DeX Mouse", sizeof(event.u.create.name) - 1);
    event.u.create.rd_data = (__u8 *)mouse_report_desc;
    event.u.create.rd_size = sizeof(mouse_report_desc);
    event.u.create.bus = BUS_USB;
    event.u.create.vendor = 0x1234;
    event.u.create.product = 0x5678;
    if (!write_uhid_event(&event)) {
        close(uhid_fd);
        uhid_fd = -1;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_minidex_app_input_adb_PrivilegedMouseService_nativeSendReport(
        JNIEnv *env, jobject instance, jint buttons, jint dx, jint dy, jint wheel) {
    (void)env;
    (void)instance;
    struct uhid_event event;
    uint8_t report[4] = {
        (uint8_t)buttons,
        (uint8_t)dx,
        (uint8_t)dy,
        (uint8_t)wheel
    };
    memset(&event, 0, sizeof(event));
    event.type = UHID_INPUT;
    event.u.input.size = sizeof(report);
    memcpy(event.u.input.data, report, sizeof(report));
    return write_uhid_event(&event) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_minidex_app_input_adb_PrivilegedMouseService_nativeDestroyMouse(
        JNIEnv *env, jobject instance) {
    (void)env;
    (void)instance;
    if (uhid_fd < 0) return;
    struct uhid_event event;
    memset(&event, 0, sizeof(event));
    event.type = UHID_DESTROY;
    write_uhid_event(&event);
    close(uhid_fd);
    uhid_fd = -1;
}
