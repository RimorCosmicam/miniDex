#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uhid.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const uint8_t mouse_descriptor[] = {
    0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01, 0xA1, 0x00,
    0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
    0x95, 0x03, 0x75, 0x01, 0x81, 0x02, 0x95, 0x01, 0x75, 0x05,
    0x81, 0x01, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
    0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
    0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, 0x81, 0x25, 0x7F,
    0x75, 0x08, 0x95, 0x01, 0x81, 0x06, 0xC0, 0xC0
};

static int write_event(int fd, const struct uhid_event *event) {
    ssize_t written = write(fd, event, sizeof(*event));
    return written == (ssize_t)sizeof(*event) ? 0 : -1;
}

static int create_mouse(int fd) {
    struct uhid_event event;
    memset(&event, 0, sizeof(event));
    event.type = UHID_CREATE2;
    snprintf((char *)event.u.create2.name, sizeof(event.u.create2.name),
             "MiniDex Native Mouse");
    memcpy(event.u.create2.rd_data, mouse_descriptor, sizeof(mouse_descriptor));
    event.u.create2.rd_size = sizeof(mouse_descriptor);
    event.u.create2.bus = BUS_USB;
    event.u.create2.vendor = 0x18D1;
    event.u.create2.product = 0x4E22;
    event.u.create2.version = 1;
    event.u.create2.country = 0;
    return write_event(fd, &event);
}

static int send_report(int fd, int buttons, int dx, int dy, int wheel, int horizontal) {
    struct uhid_event event;
    memset(&event, 0, sizeof(event));
    event.type = UHID_INPUT2;
    event.u.input2.size = 5;
    event.u.input2.data[0] = (uint8_t)buttons;
    event.u.input2.data[1] = (uint8_t)dx;
    event.u.input2.data[2] = (uint8_t)dy;
    event.u.input2.data[3] = (uint8_t)wheel;
    event.u.input2.data[4] = (uint8_t)horizontal;
    return write_event(fd, &event);
}

int main(void) {
    int fd = open("/dev/uhid", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "OPEN_FAILED %d\n", errno);
        return 2;
    }
    if (create_mouse(fd) != 0) {
        fprintf(stderr, "CREATE_FAILED %d\n", errno);
        close(fd);
        return 3;
    }

    // Give InputReader time to classify the new device before the first report.
    usleep(100000);
    puts("READY");
    fflush(stdout);

    char line[128];
    while (fgets(line, sizeof(line), stdin) != NULL) {
        int buttons = 0, dx = 0, dy = 0, wheel = 0, horizontal = 0;
        if (sscanf(line, "R %d %d %d %d %d", &buttons, &dx, &dy, &wheel, &horizontal) == 5) {
            if (send_report(fd, buttons, dx, dy, wheel, horizontal) != 0) break;
        } else {
            int delay_ms = 0;
            if (sscanf(line, "D %d", &delay_ms) == 1 && delay_ms > 0) {
                usleep((useconds_t)delay_ms * 1000);
            }
        }
    }

    struct uhid_event destroy;
    memset(&destroy, 0, sizeof(destroy));
    destroy.type = UHID_DESTROY;
    write_event(fd, &destroy);
    close(fd);
    return 0;
}
